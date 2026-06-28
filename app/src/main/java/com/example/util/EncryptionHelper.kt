package com.example.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object EncryptionHelper {
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "VVF_VAULT_KEY_ALIAS"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128

    init {
        initKeyStoreKey()
    }

    private fun initKeyStoreKey() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEY_STORE
                )
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            ?: throw IllegalStateException("Key not found in KeyStore")
        return entry.secretKey
    }

    /**
     * Encrypts a source file and writes the encrypted bytes to a destination file.
     * Prepends the 12-byte IV to the output file.
     */
    fun encryptFile(sourceFile: File, destFile: File) {
        val secretKey = getSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv

        FileInputStream(sourceFile).use { input ->
            FileOutputStream(destFile).use { output ->
                // Write the IV first
                output.write(iv)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                val cipherOutputStream = javax.crypto.CipherOutputStream(output, cipher)
                cipherOutputStream.use { cipherOut ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        cipherOut.write(buffer, 0, bytesRead)
                    }
                }
            }
        }
    }

    /**
     * Decrypts a source file (with prepended 12-byte IV) and writes the plaintext to a destination file.
     */
    fun decryptFile(sourceFile: File, destFile: File) {
        val secretKey = getSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)

        FileInputStream(sourceFile).use { input ->
            // Read the IV
            val iv = ByteArray(IV_LENGTH_BYTES)
            val ivRead = input.read(iv)
            if (ivRead != IV_LENGTH_BYTES) {
                throw IllegalStateException("Invalid encrypted file format: missing IV")
            }

            val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            FileOutputStream(destFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                val cipherInputStream = javax.crypto.CipherInputStream(input, cipher)
                cipherInputStream.use { cipherIn ->
                    while (cipherIn.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }
        }
    }
}
