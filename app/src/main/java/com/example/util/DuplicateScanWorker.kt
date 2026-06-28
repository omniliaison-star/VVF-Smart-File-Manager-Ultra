package com.example.util

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.database.AppDatabase
import com.example.data.repository.FileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DuplicateScanWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d("DuplicateScanWorker", "Starting background periodic duplicate scanning...")
        try {
            val db = AppDatabase.getDatabase(applicationContext)
            val fileRepository = FileRepository(
                applicationContext,
                db.fileDao(),
                db.embeddingDao(),
                db.categoryEntityDao(),
                db.secureStateEntityDao()
            )
            
            // 1. Scan exact duplicates
            val exactDuplicates = fileRepository.findDuplicates()
            fileRepository.saveDuplicatesCache(exactDuplicates)
            
            // 2. Scan semantic duplicates
            val semanticDuplicates = fileRepository.findSemanticDuplicates()
            fileRepository.saveSemanticDuplicatesCache(semanticDuplicates)
            
            Log.d("DuplicateScanWorker", "Background periodic duplicate scanning completed successfully.")
            Result.success()
        } catch (e: Exception) {
            Log.e("DuplicateScanWorker", "Background duplicate scanning failed", e)
            Result.failure()
        }
    }
}
