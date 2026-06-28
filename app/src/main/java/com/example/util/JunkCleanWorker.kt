package com.example.util

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.database.AppDatabase
import com.example.data.repository.FileRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class JunkCleanWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d("JunkCleanWorker", "Starting background periodic junk cleanup...")
        try {
            val db = AppDatabase.getDatabase(applicationContext)
            val fileRepository = FileRepository(applicationContext, db.fileDao(), db.embeddingDao(), db.categoryEntityDao(), db.secureStateEntityDao())
            
            // Run standard scan
            val junkMap = fileRepository.scanJunk()
            
            // Clean up files in logs and temp
            var deletedCount = 0
            var deletedBytes = 0L
            
            junkMap["logs"]?.forEach { item ->
                val file = File(item.path)
                if (file.exists() && SecureDeleter.secureDelete(file)) {
                    deletedCount++
                    deletedBytes += item.size
                }
            }
            junkMap["temp"]?.forEach { item ->
                val file = File(item.path)
                if (file.exists() && SecureDeleter.secureDelete(file)) {
                    deletedCount++
                    deletedBytes += item.size
                }
            }
            
            // Clean empty dirs
            junkMap["empty"]?.forEach { item ->
                val file = File(item.path)
                if (file.exists() && SecureDeleter.secureDelete(file)) {
                    deletedCount++
                }
            }

            Log.d("JunkCleanWorker", "Background junk cleanup finished. Deleted $deletedCount items, reclaimed $deletedBytes bytes.")
            Result.success()
        } catch (e: Exception) {
            Log.e("JunkCleanWorker", "Junk clean worker failed", e)
            Result.retry()
        }
    }
}
