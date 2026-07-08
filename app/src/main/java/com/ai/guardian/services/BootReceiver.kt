package com.ai.guardian.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * BootReceiver — restarts GuardianForegroundService after device reboot.
 *
 * This is the primary recovery mechanism after:
 *   - Device power off / reboot
 *   - System update restart
 *
 * The Accessibility Service typically restarts itself independently via the
 * system's accessibility framework. The Foreground Service is restarted here.
 *
 * Limitation (documented): If the user has Force Stopped Guardian before
 * rebooting, Android will NOT deliver this broadcast to the app until the user
 * manually launches it again. This is an Android platform constraint.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON") {
            return
        }

        Log.d(TAG, "Boot completed. Restarting GuardianForegroundService.")

        try {
            val serviceIntent = Intent(context, GuardianForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart GuardianForegroundService on boot", e)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
