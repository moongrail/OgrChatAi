package com.ogrchatai.app.util

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object MessageCrypto {

    private const val KEY_ALIAS = "ogrchatai_message_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val AES_GCM_NOPADDING = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val NONCE_LENGTH = 12

    @Suppress("UNUSED_PARAMETER")
    fun encrypt(context: Context, plaintext: String): EncryptedResult {
        val key = getOrCreateKey(context)
        val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
        
        val nonce = generateNonce()
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, nonce)
        
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)
        
        val plaintextBytes = plaintext.toByteArray(StandardCharsets.UTF_8)
        val ciphertext = cipher.doFinal(plaintextBytes)
        
        return EncryptedResult(
            ciphertext = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            nonce = Base64.encodeToString(nonce, Base64.NO_WRAP)
        )
    }

    fun decrypt(context: Context, encryptedResult: EncryptedResult): String {
        val key = getOrCreateKey(context)
        val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
        
        val nonce = Base64.decode(encryptedResult.nonce, Base64.NO_WRAP)
        val ciphertext = Base64.decode(encryptedResult.ciphertext, Base64.NO_WRAP)
        
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, nonce)
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)
        
        val plaintextBytes = cipher.doFinal(ciphertext)
        return String(plaintextBytes, StandardCharsets.UTF_8)
    }

    private fun getOrCreateKey(context: Context): javax.crypto.SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = javax.crypto.KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE
            )
            val keySpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build()
            keyGenerator.init(keySpec)
            keyGenerator.generateKey()
        }
        
        return keyStore.getKey(KEY_ALIAS, null) as javax.crypto.SecretKey
    }

    private fun generateNonce(): ByteArray {
        val nonce = ByteArray(NONCE_LENGTH)
        java.security.SecureRandom().nextBytes(nonce)
        return nonce
    }

    data class EncryptedResult(
        val ciphertext: String,
        val nonce: String
    )
}
