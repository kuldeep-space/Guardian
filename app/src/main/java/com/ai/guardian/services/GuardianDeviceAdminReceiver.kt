package com.ai.guardian.services

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ai.guardian.security.AuditEvent
import com.ai.guardian.security.TamperDetectionManager

/**
 * Device Administrator receiver for Guardian.
 *
 * Capabilities (normal Device Admin, NOT Device Owner):
 * - Creates friction before uninstall (user must remove admin first).
 * - Detects when admin was actually removed and notifies Parent via audit log.
 *
 * Limitation (documented): On Android 9+, Device Admin does NOT hard-block
 * uninstall. It requires the user to first visit Settings > Security > Device
 * Admin to remove Guardian's admin status, which adds one extra step and creates
 * an audit event that the Parent will receive.
 *
 * Device Owner mode (separate path via DevicePolicyController) provides the
 * hard block when Guardian is provisioned as Device Owner via ADB/NFC.
 */
class GuardianDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Log.d(TAG, "Device Admin enabled.")
        TamperDetectionManager.getInstance(context).log(
            AuditEvent.DEVICE_ADMIN_ENABLED,
            "Guardian Device Admin was activated."
        )
    }

    /**
     * This message is displayed to the user when they attempt to remove Guardian
     * as a Device Administrator. It is the ONLY official hook that creates friction.
     * The user can still proceed to disable it — this is an Android platform limitation.
     */
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        Log.w(TAG, "Device Admin disable requested by user.")
        TamperDetectionManager.getInstance(context).log(
            AuditEvent.DEVICE_ADMIN_DISABLE_REQUESTED,
            "Child attempted to remove Device Admin."
        )
        return "Guardian is protecting this device. Removing admin requires Parent authorization. " +
               "The Parent will be notified if you proceed."
    }

    /**
     * Device Admin was actually removed. Parent is notified via audit sync.
     * Guardian can no longer use admin-level APIs until re-activated.
     */
    override fun onDisabled(context: Context, intent: Intent) {
        Log.e(TAG, "Device Admin DISABLED by user.")
        TamperDetectionManager.getInstance(context).log(
            AuditEvent.DEVICE_ADMIN_DISABLED,
            "Guardian Device Admin was removed. Protection weakened."
        )
    }

    companion object {
        private const val TAG = "GuardianDeviceAdminReceiver"
    }
}
