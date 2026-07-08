package com.ai.guardian.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ai.guardian.data.remote.DeviceSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PackageChangeReceiver : BroadcastReceiver() {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val data = intent.data
        val packageName = data?.schemeSpecificPart

        val isOwnPackage = packageName == context.packageName
        val isRemoved = action == Intent.ACTION_PACKAGE_REMOVED && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        val isAddedOrReplaced = action == Intent.ACTION_PACKAGE_ADDED || action == Intent.ACTION_PACKAGE_REPLACED

        // Guardian's own removal — log tamper event and notify Parent
        if (isOwnPackage && isRemoved) {
            Log.e("PackageChangeReceiver", "Guardian itself is being UNINSTALLED!")
            com.ai.guardian.security.TamperDetectionManager.getInstance(context)
                .log(com.ai.guardian.security.AuditEvent.GUARDIAN_UNINSTALL_DETECTED,
                    "Guardian uninstall detected on device.")
            return
        }

        if (isOwnPackage) return

        if (packageName == null) return

        if (isRemoved || isAddedOrReplaced) {
            Log.d("PackageChangeReceiver", "Detected package change: $packageName (Removed: $isRemoved)")
            
            val pendingResult = goAsync()
            coroutineScope.launch {
                try {
                    val container = (context.applicationContext as com.ai.guardian.GuardianApplication).container
                    val syncEngineManager = container.syncEngineManager
                    val deviceSyncManager = container.deviceSyncManager
                    if (!deviceSyncManager.isChildDevice()) {
                        Log.d("PackageChangeReceiver", "Ignoring package change on PARENT device")
                        return@launch
                    }
                    if (isRemoved) {
                        syncEngineManager.requestPackageRemoved(packageName)
                    } else {
                        syncEngineManager.requestPackageAdded(packageName)
                    }
                } catch (e: Exception) {
                    Log.e("PackageChangeReceiver", "Failed to sync single app change", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
