package com.ai.guardian.security

import android.content.Context
import android.util.Log
import com.ai.guardian.data.AppDatabase
import com.ai.guardian.data.entity.ApprovalRequestEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Central gate for all sensitive (Parent-authorized) operations.
 *
 * Usage from any screen/ViewModel:
 *
 *   protectedActionGate.request(
 *       action = ProtectedAction.DISABLE_GLOBAL_PROTECTION,
 *       onApproved = { /* execute the action */ },
 *       onDenied = { reason -> /* show error */ }
 *   )
 *
 * Flow:
 *   1. Creates ApprovalRequestEntity in Room with PENDING status.
 *   2. Writes approval request to Firestore approvalRequests collection.
 *   3. Attaches a ONE-TIME Firestore listener on the approval response document.
 *   4. On arrival: delegates full validation to AuthorizationTokenValidator.
 *   5. On success: updates Room to CONSUMED, logs APPROVED audit event.
 *   6. On failure: updates Room to REJECTED/EXPIRED, logs relevant event.
 *   7. Listener is detached immediately after first result (or on timeout).
 */
class ProtectedActionGate(
    private val context: Context,
    private val childDeviceUuid: String,
    private val keyManager: ParentSecretKeyManager,
    private val validator: AuthorizationTokenValidator
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val db by lazy { AppDatabase.getDatabase(context) }
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val tamper by lazy { TamperDetectionManager.getInstance(context) }

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    private val activeListeners = java.util.Collections.synchronizedMap(mutableMapOf<String, ListenerRegistration>())
    private val activeTimeoutJobs = java.util.Collections.synchronizedMap(mutableMapOf<String, kotlinx.coroutines.Job>())

    fun request(
        action: ProtectedAction,
        parentUuid: String,
        onRequestCreated: (String, Long) -> Unit,
        onApproved: () -> Unit,
        onDenied: (String) -> Unit
    ) {
        scope.launch {
            try {
                // 1. Check for existing PENDING request in Firestore (Duplicate Request Protection)
                val queryTask = firestore.collection("devices")
                    .document(childDeviceUuid)
                    .collection("approvalRequests")
                    .whereEqualTo("childDeviceId", childDeviceUuid)
                    .whereEqualTo("parentDeviceId", parentUuid)
                    .whereEqualTo("requestType", action.name)
                    .whereEqualTo("status", "PENDING")
                    .get()

                val snapshot = com.google.android.gms.tasks.Tasks.await(queryTask)
                val existingRequest = snapshot.documents.firstOrNull()?.let { doc ->
                    val req = doc.toObject(com.ai.guardian.data.remote.models.ApprovalRequest::class.java)
                    if (req != null && req.expiresAt > System.currentTimeMillis()) {
                        req
                    } else null
                }

                val request = if (existingRequest != null) {
                    Log.d(TAG, "Reusing existing pending approval request: ${existingRequest.requestId}")
                    existingRequest
                } else {
                    // Create new request
                    val newId = java.util.UUID.randomUUID().toString()
                    val req = com.ai.guardian.data.remote.models.ApprovalRequest(
                        requestId = newId,
                        childDeviceId = childDeviceUuid,
                        parentDeviceId = parentUuid,
                        requestType = action.name,
                        createdAt = System.currentTimeMillis(),
                        expiresAt = System.currentTimeMillis() + 60000L, // 60s default
                        nonce = java.util.UUID.randomUUID().toString(),
                        status = "PENDING"
                    )

                    firestore.collection("devices")
                        .document(childDeviceUuid)
                        .collection("approvalRequests")
                        .document(newId)
                        .set(req)
                        .await()

                    Log.d(TAG, "Created new approval request: ${req.requestId}")
                    req
                }

                // Write locally to Room
                val entity = com.ai.guardian.data.entity.ApprovalRequestEntity(
                    requestId = request.requestId,
                    action = request.requestType,
                    status = request.status,
                    requestedAt = request.createdAt,
                    expiresAt = request.expiresAt,
                    tokenNonce = request.nonce
                )
                db.approvalRequestDao().insert(entity)

                // Log audit event
                tamper.log(AuditEvent.PARENT_APPROVAL_REQUESTED, action.displayName)

                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onRequestCreated(request.requestId, request.expiresAt)
                }

                // Start observing the request
                listenToRequest(request, action, onApproved, onDenied)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to request approval", e)
                kotlinx.coroutines.withContext(Dispatchers.Main) { onDenied("Failed to send request: ${e.message}") }
            }
        }
    }

    fun cancelRequest(requestId: String) {
        scope.launch {
            try {
                firestore.collection("devices")
                    .document(childDeviceUuid)
                    .collection("approvalRequests")
                    .document(requestId)
                    .update("status", "CANCELLED")
                    .await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cancel request $requestId", e)
            }
        }
    }

    fun recoverPendingRequests(
        parentUuid: String,
        onRequestRecovered: (String, Long) -> Unit,
        onApproved: () -> Unit,
        onDenied: (String) -> Unit
    ) {
        scope.launch {
            try {
                val queryTask = firestore.collection("devices")
                    .document(childDeviceUuid)
                    .collection("approvalRequests")
                    .whereEqualTo("childDeviceId", childDeviceUuid)
                    .whereEqualTo("parentDeviceId", parentUuid)
                    .whereEqualTo("status", "PENDING")
                    .get()
                
                val snapshot = com.google.android.gms.tasks.Tasks.await(queryTask)
                for (doc in snapshot.documents) {
                    val request = doc.toObject(com.ai.guardian.data.remote.models.ApprovalRequest::class.java) ?: continue
                    val action = ProtectedAction.values().firstOrNull { it.name == request.requestType } ?: continue
                    
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        onRequestRecovered(request.requestId, request.expiresAt)
                    }
                    
                    listenToRequest(request, action, onApproved, onDenied)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to recover pending requests", e)
            }
        }
    }

    private fun listenToRequest(
        request: com.ai.guardian.data.remote.models.ApprovalRequest,
        action: ProtectedAction,
        onApproved: () -> Unit,
        onDenied: (String) -> Unit
    ) {
        val requestId = request.requestId
        val docRef = firestore.collection("devices")
            .document(childDeviceUuid)
            .collection("approvalRequests")
            .document(requestId)

        if (activeListeners.containsKey(requestId)) {
            return
        }

        _pendingCount.value = _pendingCount.value + 1

        // Timeout Job
        val now = System.currentTimeMillis()
        val remaining = (request.expiresAt - now).coerceAtLeast(0L)
        val timeoutJob = scope.launch {
            kotlinx.coroutines.delay(remaining)
            handleHandshakeFailure(requestId, action, "EXPIRED", "Request timed out", onDenied)
        }
        activeTimeoutJobs[requestId] = timeoutJob

        // Snapshot Listener
        val listener = docRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.e(TAG, "Snapshot error for request $requestId", e)
                return@addSnapshotListener
            }
            if (snapshot == null || !snapshot.exists()) {
                return@addSnapshotListener
            }

            val updated = snapshot.toObject(com.ai.guardian.data.remote.models.ApprovalRequest::class.java) ?: return@addSnapshotListener

            // Verify request ownership
            if (updated.childDeviceId != childDeviceUuid) {
                return@addSnapshotListener
            }

            scope.launch {
                when (updated.status) {
                    "APPROVED" -> {
                        val payload = AuthorizationTokenValidator.TokenPayload(
                            requestId = updated.requestId,
                            action = updated.requestType,
                            requestedAt = updated.createdAt,
                            nonce = updated.nonce,
                            childUuid = updated.childDeviceId,
                            parentUuid = updated.parentDeviceId
                        )

                        val validation = validator.validate(
                            token = updated.authorizationToken,
                            payload = payload,
                            expiresAt = updated.expiresAt,
                            localNonce = request.nonce,
                            localRequestId = request.requestId,
                            localAction = request.requestType,
                            parentUuid = request.parentDeviceId,
                            childUuid = request.childDeviceId
                        )

                        if (validation.isValid) {
                            cleanupRequest(requestId)
                            db.approvalRequestDao().updateStatus(requestId, "CONSUMED")
                            tamper.log(AuditEvent.PARENT_APPROVAL_GRANTED, action.displayName)
                            _pendingCount.value = (_pendingCount.value - 1).coerceAtLeast(0)
                            triggerHeartbeat()
                            kotlinx.coroutines.withContext(Dispatchers.Main) { onApproved() }
                        } else {
                            handleHandshakeFailure(requestId, action, "FAILED", "Invalid approval signature: ${validation.reason}", onDenied)
                        }
                    }
                    "REJECTED" -> {
                        handleHandshakeFailure(requestId, action, "REJECTED", "Rejected by Parent", onDenied)
                    }
                    "CANCELLED" -> {
                        handleHandshakeFailure(requestId, action, "CANCELLED", "Cancelled by Child", onDenied)
                    }
                    "EXPIRED" -> {
                        handleHandshakeFailure(requestId, action, "EXPIRED", "Request expired", onDenied)
                    }
                }
            }
        }
        activeListeners[requestId] = listener
    }

    private suspend fun handleHandshakeFailure(
        requestId: String,
        action: ProtectedAction,
        status: String,
        reason: String,
        onDenied: (String) -> Unit
    ) {
        cleanupRequest(requestId)

        db.approvalRequestDao().updateStatus(requestId, status)
        when (status) {
            "REJECTED" -> tamper.log(AuditEvent.PARENT_APPROVAL_REJECTED, "${action.displayName}: $reason")
            "EXPIRED" -> tamper.log(AuditEvent.PARENT_APPROVAL_EXPIRED, action.displayName)
            else -> tamper.log(AuditEvent.PARENT_APPROVAL_INVALID_TOKEN, "${action.displayName}: $reason")
        }

        _pendingCount.value = (_pendingCount.value - 1).coerceAtLeast(0)
        triggerHeartbeat()
        kotlinx.coroutines.withContext(Dispatchers.Main) { onDenied(reason) }
    }

    private suspend fun cleanupRequest(requestId: String) {
        activeTimeoutJobs.remove(requestId)?.cancel()
        activeListeners.remove(requestId)?.remove()

        try {
            firestore.collection("devices")
                .document(childDeviceUuid)
                .collection("approvalRequests")
                .document(requestId)
                .delete()
                .await()
            Log.d(TAG, "Cleaned up approval request document from Firestore: $requestId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete approval request document from Firestore", e)
        }
    }

    fun clearAllListeners() {
        synchronized(activeListeners) {
            for (listener in activeListeners.values) {
                listener.remove()
            }
            activeListeners.clear()
        }
        synchronized(activeTimeoutJobs) {
            for (job in activeTimeoutJobs.values) {
                job.cancel()
            }
            activeTimeoutJobs.clear()
        }
    }

    private fun triggerHeartbeat() {
        val app = context.applicationContext as? com.ai.guardian.GuardianApplication
        app?.container?.syncEngineManager?.triggerImmediateHeartbeat()
    }

    companion object {
        private const val TAG = "ProtectedActionGate"
    }
}
