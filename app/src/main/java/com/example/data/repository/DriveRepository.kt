package com.example.data.repository

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.example.data.model.FileItem
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

sealed class DriveConnectionResult {
    data class Success(val email: String) : DriveConnectionResult()
    data class Failure(val reason: String) : DriveConnectionResult()
    object Cancelled : DriveConnectionResult()
}

object DriveSignInCoordinator {
    var onLaunchSignInIntent: ((Intent) -> Unit)? = null
    private var pendingResult: CompletableDeferred<DriveConnectionResult>? = null

    fun initiateSignIn(intent: Intent): CompletableDeferred<DriveConnectionResult> {
        val deferred = CompletableDeferred<DriveConnectionResult>()
        pendingResult = deferred
        onLaunchSignInIntent?.invoke(intent) ?: run {
            deferred.complete(DriveConnectionResult.Failure("Sign in coordinator not initialized in activity"))
        }
        return deferred
    }

    fun onSignInResult(result: DriveConnectionResult) {
        pendingResult?.complete(result)
        pendingResult = null
    }
}

class DriveRepository(private val context: Context) {

    fun getEncryptedPrefs(context: Context): SharedPreferences {
        return try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                "drive_secure_prefs",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e("DriveRepository", "Error creating EncryptedSharedPreferences: ${e.message}", e)
            context.getSharedPreferences("drive_secure_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    fun isConnected(context: Context): Boolean {
        val prefs = getEncryptedPrefs(context)
        return prefs.contains("drive_access_token")
    }

    fun getConnectedEmailDisplay(context: Context): String? {
        val prefs = getEncryptedPrefs(context)
        return prefs.getString("drive_user_email_display", null)
    }

    suspend fun connectDrive(activity: Activity): DriveConnectionResult = withContext(Dispatchers.Main) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/drive.file"))
            .requestIdToken("605488539075-kf4jb3l2ibrgmftd9lm7po76d8jg4f21.apps.googleusercontent.com")
            .requestServerAuthCode("605488539075-kf4jb3l2ibrgmftd9lm7po76d8jg4f21.apps.googleusercontent.com")
            .build()

        val googleSignInClient = GoogleSignIn.getClient(activity, gso)
        try {
            googleSignInClient.signOut()
        } catch (e: Exception) {
            Log.e("DriveRepository", "Sign out error: ${e.message}")
        }

        val signInIntent = googleSignInClient.signInIntent
        val deferred = DriveSignInCoordinator.initiateSignIn(signInIntent)
        deferred.await()
    }

    private fun getDriveService(context: Context): Drive? {
        val sharedPreferences = getEncryptedPrefs(context)
        val token = sharedPreferences.getString("drive_access_token", null) ?: return null
        
        val transport = NetHttpTransport()
        val jsonFactory = GsonFactory.getDefaultInstance()
        
        return Drive.Builder(
            transport,
            jsonFactory
        ) { request ->
            request.headers.authorization = "Bearer $token"
        }
        .setApplicationName("Smart Explorer")
        .build()
    }

    suspend fun listDriveFiles(context: Context): List<FileItem> = withContext(Dispatchers.IO) {
        if (!isConnected(context)) return@withContext emptyList()
        val service = getDriveService(context) ?: return@withContext emptyList()
        try {
            val result = service.files().list()
                .setQ("trashed = false")
                .setFields("files(id, name, size, mimeType, modifiedTime)")
                .execute()

            val files = result.getFiles() ?: emptyList()
            files.map { file ->
                val size = file.getSize() ?: 0L
                val modifiedTime = file.getModifiedTime()?.getValue() ?: 0L
                val mime = file.getMimeType() ?: ""
                val isDir = mime == "application/vnd.google-apps.folder"
                FileItem(
                    id = file.getId() ?: "",
                    name = file.getName() ?: "",
                    path = file.getId() ?: "",
                    size = size,
                    isDirectory = isDir,
                    mimeType = mime,
                    lastModified = modifiedTime,
                    isVaultItem = false
                )
            }
        } catch (e: GoogleJsonResponseException) {
            Log.e("DriveRepository", "Google JSON API error: ${e.statusCode} - ${e.message}", e)
            if (e.statusCode == 401 || e.statusCode == 403) {
                withContext(Dispatchers.Main) {
                    disconnectDrive(context)
                }
            }
            emptyList()
        } catch (e: Exception) {
            Log.e("DriveRepository", "Error listing Drive files: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun searchDriveFiles(query: String, context: Context): List<FileItem> = withContext(Dispatchers.IO) {
        if (!isConnected(context)) return@withContext emptyList()
        val service = getDriveService(context) ?: return@withContext emptyList()
        try {
            val escapedQuery = query.replace("'", "\\'")
            val qString = "name contains '$escapedQuery' and trashed = false"
            val result = service.files().list()
                .setQ(qString)
                .setFields("files(id, name, size, mimeType, modifiedTime)")
                .execute()

            val files = result.getFiles() ?: emptyList()
            files.map { file ->
                val size = file.getSize() ?: 0L
                val modifiedTime = file.getModifiedTime()?.getValue() ?: 0L
                val mime = file.getMimeType() ?: ""
                val isDir = mime == "application/vnd.google-apps.folder"
                FileItem(
                    id = file.getId() ?: "",
                    name = file.getName() ?: "",
                    path = file.getId() ?: "",
                    size = size,
                    isDirectory = isDir,
                    mimeType = mime,
                    lastModified = modifiedTime,
                    isVaultItem = false
                )
            }
        } catch (e: GoogleJsonResponseException) {
            Log.e("DriveRepository", "Google JSON search error: ${e.statusCode} - ${e.message}", e)
            if (e.statusCode == 401 || e.statusCode == 403) {
                withContext(Dispatchers.Main) {
                    disconnectDrive(context)
                }
            }
            emptyList()
        } catch (e: Exception) {
            Log.e("DriveRepository", "Error searching Drive files: ${e.message}", e)
            emptyList()
        }
    }

    fun disconnectDrive(context: Context) {
        val prefs = getEncryptedPrefs(context)
        prefs.edit()
            .remove("drive_access_token")
            .remove("drive_user_email_hash")
            .remove("drive_user_email_display")
            .apply()
        
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
            val googleSignInClient = GoogleSignIn.getClient(context, gso)
            googleSignInClient.signOut()
            googleSignInClient.revokeAccess()
        } catch (e: Exception) {
            Log.e("DriveRepository", "Error on revoke access: ${e.message}", e)
        }
    }

    suspend fun downloadDriveFile(fileId: String, context: Context, outputFile: java.io.File): Boolean = withContext(Dispatchers.IO) {
        if (!isConnected(context)) return@withContext false
        val service = getDriveService(context) ?: return@withContext false
        try {
            outputFile.parentFile?.mkdirs()
            outputFile.outputStream().use { outputStream ->
                service.files().get(fileId).executeMediaAndDownloadTo(outputStream)
            }
            true
        } catch (e: Exception) {
            Log.e("DriveRepository", "Error downloading Drive file $fileId: ${e.message}", e)
            false
        }
    }

    fun sha256(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
            hash.fold("") { str, it -> str + "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }
}
