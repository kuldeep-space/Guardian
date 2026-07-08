package com.ai.guardian.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

object BatteryOptimizationUtil {

    /**
     * Checks if the app is ignoring battery optimizations (standard Android Doze).
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    }

    /**
     * Returns an intent to the settings page to disable battery optimization for this app.
     */
    fun getBatteryOptimizationSettingsIntent(context: Context): Intent? {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        return if (intent.resolveActivity(context.packageManager) != null) intent else null
    }

    /**
     * Checks known OEM AutoStart/Battery optimization intents to see if the device
     * has aggressive background restrictions (like MIUI, ColorOS).
     * If an intent resolves, it returns it so the app can guide the user to whitelist it.
     */
    fun getOemAutoStartIntent(context: Context): Intent? {
        val intents = listOf(
            // Xiaomi / MIUI
            Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            // Oppo / ColorOS
            Intent().setClassName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            Intent().setClassName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            Intent().setClassName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            // Vivo
            Intent().setClassName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
            Intent().setClassName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            // Huawei / EMUI
            Intent().setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            Intent().setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
            // Samsung
            Intent().setClassName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
            // OnePlus
            Intent().setClassName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
        )

        for (intent in intents) {
            if (context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                Log.d("BatteryOptimization", "OEM AutoStart Intent resolved: ${intent.component?.className}")
                return intent
            }
        }
        return null
    }

    /**
     * Helper to log if OEM restrictions might be in place.
     */
    fun checkAndLogOptimizationStatus(context: Context) {
        if (!isIgnoringBatteryOptimizations(context)) {
            Log.w("BatteryOptimization", "Guardian is NOT ignoring Android Battery Optimizations. Background sync may be delayed.")
        }
        
        val oemIntent = getOemAutoStartIntent(context)
        if (oemIntent != null) {
            Log.w("BatteryOptimization", "Aggressive OEM battery management detected (${oemIntent.component?.packageName}). Guardian should instruct the user to allow AutoStart.")
        }
    }
}
