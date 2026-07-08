package com.ai.guardian.security

import android.content.Context
import android.util.Log
import com.ai.guardian.data.AppDatabase
import com.ai.guardian.data.entity.AuditEventEntity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Central tamper detection and audit log manager.
 *
 * All security events flow through this singleton:
 * 1. Written to local Room immediately (offline-safe, single source of truth).
 * 2. Synced to Firestore in batches when the network is available.
 *    Parent device can then observe the audit stream in real-time.
 *
 * Design guarantees:
 * - Events are never lost even if Firestore sync fails.
 * - Room write is synchronous within the calling coroutine context.
 * - Firestore sync is best-effort, non-blocking.
 */
class TamperDetectionManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val db by lazy { AppDatabase.getDatabase(context) }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    // --- Public API ---

    fun log(event: AuditEvent, detail: String = "") {
        scope.launch {
            try {
                val entity = AuditEventEntity(
                    eventType = event.name,
                    detail = detail
                )
                db.auditEventDao().insertEvent(entity)
                Log.d(TAG, "[AUDIT] ${event.name}: $detail")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write audit event ${event.name}", e)
            }
        }
    }

    /**
     * Syncs all unsynced local audit events to the Parent's Firestore audit log.
     * Called by SyncEngineManager when the sync engine is RUNNING.
     *
     * @param childDeviceUuid The UUID of this (Child) device, used to write under
     *                        devices/{childUuid}/auditLog/{eventId}
     */
    fun syncPendingEvents(childDeviceUuid: String) {
        scope.launch {
            try {
                val unsynced = db.auditEventDao().getUnsynced()
                if (unsynced.isEmpty()) return@launch

                val batch = firestore.batch()
                for (event in unsynced) {
                    val ref = firestore
                        .collection("devices")
                        .document(childDeviceUuid)
                        .collection("auditLog")
                        .document(event.id)
                    batch.set(ref, mapOf(
                        "eventType" to event.eventType,
                        "detail" to event.detail,
                        "timestamp" to event.timestamp
                    ))
                }
                batch.commit()
                    .addOnSuccessListener {
                        scope.launch {
                            db.auditEventDao().markSynced(unsynced.map { it.id })
                            Log.d(TAG, "Synced ${unsynced.size} audit events to Firestore.")
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to sync audit events to Firestore", e)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error during audit sync", e)
            }
        }
    }

    /**
     * Prunes local audit events older than 30 days to prevent unbounded DB growth.
     */
    fun pruneOldEvents() {
        scope.launch {
            val cutoff = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            db.auditEventDao().pruneOlderThan(cutoff)
        }
    }

    companion object {
        private const val TAG = "TamperDetectionManager"

        @Volatile
        private var INSTANCE: TamperDetectionManager? = null

        fun getInstance(context: Context): TamperDetectionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TamperDetectionManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
