package com.example.data.repository

import android.content.Context
import com.example.data.model.FileItem
import com.example.util.EncryptionHelper
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

sealed class GoogleSignInResult {
    data class Success(val email: String, val displayName: String) : GoogleSignInResult()
    data class Failure(val reason: String) : GoogleSignInResult()
    object Cancelled : GoogleSignInResult()
}

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

    fun getGoogleEmailHash(): String? {
        return sharedPrefs.getString("vault_google_email_hash", null)
    }

    fun saveGoogleEmailHash(hash: String): Boolean {
        return sharedPrefs.edit().putString("vault_google_email_hash", hash).commit()
    }

    fun hashEmail(email: String): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(email.lowercase().trim().toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            email
        }
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

    suspend fun signInWithGoogle(context: Context): GoogleSignInResult = withContext(Dispatchers.Main) {
        try {
            val credentialManager = CredentialManager.create(context)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setServerClientId("605488539075-kf4jb3l2ibrgmftd9lm7po76d8jg4f21.apps.googleusercontent.com")
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(context, request)
            val credential = result.credential

            if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val email = googleIdTokenCredential.id
                val displayName = googleIdTokenCredential.displayName ?: ""
                
                // Store SHA-256 hash of email in SharedPreferences
                val emailHash = hashEmail(email)
                saveGoogleEmailHash(emailHash)

                GoogleSignInResult.Success(
                    email = email,
                    displayName = displayName
                )
            } else {
                GoogleSignInResult.Failure("Unexpected credential type: ${credential.type}")
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleSignInResult.Cancelled
        } catch (e: NoCredentialException) {
            GoogleSignInResult.Failure("No Google account found. Use PIN instead.")
        } catch (e: Exception) {
            GoogleSignInResult.Failure(e.localizedMessage ?: "Unknown error")
        }
    }
}
