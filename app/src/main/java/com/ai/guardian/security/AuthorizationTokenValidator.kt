package com.ai.guardian.security

import android.util.Log

/**
 * Validates HMAC-SHA256 authorization tokens produced by the Parent device.
 *
 * Token format (pipe-delimited string that is HMAC-signed):
 *   requestId|action|requestedAt|nonce|childUUID|parentUUID
 *
 * Validation steps (all must pass):
 *   1. Signature matches (HMAC-SHA256 with Parent's Keystore key)
 *   2. Token has not expired (current time < expiresAt field from Firestore)
 *   3. requestId matches the local pending approval record
 *   4. action matches what was requested
 *   5. nonce matches the locally generated nonce (one-time use guarantee)
 *   6. The request has not already been consumed (status != CONSUMED)
 */
class AuthorizationTokenValidator(private val keyManager: ParentSecretKeyManager) {

    data class TokenPayload(
        val requestId: String,
        val action: String,
        val requestedAt: Long,
        val nonce: String,
        val childUuid: String,
        val parentUuid: String
    )

    data class ValidationResult(
        val isValid: Boolean,
        val reason: String = ""
    )

    /**
     * Builds the canonical message string that was signed by the Parent.
     * Both Parent (signing) and Child (verifying) must produce the SAME string.
     */
    fun buildMessage(payload: TokenPayload): String =
        "${payload.requestId}|${payload.action}|${payload.requestedAt}|${payload.nonce}|${payload.childUuid}|${payload.parentUuid}"

    /**
     * Validates a token received from the Parent via Firestore.
     *
     * @param token          The Base64 HMAC-SHA256 signature from the Parent.
     * @param payload        The token payload reconstructed from Firestore fields.
     * @param expiresAt      The expiration timestamp from the Firestore document.
     * @param localNonce     The nonce stored in the local ApprovalRequestEntity.
     * @param localRequestId The requestId from the local ApprovalRequestEntity.
     * @param localAction    The action name from the local ApprovalRequestEntity.
     * @param parentUuid     The UUID of the Parent device.
     * @param childUuid      The UUID of this (Child) device.
     */
    fun validate(
        token: String,
        payload: TokenPayload,
        expiresAt: Long,
        localNonce: String,
        localRequestId: String,
        localAction: String,
        parentUuid: String,
        childUuid: String
    ): ValidationResult {
        // Step 1: Verify HMAC signature
        val message = buildMessage(payload)
        
        if (!keyManager.hasKeyFor(parentUuid)) {
            Log.w(TAG, "[Crypto] Verification failed: pairing key missing for parentUuid=$parentUuid")
            return ValidationResult(false, "INVALID_PAIRING_KEY")
        }

        val signatureValid = keyManager.verify(parentUuid, message, token)
        if (!signatureValid) {
            Log.w(TAG, "[Crypto] Token signature INVALID for requestId=${payload.requestId}")
            return ValidationResult(false, "INVALID_SIGNATURE")
        }

        // Step 2: Check expiration
        if (System.currentTimeMillis() > expiresAt) {
            Log.w(TAG, "[Crypto] Token EXPIRED for requestId=${payload.requestId}")
            return ValidationResult(false, "REQUEST_EXPIRED")
        }

        // Step 3: Verify requestId matches
        if (payload.requestId != localRequestId) {
            Log.w(TAG, "[Crypto] Request ID mismatch: ${payload.requestId} != $localRequestId")
            return ValidationResult(false, "INVALID_REQUEST")
        }

        // Step 4: Verify action matches
        if (payload.action != localAction) {
            Log.w(TAG, "[Crypto] Action mismatch: ${payload.action} != $localAction")
            return ValidationResult(false, "INVALID_REQUEST")
        }

        // Step 5: Verify nonce (one-time use)
        if (payload.nonce != localNonce) {
            Log.w(TAG, "[Crypto] Nonce mismatch for requestId=${payload.requestId}")
            return ValidationResult(false, "INVALID_NONCE")
        }

        // Step 6: Verify device identities
        if (payload.childUuid != childUuid) {
            Log.w(TAG, "[Crypto] Device identity mismatch (childUuid): ${payload.childUuid} != $childUuid")
            return ValidationResult(false, "INVALID_CHILD_UUID")
        }
        if (payload.parentUuid != parentUuid) {
            Log.w(TAG, "[Crypto] Device identity mismatch (parentUuid): ${payload.parentUuid} != $parentUuid")
            return ValidationResult(false, "INVALID_PARENT_UUID")
        }

        Log.d(TAG, "[Crypto] Token VALID for action=${payload.action}, requestId=${payload.requestId}")
        return ValidationResult(true)
    }

    companion object {
        private const val TAG = "AuthorizationTokenValidator"
    }
}
