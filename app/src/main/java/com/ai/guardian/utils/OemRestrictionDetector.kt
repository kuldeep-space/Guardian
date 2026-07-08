package com.ai.guardian.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object OemRestrictionDetector {
    private const val PREFS_NAME = "oem_detector_prefs"
    private const val KEY_PROMPTED = "has_prompted_oem_restrictions"

    fun getOemName(): String {
        return android.os.Build.MANUFACTURER.lowercase()
    }

    fun isOemRestricted(): Boolean {
        val oem = getOemName()
        return oem.contains("samsung") ||
                oem.contains("xiaomi") ||
                oem.contains("redmi") ||
                oem.contains("poco") ||
                oem.contains("oppo") ||
                oem.contains("vivo") ||
                oem.contains("oneplus") ||
                oem.contains("realme") ||
                oem.contains("huawei") ||
                oem.contains("honor") ||
                oem.contains("motorola") ||
                oem.contains("nothing")
    }

    fun hasPrompted(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PROMPTED, false)
    }

    fun markPrompted(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PROMPTED, true).apply()
    }

    fun getOemSettingsIntent(context: Context): Intent? {
        val oem = getOemName()
        val intent = Intent()
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        
        return when {
            oem.contains("xiaomi") || oem.contains("redmi") || oem.contains("poco") -> {
                intent.setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                intent
            }
            oem.contains("oppo") || oem.contains("realme") -> {
                intent.setClassName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")
                intent
            }
            oem.contains("vivo") -> {
                intent.setClassName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")
                intent
            }
            oem.contains("huawei") || oem.contains("honor") -> {
                intent.setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
                intent
            }
            else -> {
                // Fallback to standard App Details Settings
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        }
    }
}
