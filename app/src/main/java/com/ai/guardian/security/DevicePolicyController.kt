package com.ai.guardian.security

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import android.util.Log
import com.ai.guardian.services.GuardianDeviceAdminReceiver

/**
 * Encapsulates all DevicePolicyManager operations.
 *
 * Automatically detects whether Guardian is running as:
 *   - Normal app (no DPM capabilities)
 *   - Device Administrator (limited friction capabilities)
 *   - Device Owner (full enterprise lockdown capabilities)
 *
 * This class is safe to call regardless of the current mode — all methods are
 * guarded with isDeviceAdmin / isDeviceOwner checks so no crashes occur on
 * non-managed devices.
 */
class DevicePolicyController(private val context: Context) {

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ComponentName(context, GuardianDeviceAdminReceiver::class.java)

    /** True if Guardian is currently an active Device Administrator. */
    val isDeviceAdmin: Boolean
        get() = dpm.isAdminActive(adminComponent)

    /**
     * True if Guardian is the Device Owner of this device.
     * Device Owner provides the strongest protection capabilities.
     * Provisioned via: adb shell dpm set-device-owner com.ai.guardian/.services.GuardianDeviceAdminReceiver
     */
    val isDeviceOwner: Boolean
        get() = dpm.isDeviceOwnerApp(context.packageName)

    // --- Device Owner capabilities ---

    /**
     * Blocks Guardian from being uninstalled via Settings > Apps.
     * Only available in Device Owner mode. No-op otherwise.
     */
    fun enforceUninstallBlock() {
        if (!isDeviceOwner) return
        dpm.setUninstallBlocked(adminComponent, context.packageName, true)
        Log.d(TAG, "Uninstall block enforced via Device Owner.")
        TamperDetectionManager.getInstance(context)
            .log(AuditEvent.DEVICE_OWNER_POLICY_APPLIED, "setUninstallBlocked=true")
    }

    /**
     * Prevents Factory Reset via Settings.
     * Only available in Device Owner mode. No-op otherwise.
     */
    fun blockFactoryReset() {
        if (!isDeviceOwner) return
        dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
        Log.d(TAG, "Factory reset blocked via Device Owner.")
        TamperDetectionManager.getInstance(context)
            .log(AuditEvent.DEVICE_OWNER_POLICY_APPLIED, "DISALLOW_FACTORY_RESET")
    }

    /**
     * Prevents users from being added or removed.
     * Only available in Device Owner mode. No-op otherwise.
     */
    fun blockUserChanges() {
        if (!isDeviceOwner) return
        dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_ADD_USER)
        dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_REMOVE_USER)
        Log.d(TAG, "User changes blocked via Device Owner.")
    }

    /**
     * Applies all Device Owner security policies at once.
     * Called from GuardianForegroundService.onCreate() on each startup.
     */
    fun applyAllPolicies() {
        if (!isDeviceOwner) {
            Log.d(TAG, "Not Device Owner — skipping policy enforcement.")
            return
        }
        enforceUninstallBlock()
        blockFactoryReset()
        blockUserChanges()
        Log.d(TAG, "All Device Owner policies applied.")
    }

    companion object {
        private const val TAG = "DevicePolicyController"
    }
}
