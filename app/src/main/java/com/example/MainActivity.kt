package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.AppNavigationWrapper
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Schedule periodic background junk cleanup using WorkManager
        try {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()
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
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            MyApplicationTheme {
                val viewModel: MainViewModel = viewModel()
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
