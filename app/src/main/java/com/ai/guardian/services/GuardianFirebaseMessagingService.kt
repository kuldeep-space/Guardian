package com.ai.guardian.services

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * GuardianFirebaseMessagingService
 *
 * This class exists solely to satisfy the Firebase Messaging SDK component registration
 * and prevent ClassNotFoundException process crashes when background FCM control events occur.
 *
 * This class is intentionally kept as a minimal no-op implementation because Guardian uses
 * Firestore snapshot listeners for all active sync operations rather than FCM data payloads.
 */
class GuardianFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        // No-op: Firestore listeners handle all sync and commands.
        // We log message receipt in debug builds for telemetry purposes only.
        if (com.ai.guardian.BuildConfig.DEBUG) {
            Log.d(TAG, "[FCM] Background wake-up message received from: ${message.from}")
        }
    }

    override fun onNewToken(token: String) {
        if (com.ai.guardian.BuildConfig.DEBUG) {
            Log.d(TAG, "[FCM] New token generated (Guardian does not use tokens for command routing)")
        }
    }

    companion object {
        private const val TAG = "GuardianFCM"
    }
}
