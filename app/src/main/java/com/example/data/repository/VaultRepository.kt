package com.example.data.repository

import android.content.Context
import com.example.data.model.FileItem
import com.example.util.EncryptionHelper
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VaultRepository(
    private val context: Context,
    private val fileRepository: FileRepository
) {
    private val sharedPrefs = context.getSharedPreferences("vvf_vault_prefs", Context.MODE_PRIVATE)

    private fun generateSalt(): String {
        return try {
            val random = java.security.SecureRandom()
            val saltBytes = ByteArray(16)
            random.nextBytes(saltBytes)
            android.util.Base64.encodeToString(saltBytes, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            "default_static_salt_fallback"
        }
    }

    private fun hashPin(pin: String, salt: String): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val saltedInput = pin + salt
            val hashBytes = digest.digest(saltedInput.toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            pin
        }
    }

    fun isPinSet(): Boolean {
        return sharedPrefs.contains("vault_pin")
    }

    fun setPin(pin: String): Boolean {
        if (pin.length < 4) return false
        val salt = generateSalt()
        val hashed = hashPin(pin, salt)
        return sharedPrefs.edit()
            .putString("vault_salt", salt)
            .putString("vault_pin", hashed)
            .commit()
    }

    fun verifyPin(pin: String): Boolean {
        val saved = sharedPrefs.getString("vault_pin", "") ?: ""
        if (saved.isEmpty()) return false
        
        val salt = sharedPrefs.getString("vault_salt", "") ?: ""
        if (salt.isNotEmpty()) {
            val hashedInput = hashPin(pin, salt)
            return saved == hashedInput
        } else {
            // Migrate old unsalted pin format
            val legacyUnsaltedHash = try {
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                val hashBytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
                hashBytes.joinToString("") { "%02x".format(it) }
            } catch (e: Exception) {
                pin
            }
            if (saved == legacyUnsaltedHash || saved == pin) {
                // Securely migrate to salted hash
                setPin(pin)
                return true
            }
        }
        return false
    }

    suspend fun getVaultFiles(): List<FileItem> = withContext(Dispatchers.IO) {
        val vaultDir = fileRepository.vaultRoot
        val files = vaultDir.listFiles() ?: return@withContext emptyList()
        return@withContext files.map { file ->
            val originalName = file.name.removeSuffix(".enc")
            FileItem(
                id = file.absolutePath,
                name = originalName,
                path = file.absolutePath,
                size = file.length(),
                isDirectory = false,
                mimeType = fileRepository.getMimeType(File(originalName)),
                lastModified = file.lastModified(),
                isVaultItem = true
            )
        }
    }

    suspend fun encryptToVault(srcPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val srcFile = File(srcPath)
            if (!srcFile.exists() || srcFile.isDirectory) return@withContext false
            
            val encFile = File(fileRepository.vaultRoot, srcFile.name + ".enc")
            EncryptionHelper.encryptFile(srcFile, encFile)
            
            // Delete source file after successful encryption
            fileRepository.deleteFile(srcPath)
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    suspend fun decryptFromVault(vaultPath: String, destFolder: File = fileRepository.sandboxRoot): Boolean = withContext(Dispatchers.IO) {
        try {
            val encFile = File(vaultPath)
            if (!encFile.exists()) return@withContext false
            
            val originalName = encFile.name.removeSuffix(".enc")
            val destFile = File(destFolder, originalName)
            
            EncryptionHelper.decryptFile(encFile, destFile)
            
            // Delete encrypted vault file after successful decryption using secure wipe
            fileRepository.deleteFile(vaultPath)
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }
}
