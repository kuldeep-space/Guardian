package com.ai.guardian.security

import android.os.Handler
import android.os.Looper

object MaintenanceModeManager {
    private var maintenanceExpiryTimestamp = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val expiryRunnable = Runnable {
        android.util.Log.d("MaintenanceMode", "Maintenance mode expired automatically. Re-enabling all protections.")
        maintenanceExpiryTimestamp = 0L
        // Trigger a session invalidation so any currently open protected app
        // gets locked again once the 60-second maintenance window closes.
        android.util.Log.d("MaintenanceMode", "Forcing session invalidation after maintenance mode expiry.")
        com.ai.guardian.services.GuardianAccessibilityService.invalidateTrustedSession()
    }

    fun startMaintenanceMode() {
        handler.removeCallbacks(expiryRunnable)
        maintenanceExpiryTimestamp = android.os.SystemClock.elapsedRealtime() + 60_000L
        handler.postDelayed(expiryRunnable, 60_000L)
        android.util.Log.d("MaintenanceMode", "Maintenance mode activated for 60 seconds.")
    }

    fun isMaintenanceModeActive(): Boolean {
        return android.os.SystemClock.elapsedRealtime() < maintenanceExpiryTimestamp
    }

    fun clearMaintenanceMode() {
        handler.removeCallbacks(expiryRunnable)
        maintenanceExpiryTimestamp = 0L
        android.util.Log.d("MaintenanceMode", "Maintenance mode cleared manually.")
    }
}
