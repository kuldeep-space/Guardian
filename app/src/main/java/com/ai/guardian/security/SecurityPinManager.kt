package com.ai.guardian.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecurityPinManager(private val context: Context) {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }

    init {
        generateKeyIfNeeded()
    }

    private fun generateKeyIfNeeded() {
        try {
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
                keyGen.init(spec)
                keyGen.generateKey()
            }
        } catch (e: Exception) {
            android.util.Log.e("SecurityPinManager", "Failed to generate security key alias", e)
        }
    }

    private fun getSecretKey(): SecretKey {
        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }

    fun savePin(pin: String): PinStorageModel {
        val salt = generateSalt()
        val hash = hashPin(pin, salt)
        val encryptedHash = encrypt(hash)
        return PinStorageModel(
            encryptedHash = encryptedHash.first,
            iv = encryptedHash.second,
            salt = Base64.encodeToString(salt, Base64.NO_WRAP)
        )
    }

    fun verifyPin(pin: String, storedHash: String, storedIv: String, storedSalt: String): Boolean {
        return try {
            val salt = Base64.decode(storedSalt, Base64.NO_WRAP)
            val hash = hashPin(pin, salt)
            val decryptedHash = decrypt(storedHash, storedIv) ?: return false
            
            // Constant-time comparison
            val a = hash.toByteArray(Charsets.UTF_8)
            val b = decryptedHash.toByteArray(Charsets.UTF_8)
            if (a.size != b.size) return false
            var diff = 0
            for (i in a.indices) {
                diff = diff or (a[i].toInt() xor b[i].toInt())
            }
            diff == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun hashPin(pin: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.reset()
        digest.update(salt)
        val hashedBytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hashedBytes, Base64.NO_WRAP)
    }

    private fun generateSalt(): ByteArray {
        val salt = ByteArray(16)
        java.security.SecureRandom().nextBytes(salt)
        return salt
    }

    private fun encrypt(data: String): Pair<String, String> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val encryptedBytes = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        return Pair(
            Base64.encodeToString(encryptedBytes, Base64.NO_WRAP),
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        )
    }

    private fun decrypt(encryptedData: String, iv: String): String? {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            val decryptedBytes = cipher.doFinal(Base64.decode(encryptedData, Base64.NO_WRAP))
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    data class PinStorageModel(
        val encryptedHash: String,
        val iv: String,
        val salt: String
    )

    companion object {
        private const val KEY_ALIAS = "guardian_security_pin_key"
    }
}
