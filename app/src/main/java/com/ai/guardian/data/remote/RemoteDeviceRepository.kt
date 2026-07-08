package com.ai.guardian.data.remote

import android.util.Log
import com.ai.guardian.data.remote.models.RemoteAppModel
import com.ai.guardian.data.dao.PairedDeviceDao
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class RemoteDeviceRepository(
    private val deviceSyncManager: DeviceSyncManager,
    private val pairedDeviceDao: PairedDeviceDao,
    private val keyManager: com.ai.guardian.security.ParentSecretKeyManager
) {
    private val firestore = FirebaseFirestore.getInstance()

    fun getRemoteApps(deviceUuid: String): Flow<List<RemoteAppModel>> = callbackFlow {
        val appsCollection = firestore.collection("devices").document(deviceUuid).collection("apps")
        
        val listener = appsCollection.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.e("RemoteDeviceRepo", "Listen failed.", e)
                close(e)
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val apps = snapshot.documents.mapNotNull { it.toObject(RemoteAppModel::class.java) }
                // Sort apps alphabetically by name
                trySend(apps.sortedBy { it.appName.lowercase() })
            }
        }

        awaitClose {
            listener.remove()
        }
    }

    suspend fun toggleRemoteAppLock(deviceUuid: String, packageName: String, isLocked: Boolean) {
        // Parent toggles child's app directly via DeviceSyncManager bypassing SyncEngineManager
        deviceSyncManager.syncRemoteToggle(deviceUuid, packageName, isLocked)
    }

    fun getDevicePresence(deviceUuid: String): Flow<Map<String, Any>?> = callbackFlow {
        val listener = firestore.collection("devices").document(deviceUuid).collection("presence").document("current")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }
                trySend(snapshot?.data)
            }
        awaitClose { listener.remove() }
    }

    fun getDeviceState(deviceUuid: String): Flow<Map<String, Any>?> = callbackFlow {
        val listener = firestore.collection("devices").document(deviceUuid).collection("state").document("current")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }
                trySend(snapshot?.data)
            }
        awaitClose { listener.remove() }
    }

    suspend fun sendRemoteCommand(childUuid: String, commandType: com.ai.guardian.data.remote.models.CommandType, payload: String = "") {
        val parentUuid = deviceSyncManager.deviceUuid
        
        // Fetch pairing key dynamically via Room (no runBlocking used since it runs within coroutine scope)
        val pairedParent = pairedDeviceDao.getDeviceByUuid(childUuid)
        val token = pairedParent?.pairingKey ?: ""
        
        val docRef = firestore.collection("devices").document(childUuid).collection("commands").document()
        val command = com.ai.guardian.data.remote.models.RemoteCommand(
            commandId = docRef.id,
            commandType = commandType,
            parentDeviceId = parentUuid,
            childDeviceId = childUuid,
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 600000, // 10 minutes expiry
            nonce = java.util.UUID.randomUUID().toString(),
            status = com.ai.guardian.data.remote.models.CommandStatus.PENDING,
            authorizationToken = token,
            commandSequence = System.currentTimeMillis(),
            commandPriority = if (commandType == com.ai.guardian.data.remote.models.CommandType.EMERGENCY_LOCK) 100 else 10,
            payload = payload
        )
        try {
            docRef.set(command).await()
            Log.d("RemoteDeviceRepo", "Successfully wrote command $commandType to Firestore.")
        } catch (e: Exception) {
            Log.e("RemoteDeviceRepo", "Failed to write command $commandType to Firestore.", e)
        }
    }

    fun getPendingApprovalRequests(childUuid: String): Flow<List<com.ai.guardian.data.remote.models.ApprovalRequest>> = callbackFlow {
        val listener = firestore.collection("devices")
            .document(childUuid)
            .collection("approvalRequests")
            .whereEqualTo("status", "PENDING")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val requests = snapshot.documents.mapNotNull { 
                        it.toObject(com.ai.guardian.data.remote.models.ApprovalRequest::class.java)
                    }
                    trySend(requests)
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun approveRequest(request: com.ai.guardian.data.remote.models.ApprovalRequest) {
        val parentUuid = deviceSyncManager.deviceUuid
        
        // [Crypto] Verify that the pairing key is indexed by childDeviceId before transaction
        val pairedDevice = pairedDeviceDao.getDeviceByUuid(request.childDeviceId)
        if (pairedDevice == null) {
            Log.e("RemoteDeviceRepo", "[Crypto] Failed to approve request: childDeviceId ${request.childDeviceId} is not a registered paired device.")
            throw Exception("INVALID_PAIRING_KEY")
        }
        if (pairedDevice.pairingKey.isNullOrEmpty()) {
            Log.e("RemoteDeviceRepo", "[Crypto] Failed to approve request: pairing key is null or empty for child ${request.childDeviceId}.")
            throw Exception("INVALID_PAIRING_KEY")
        }
        Log.d("RemoteDeviceRepo", "[Crypto] Pairing key found for child: ${request.childDeviceId}")

        val docRef = firestore.collection("devices")
            .document(request.childDeviceId)
            .collection("approvalRequests")
            .document(request.requestId)
            
        try {
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (!snapshot.exists()) {
                    throw Exception("Request does not exist")
                }
                
                val currentStatus = snapshot.getString("status")
                if (currentStatus != "PENDING") {
                    throw Exception("Request is no longer PENDING (status: $currentStatus)")
                }
                
                val expiresAt = snapshot.getLong("expiresAt") ?: 0L
                if (System.currentTimeMillis() > expiresAt) {
                    throw Exception("Request has already expired")
                }
                
                // Compute HMAC signature: requestId|requestType|createdAt|nonce|childDeviceId|parentDeviceId
                val message = "${request.requestId}|${request.requestType}|${request.createdAt}|${request.nonce}|${request.childDeviceId}|${request.parentDeviceId}"
                Log.d("RemoteDeviceRepo", "[Crypto] Signing message payload.")
                val signature = keyManager.sign(request.childDeviceId, message) ?: ""
                if (signature.isEmpty()) {
                    Log.e("RemoteDeviceRepo", "[Crypto] Signature generation failed (returned empty).")
                } else {
                    Log.d("RemoteDeviceRepo", "[Crypto] Signature generated successfully.")
                }
                
                transaction.update(docRef, mapOf(
                    "status" to "APPROVED",
                    "authorizationToken" to signature
                ))
            }.await()
        } catch (e: Exception) {
            Log.e("RemoteDeviceRepo", "[Crypto] Failed to approve request ${request.requestId}", e)
            throw e
        }
    }

    suspend fun rejectRequest(childUuid: String, requestId: String) {
        val docRef = firestore.collection("devices")
            .document(childUuid)
            .collection("approvalRequests")
            .document(requestId)
            
        try {
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (!snapshot.exists()) {
                    throw Exception("Request does not exist")
                }
                
                val currentStatus = snapshot.getString("status")
                if (currentStatus != "PENDING") {
                    throw Exception("Request is no longer PENDING (status: $currentStatus)")
                }
                
                val expiresAt = snapshot.getLong("expiresAt") ?: 0L
                if (System.currentTimeMillis() > expiresAt) {
                    throw Exception("Request has already expired")
                }
                
                transaction.update(docRef, "status", "REJECTED")
            }.await()
        } catch (e: Exception) {
            Log.e("RemoteDeviceRepo", "Failed to reject request $requestId", e)
            throw e
        }
    }

    fun getPendingLockCommands(deviceUuid: String): Flow<Boolean> = callbackFlow {
        val query = firestore.collection("devices")
            .document(deviceUuid)
            .collection("commands")
            .whereIn("status", listOf("PENDING", "PROCESSING"))

        val listener = query.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.e("RemoteDeviceRepo", "[Command] Pending command listener failed.", e)
                trySend(false)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val hasPending = snapshot.documents.any { doc ->
                    val commandTypeStr = doc.getString("commandType") ?: ""
                    commandTypeStr in listOf(
                        "LOCK_DEVICE", "UNLOCK_DEVICE",
                        "EMERGENCY_LOCK", "EMERGENCY_UNLOCK",
                        "ENABLE_PROTECTION", "DISABLE_PROTECTION",
                        "SET_PIN", "CHANGE_PIN", "RESET_PIN"
                    )
                }
                Log.d("RemoteDeviceRepo", "[Command] Pending lock commands status: $hasPending")
                trySend(hasPending)
            } else {
                trySend(false)
            }
        }
        awaitClose {
            Log.d("RemoteDeviceRepo", "[Command] Removing pending command listener.")
            listener.remove()
        }
    }
}
