package com.example.util

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.database.AppDatabase
import com.example.data.repository.FileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackgroundScanWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d("BackgroundScanWorker", "Starting background periodic sandbox file scan...")
        try {
            val db = AppDatabase.getDatabase(applicationContext)
            val fileRepository = FileRepository(
                applicationContext,
                db.fileDao(),
                db.embeddingDao(),
                db.categoryEntityDao(),
                db.secureStateEntityDao()
            )
            fileRepository.indexAllFiles()
            Log.d("BackgroundScanWorker", "Background periodic file scan completed successfully.")
            Result.success()
        } catch (e: Exception) {
            Log.e("BackgroundScanWorker", "Background file scan failed", e)
            Result.failure()
        }
    }
}
