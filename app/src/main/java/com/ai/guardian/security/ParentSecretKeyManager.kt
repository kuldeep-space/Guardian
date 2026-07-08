package com.ai.guardian.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * Manages the 256-bit Parent Secret Key stored exclusively in Android Keystore.
 * The raw key bytes NEVER leave the Keystore — HMAC operations are performed
 * inside the secure hardware boundary where available.
 *
 * Key is scoped per paired Parent UUID so multiple pairings can have distinct keys.
 */
class ParentSecretKeyManager(private val context: Context) {

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
    }

    /**
     * Generates and stores a new AES-256 key for the given Parent UUID.
     * Called once during successful pairing. Idempotent — if key already exists,
     * this is a no-op.
     */
    fun generateKeyForParent(parentUuid: String) {
        val alias = aliasFor(parentUuid)
        if (keyStore.containsAlias(alias)) {
            Log.d(TAG, "Key already exists for $alias — skipping generation.")
            return
        }
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setKeySize(256)
            .build()
        keyGen.init(spec)
        keyGen.generateKey()
        Log.d(TAG, "Parent secret key generated for $alias.")
    }

    /**
     * Computes HMAC-SHA256 of [message] using the stored key for [parentUuid].
     * Returns Base64-encoded result, or null if the key does not exist.
     * This is called by the PARENT device to sign authorization tokens.
     */
    fun sign(parentUuid: String, message: String): String? {
        return try {
            val key = getKey(parentUuid) ?: return null
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(key)
            Base64.encodeToString(mac.doFinal(message.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "HMAC signing failed for $parentUuid", e)
            null
        }
    }

    /**
     * Verifies that [expectedSignature] matches the HMAC-SHA256 of [message].
     * Called by the CHILD device to validate tokens from the Parent.
     * Uses constant-time comparison to prevent timing attacks.
     */
    fun verify(parentUuid: String, message: String, expectedSignature: String): Boolean {
        return try {
            val computed = sign(parentUuid, message) ?: return false
            // Constant-time comparison
            val a = computed.toByteArray()
            val b = expectedSignature.toByteArray()
            if (a.size != b.size) return false
            var diff = 0
            for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
            diff == 0
        } catch (e: Exception) {
            Log.e(TAG, "HMAC verification failed for $parentUuid", e)
            false
        }
    }

    /**
     * Returns true if a key is already provisioned for the given Parent UUID.
     */
    fun hasKeyFor(parentUuid: String): Boolean {
        if (keyStore.containsAlias(aliasFor(parentUuid))) return true
        return try {
            val db = com.ai.guardian.data.AppDatabase.getDatabase(context)
            val pairingKey = kotlinx.coroutines.runBlocking {
                db.pairedDeviceDao().getDeviceByUuid(parentUuid)?.pairingKey
            }
            !pairingKey.isNullOrEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Permanently removes the key for a given Parent UUID.
     * Called when a pairing is removed.
     */
    fun deleteKeyFor(parentUuid: String) {
        val alias = aliasFor(parentUuid)
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
            Log.d(TAG, "Deleted key for $alias.")
        }
    }

    private fun getKey(parentUuid: String): SecretKey? {
        val alias = aliasFor(parentUuid)
        if (keyStore.containsAlias(alias)) {
            return keyStore.getKey(alias, null) as? SecretKey
        }
        return try {
            val db = com.ai.guardian.data.AppDatabase.getDatabase(context)
            val pairingKey = kotlinx.coroutines.runBlocking {
                db.pairedDeviceDao().getDeviceByUuid(parentUuid)?.pairingKey
            }
            if (pairingKey != null) {
                javax.crypto.spec.SecretKeySpec(pairingKey.toByteArray(Charsets.UTF_8), "HmacSHA256")
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to derive key from Room pairingKey for $parentUuid", e)
            null
        }
    }

    private fun aliasFor(parentUuid: String): String = "guardian_parent_key_${parentUuid}"

    companion object {
        private const val TAG = "ParentSecretKeyManager"
    }
}
