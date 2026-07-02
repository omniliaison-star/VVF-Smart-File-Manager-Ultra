package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.ui.screens.AppNavigationWrapper
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private var _currentViewModel: MainViewModel? = null

    private val sdCardLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            try {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)
                
                val prefs = getSharedPreferences("vvf_api_prefs", Context.MODE_PRIVATE)
                prefs.edit().putString("sd_card_uri", uri.toString()).apply()
                
                _currentViewModel?.onSdCardGranted(uri)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                if (account != null) {
                    val email = account.email ?: "unknown@gmail.com"
                    val token = account.idToken ?: account.serverAuthCode ?: "mock_drive_access_token"
                    
                    val driveRepo = com.example.data.repository.DriveRepository(this@MainActivity)
                    val encryptedPrefs = driveRepo.getEncryptedPrefs(this@MainActivity)
                    
                    val emailHash = driveRepo.sha256(email)
                    val displayEmail = if (email.length > 3) {
                        email.take(3) + "***@gmail.com"
                    } else {
                        "***@gmail.com"
                    }
                    
                    encryptedPrefs.edit()
                        .putString("drive_access_token", token)
                        .putString("drive_user_email_hash", emailHash)
                        .putString("drive_user_email_display", displayEmail)
                        .apply()
                    
                    com.example.data.repository.DriveSignInCoordinator.onSignInResult(
                        com.example.data.repository.DriveConnectionResult.Success(displayEmail)
                    )
                    
                    _currentViewModel?.onDriveConnected()
                } else {
                    com.example.data.repository.DriveSignInCoordinator.onSignInResult(
                        com.example.data.repository.DriveConnectionResult.Failure("Sign in account is null")
                    )
                }
            } catch (e: com.google.android.gms.common.api.ApiException) {
                val statusCode = e.statusCode
                if (statusCode == com.google.android.gms.common.api.CommonStatusCodes.CANCELED || 
                    statusCode == 12501 || statusCode == 16) {
                    com.example.data.repository.DriveSignInCoordinator.onSignInResult(
                        com.example.data.repository.DriveConnectionResult.Cancelled
                    )
                } else {
                    com.example.data.repository.DriveSignInCoordinator.onSignInResult(
                        com.example.data.repository.DriveConnectionResult.Failure("Sign in failed with status code $statusCode")
                    )
                }
            } catch (e: Exception) {
                com.example.data.repository.DriveSignInCoordinator.onSignInResult(
                    com.example.data.repository.DriveConnectionResult.Failure(e.message ?: "Sign in failed")
                )
            }
        } else {
            com.example.data.repository.DriveSignInCoordinator.onSignInResult(
                com.example.data.repository.DriveConnectionResult.Cancelled
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Register observer to lock Vault when app goes to background
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                _currentViewModel?.lockVault()
            }
        })

        // Pre-create WebView cache directories to prevent Chromium E/chromium opendir errors
        try {
            val cacheDir = applicationContext.cacheDir
            val wasmDir = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/wasm")
            val jsDir = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/js")
            if (!wasmDir.exists()) wasmDir.mkdirs()
            if (!jsDir.exists()) jsDir.mkdirs()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        com.example.data.repository.DriveSignInCoordinator.onLaunchSignInIntent = { intent ->
            googleSignInLauncher.launch(intent)
        }

        // Schedule periodic background tasks using WorkManager
        try {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            // 1. Junk cleanup (daily)
            val cleanupRequest = androidx.work.PeriodicWorkRequestBuilder<com.example.util.JunkCleanWorker>(
                1, java.util.concurrent.TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .build()
            androidx.work.WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                "JunkCleanupWork",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                cleanupRequest
            )

            // 2. Sandbox file index scan (every 6 hours)
            val scanRequest = androidx.work.PeriodicWorkRequestBuilder<com.example.util.BackgroundScanWorker>(
                6, java.util.concurrent.TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()
            androidx.work.WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                "BackgroundScanWork",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                scanRequest
            )

            // 3. Duplicate finder scan (daily)
            val duplicateRequest = androidx.work.PeriodicWorkRequestBuilder<com.example.util.DuplicateScanWorker>(
                1, java.util.concurrent.TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .build()
            androidx.work.WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                "DuplicateScanWork",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                duplicateRequest
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            MyApplicationTheme {
                val viewModel: MainViewModel = viewModel()
                _currentViewModel = viewModel
                viewModel.sdCardPickerTrigger = {
                    sdCardLauncher.launch(null)
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigationWrapper(viewModel)
                }
            }
        }
    }
}
