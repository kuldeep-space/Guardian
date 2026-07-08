package com.ai.guardian.telemetry

import android.util.Log

object TelemetryManager {
    private const val TAG = "TelemetryManager"

    // Sensitive keys/patterns to scrub from logs
    private val SECRET_PATTERNS = listOf(
        Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"), // UUIDs
        Regex("pairingKey", RegexOption.IGNORE_CASE),
        Regex("authorizationToken", RegexOption.IGNORE_CASE),
        Regex("face_?embedding", RegexOption.IGNORE_CASE),
        Regex("biometric", RegexOption.IGNORE_CASE),
        Regex("nonce", RegexOption.IGNORE_CASE)
    )

    fun logEvent(eventName: String, params: Map<String, Any> = emptyMap()) {
        // Enforce: Track anonymous events only. No PII or package names.
        val sanitizedParams = params.filterKeys { key ->
            val isSafe = key != "packageName" && key != "email" && key != "name" && key != "phone"
            isSafe
        }.mapValues { (_, value) ->
            scrubSecret(value.toString())
        }

        Log.d(TAG, "Event: $eventName, Params: $sanitizedParams")
    }

    fun logError(throwable: Throwable, message: String) {
        val cleanMsg = scrubSecret(message)
        val cleanThrowable = sanitizeThrowable(throwable)
        Log.e(TAG, "Error: $cleanMsg", cleanThrowable)
    }

    fun logInfo(message: String) {
        val cleanMsg = scrubSecret(message)
        Log.i(TAG, cleanMsg)
    }

    private fun scrubSecret(input: String): String {
        var scrubbed = input
        for (pattern in SECRET_PATTERNS) {
            scrubbed = scrubbed.replace(pattern, "[REDACTED]")
        }
        return scrubbed
    }

    private fun sanitizeThrowable(throwable: Throwable): Throwable {
        val cleanMessage = scrubSecret(throwable.message ?: "")
        val newThrowable = Exception(cleanMessage)
        newThrowable.stackTrace = throwable.stackTrace
        return newThrowable
    }
}
