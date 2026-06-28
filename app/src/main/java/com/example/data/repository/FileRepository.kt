package com.example.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import com.example.data.database.DbEmbedding
import com.example.data.database.DbFile
import com.example.data.database.FileDao
import com.example.data.database.EmbeddingDao
import com.example.data.model.FileItem
import com.example.util.EncryptionHelper
import com.example.util.GeminiService
import com.example.util.ZipHelper
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class FileRepository(
    private val context: Context,
    private val fileDao: FileDao,
    private val embeddingDao: EmbeddingDao
) {
    private val TAG = "FileRepository"
    
    // Sandbox root directory for file manager operations
    val sandboxRoot: File by lazy {
        File(context.filesDir, "VVF_Smart_Explorer").apply {
            if (!exists()) {
                mkdirs()
                prepopulateSandbox(this)
            }
        }
    }

    // Vault folder inside app's private storage
    val vaultRoot: File by lazy {
        File(context.filesDir, "VVF_Vault").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    /**
     * Helper to get standard file list in a given path.
     */
    suspend fun getFilesAndFolders(dirPath: String): List<FileItem> = withContext(Dispatchers.IO) {
        val targetDir = if (dirPath.isEmpty()) sandboxRoot else File(dirPath)
        if (!targetDir.exists() || !targetDir.isDirectory) return@withContext emptyList()

        val filesList = targetDir.listFiles() ?: return@withContext emptyList()
        return@withContext filesList.map { file ->
            FileItem(
                id = file.absolutePath,
                name = file.name,
                path = file.absolutePath,
                size = if (file.isDirectory) 0L else file.length(),
                isDirectory = file.isDirectory,
                mimeType = getMimeType(file),
                lastModified = file.lastModified()
            )
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    /**
     * Populate standard folders & realistic files on first launch to demonstrate features.
     */
    private fun prepopulateSandbox(root: File) {
        try {
            val docsDir = File(root, "Documents").apply { mkdirs() }
            val photosDir = File(root, "Photos").apply { mkdirs() }
            val audioDir = File(root, "Audio").apply { mkdirs() }
            val junkDir = File(root, "TempJunk").apply { mkdirs() }

            // Document 1
            File(docsDir, "Policy_Draft_Final.pdf").writeText(
                "Hemp Policy Draft and Regulatory Policy.\n" +
                "This document covers the industrial hemp development regulations, " +
                "agricultural policies, and tetrahydrocannabinol concentration limits."
            )

            // Document 2
            File(docsDir, "Quantum_Computing_Intro.txt").writeText(
                "Introduction to Quantum Information and Quantum Physics.\n" +
                "This paper discusses quantum superposition, entanglement, qubits, " +
                "and Shor's factoring algorithm."
            )

            // Document 3 (A duplicate of Document 1 to test Duplicate Cleaner!)
            File(docsDir, "Policy_Draft_Backup_Copy.pdf").writeText(
                "Hemp Policy Draft and Regulatory Policy.\n" +
                "This document covers the industrial hemp development regulations, " +
                "agricultural policies, and tetrahydrocannabinol concentration limits."
            )

            // Image 1
            File(photosDir, "IMG_202501_mountain.jpg").writeText("MOCK_IMAGE_DATA_MOUNTAIN_HIKE_2025")
            // Image 2 (Duplicate to clean)
            File(photosDir, "IMG_202501_mountain_copy.jpg").writeText("MOCK_IMAGE_DATA_MOUNTAIN_HIKE_2025")
            // Image 3
            File(photosDir, "IMG_202502_beach.jpg").writeText("MOCK_IMAGE_DATA_SUNSET_BEACH_TRIP")

            // Audio 1
            File(audioDir, "Acoustic_Guitar_Solo.mp3").writeText("MOCK_AUDIO_GUITAR_RECORDING")

            // Junk items to clean
            File(junkDir, "system_dump.log").writeText("DEBUG log output: 2026-06-27 Crash details info...")
            File(junkDir, "session_cache_091.tmp").writeText("TEMP session cache details.")
            File(root, "empty_folder_to_delete").mkdirs()

            Log.d(TAG, "Sandbox prepopulated successfully!")
        } catch (e: Exception) {
            Log.e(TAG, "Error prepopulating sandbox: ${e.message}")
        }
    }

    /**
     * Query MediaStore for Images on the device.
     */
    suspend fun getMediaStoreImages(): List<FileItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<FileItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATA
        )
        val cursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null,
            "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        )
        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val name = c.getString(nameCol)
                val size = c.getLong(sizeCol)
                val date = c.getLong(dateCol) * 1000 // Convert to MS
                val mime = c.getString(mimeCol)
                val path = c.getString(dataCol) ?: ""
                list.add(FileItem(id.toString(), name, path, size, false, mime, date))
            }
        }
        return@withContext list
    }

    /**
     * Query MediaStore for Videos.
     */
    suspend fun getMediaStoreVideos(): List<FileItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<FileItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATA
        )
        cursorQuery(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, list)
        return@withContext list
    }

    /**
     * Query MediaStore for Audio.
     */
    suspend fun getMediaStoreAudio(): List<FileItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<FileItem>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATA
        )
        cursorQuery(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, list)
        return@withContext list
    }

    private fun cursorQuery(uri: Uri, projection: Array<String>, list: MutableList<FileItem>) {
        val cursor = context.contentResolver.query(
            uri, projection, null, null,
            "${projection[3]} DESC"
        )
        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(projection[0])
            val nameCol = c.getColumnIndexOrThrow(projection[1])
            val sizeCol = c.getColumnIndexOrThrow(projection[2])
            val dateCol = c.getColumnIndexOrThrow(projection[3])
            val mimeCol = c.getColumnIndexOrThrow(projection[4])
            val dataCol = c.getColumnIndexOrThrow(projection[5])

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val name = c.getString(nameCol)
                val size = c.getLong(sizeCol)
                val date = c.getLong(dateCol) * 1000
                val mime = c.getString(mimeCol)
                val path = c.getString(dataCol) ?: ""
                list.add(FileItem(id.toString(), name, path, size, false, mime, date))
            }
        }
    }

    /**
     * traditional explorer operation: copy file
     */
    suspend fun copyFile(srcPath: String, destDirPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val srcFile = File(srcPath)
            val destDir = File(destDirPath)
            if (!srcFile.exists() || !destDir.exists()) return@withContext false
            val destFile = File(destDir, srcFile.name)
            
            if (srcFile.isDirectory) {
                destFile.mkdirs()
                srcFile.listFiles()?.forEach { child ->
                    copyFile(child.absolutePath, destFile.absolutePath)
                }
            } else {
                FileInputStream(srcFile).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error copying file: ${e.message}")
            return@withContext false
        }
    }

    /**
     * traditional explorer operation: move file
     */
    suspend fun moveFile(srcPath: String, destDirPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val srcFile = File(srcPath)
            val destDir = File(destDirPath)
            if (!srcFile.exists() || !destDir.exists()) return@withContext false
            val destFile = File(destDir, srcFile.name)
            
            val success = srcFile.renameTo(destFile)
            if (!success) {
                // Fallback to copy and delete if rename fails across file systems
                if (copyFile(srcPath, destDirPath)) {
                    deleteFile(srcPath)
                    return@withContext true
                }
                return@withContext false
            }
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error moving file: ${e.message}")
            return@withContext false
        }
    }

    /**
     * traditional explorer operation: rename file
     */
    suspend fun renameFile(filePath: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) return@withContext false
            val parent = file.parentFile ?: return@withContext false
            val destFile = File(parent, newName)
            return@withContext file.renameTo(destFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error renaming file: ${e.message}")
            return@withContext false
        }
    }

    /**
     * traditional explorer operation: delete file
     */
    suspend fun deleteFile(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) return@withContext false
            val success = if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
            if (success) {
                fileDao.deleteFileByPath(filePath)
            }
            return@withContext success
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file: ${e.message}")
            return@withContext false
        }
    }

    /**
     * Create a new folder
     */
    suspend fun createFolder(parentPath: String, folderName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val parent = if (parentPath.isEmpty()) sandboxRoot else File(parentPath)
            val newFolder = File(parent, folderName)
            return@withContext newFolder.mkdirs()
        } catch (e: Exception) {
            Log.e(TAG, "Error creating folder: ${e.message}")
            return@withContext false
        }
    }

    /**
     * Utility to get mime type
     */
    fun getMimeType(file: File): String {
        if (file.isDirectory) return "directory"
        val extension = MimeTypeMap.getFileExtensionFromUrl(file.absolutePath)
        if (extension != null) {
            val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            if (type != null) return type
        }
        return when (file.extension.lowercase()) {
            "txt" -> "text/plain"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "tar" -> "application/x-tar"
            "gz" -> "application/gzip"
            "mp3" -> "audio/mpeg"
            "mp4" -> "video/mp4"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "apk" -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }

    /**
     * Index files in background and insert to DB
     */
    suspend fun indexAllFiles(onProgress: (Int, Int) -> Unit = { _, _ -> }) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting file indexing on sandbox...")
            val allFiles = mutableListOf<File>()
            fun traverse(dir: File) {
                val list = dir.listFiles() ?: return
                for (f in list) {
                    if (f.isDirectory) {
                        traverse(f)
                    } else {
                        allFiles.add(f)
                    }
                }
            }
            traverse(sandboxRoot)
            
            val total = allFiles.size
            Log.d(TAG, "Found $total files to index.")
            
            // Clear current records in db to refresh index
            fileDao.clearAllFiles()
            
            allFiles.forEachIndexed { index, file ->
                val dbFile = DbFile(
                    path = file.absolutePath,
                    name = file.name,
                    size = file.length(),
                    mimeType = getMimeType(file),
                    createdAt = file.lastModified(),
                    modifiedAt = file.lastModified()
                )
                val id = fileDao.insertFile(dbFile)

                // If Gemini API Key is available, fetch embedding for documents
                if (GeminiService.isApiKeyAvailable() && (dbFile.mimeType.contains("text") || dbFile.mimeType.contains("pdf"))) {
                    try {
                        val fileContent = file.readText().take(1000) // embed first 1000 chars
                        val vector = GeminiService.getEmbedding(fileContent)
                        if (vector != null) {
                            val vectorString = vector.joinToString(",")
                            embeddingDao.insertEmbedding(DbEmbedding(fileId = id, vectorData = vectorString))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed embedding file ${file.name}: ${e.message}")
                    }
                }
                
                onProgress(index + 1, total)
            }
            Log.d(TAG, "File indexing complete!")
        } catch (e: Exception) {
            Log.e(TAG, "Error indexing files: ${e.message}", e)
        }
    }

    /**
     * Compute file checksum/MD5 to find duplicates
     */
    fun calculateMD5(file: File): String {
        if (!file.exists() || file.isDirectory) return ""
        return try {
            val digest = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(8192)
            var bytesRead: Int
            FileInputStream(file).use { fis ->
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val md5Bytes = digest.digest()
            md5Bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Find Duplicate files based on MD5 checksum
     */
    suspend fun findDuplicates(): Map<String, List<FileItem>> = withContext(Dispatchers.IO) {
        val duplicatesMap = mutableMapOf<String, MutableList<FileItem>>()
        val filesByMD5 = mutableMapOf<String, MutableList<FileItem>>()
        
        fun traverse(dir: File) {
            val list = dir.listFiles() ?: return
            for (f in list) {
                if (f.isDirectory) {
                    traverse(f)
                } else {
                    val md5 = calculateMD5(f)
                    if (md5.isNotEmpty()) {
                        val item = FileItem(
                            id = f.absolutePath,
                            name = f.name,
                            path = f.absolutePath,
                            size = f.length(),
                            isDirectory = false,
                            mimeType = getMimeType(f),
                            lastModified = f.lastModified()
                        )
                        filesByMD5.getOrPut(md5) { mutableListOf() }.add(item)
                    }
                }
            }
        }

        traverse(sandboxRoot)

        // Filter keys that have more than 1 file (actual duplicates!)
        for ((md5, list) in filesByMD5) {
            if (list.size > 1) {
                duplicatesMap[md5] = list
            }
        }

        return@withContext duplicatesMap
    }

    /**
     * Scan for junk files (temp, logs, empty folders)
     */
    suspend fun scanJunk(): Map<String, List<FileItem>> = withContext(Dispatchers.IO) {
        val junkMap = mutableMapOf<String, MutableList<FileItem>>()
        junkMap["logs"] = mutableListOf()
        junkMap["temp"] = mutableListOf()
        junkMap["empty"] = mutableListOf()

        fun traverse(dir: File) {
            val list = dir.listFiles() ?: return
            for (f in list) {
                if (f.isDirectory) {
                    val children = f.listFiles()
                    if (children == null || children.isEmpty()) {
                        junkMap["empty"]?.add(
                            FileItem(f.absolutePath, f.name, f.absolutePath, 0L, true, "directory", f.lastModified())
                        )
                    } else {
                        traverse(f)
                    }
                } else {
                    val ext = f.extension.lowercase()
                    val item = FileItem(f.absolutePath, f.name, f.absolutePath, f.length(), false, getMimeType(f), f.lastModified())
                    if (ext == "log") {
                        junkMap["logs"]?.add(item)
                    } else if (ext == "tmp" || f.name.contains("cache")) {
                        junkMap["temp"]?.add(item)
                    }
                }
            }
        }

        traverse(sandboxRoot)
        return@withContext junkMap
    }

    /**
     * AI Semantic Search or local fallback keyword search
     */
    suspend fun semanticSearch(query: String): List<FileItem> = withContext(Dispatchers.IO) {
        // Step 1: Save history
        // (will be done in Viewmodel)

        // Step 2: Semantic check
        if (GeminiService.isApiKeyAvailable()) {
            val queryEmbedding = GeminiService.getEmbedding(query)
            if (queryEmbedding != null) {
                val dbEmbeddings = embeddingDao.getAllEmbeddings()
                val scoredFiles = mutableListOf<Pair<DbFile, Float>>()
                
                for (dbEmb in dbEmbeddings) {
                    val file = fileDao.getFileById(dbEmb.fileId) ?: continue
                    val fileVector = dbEmb.vectorData.split(",").map { it.toFloat() }.toFloatArray()
                    val score = GeminiService.cosineSimilarity(queryEmbedding, fileVector)
                    scoredFiles.add(Pair(file, score))
                }
                
                // Sort by similarity descending, filtering a threshold
                val results = scoredFiles.filter { it.second > 0.15f }
                    .sortedByDescending { it.second }
                    .map { Pair(it.first, it.second) }
                
                if (results.isNotEmpty()) {
                    Log.d(TAG, "Gemini Semantic Search matched ${results.size} files.")
                    return@withContext results.map { (dbFile, score) ->
                        FileItem(
                            id = dbFile.path,
                            name = "${dbFile.name} (Match: ${(score * 100).toInt()}%)",
                            path = dbFile.path,
                            size = dbFile.size,
                            isDirectory = false,
                            mimeType = dbFile.mimeType,
                            lastModified = dbFile.modifiedAt
                        )
                    }
                }
            }
        }

        // Step 3: Offline fallback search
        Log.d(TAG, "Falling back to local keyword semantic ranking search.")
        val allFiles = fileDao.getAllFilesList()
        val results = mutableListOf<Pair<DbFile, Int>>()
        
        val queryWords = query.lowercase().split(Regex("\\s+")).filter { it.length > 2 }
        for (f in allFiles) {
            var score = 0
            val nameLower = f.name.lowercase()
            val pathLower = f.path.lowercase()
            
            // Check literal keyword contains
            if (nameLower.contains(query.lowercase())) {
                score += 50
            }
            
            // Keyword overlaps
            for (word in queryWords) {
                if (nameLower.contains(word)) score += 10
                if (pathLower.contains(word)) score += 2
            }
            
            // Smart file categories matches:
            // e.g. Query "photos" or "images" matches image files!
            if ((query.contains("photo") || query.contains("image") || query.contains("pic")) && f.mimeType.startsWith("image/")) {
                score += 30
            }
            if ((query.contains("doc") || query.contains("pdf") || query.contains("paper") || query.contains("policy")) && 
                (f.mimeType.contains("pdf") || f.mimeType.contains("text") || f.mimeType.contains("document"))) {
                score += 35
            }
            if ((query.contains("song") || query.contains("audio") || query.contains("music")) && f.mimeType.startsWith("audio/")) {
                score += 30
            }
            if ((query.contains("video") || query.contains("movie")) && f.mimeType.startsWith("video/")) {
                score += 30
            }

            if (score > 0) {
                results.add(Pair(f, score))
            }
        }

        return@withContext results.sortedByDescending { it.second }.map { (dbFile, score) ->
            FileItem(
                id = dbFile.path,
                name = dbFile.name,
                path = dbFile.path,
                size = dbFile.size,
                isDirectory = false,
                mimeType = dbFile.mimeType,
                lastModified = dbFile.modifiedAt
            )
        }
    }
}
