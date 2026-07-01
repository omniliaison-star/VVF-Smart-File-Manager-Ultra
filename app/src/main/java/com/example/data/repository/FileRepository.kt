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
import com.example.data.database.CategoryEntityDao
import com.example.data.database.CategoryEntity
import com.example.data.database.SecureStateEntity
import com.example.data.database.SecureStateEntityDao
import com.example.data.model.FileItem
import com.example.util.EncryptionHelper
import com.example.util.GeminiService
import com.example.util.ZipHelper
import com.example.util.SecureDeleter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.InputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import com.example.data.database.AppDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.withContext

class FileRepository(
    private val context: Context,
    private val fileDao: FileDao,
    private val embeddingDao: EmbeddingDao,
    private val categoryEntityDao: CategoryEntityDao,
    private val secureStateEntityDao: SecureStateEntityDao
) {
    private val TAG = "FileRepository"
    
    val allCategories: Flow<List<CategoryEntity>> = categoryEntityDao.getAllCategories()
    
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

    private fun securelyWipe(file: File) {
        SecureDeleter.securelyWipe(file)
    }

    /**
     * traditional explorer operation: delete file
     */
    suspend fun deleteFile(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) return@withContext false
            
            // Use SecureDeleter to securely wipe and delete
            val success = SecureDeleter.secureDelete(file)
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

    fun classifyFile(file: DbFile): String {
        val path = file.path
        val mimeType = file.mimeType.lowercase()
        val name = file.name.lowercase()

        return when {
            path.contains("WhatsApp/Media", ignoreCase = true) -> "WhatsApp Media"
            path.contains("Screenshots", ignoreCase = true) -> "Screenshots"
            mimeType.startsWith("image/") -> "Photos"
            mimeType.startsWith("video/") -> "Videos"
            mimeType.startsWith("audio/") -> "Audio"
            mimeType == "application/vnd.android.package-archive" || name.endsWith(".apk") -> "APK"
            path.contains("download", ignoreCase = true) || path.contains("downloads", ignoreCase = true) -> "Downloads"
            mimeType.contains("zip") || mimeType.contains("tar") || mimeType.contains("rar") || mimeType.contains("gzip") ||
                    name.endsWith(".zip") || name.endsWith(".tar") || name.endsWith(".gz") || name.endsWith(".rar") || name.endsWith(".7z") -> "Archives"
            else -> "Documents"
        }
    }

    suspend fun getFilesByCategory(categoryName: String): List<FileItem> = withContext(Dispatchers.IO) {
        val allDbFiles = fileDao.getAllFilesList()
        return@withContext allDbFiles.filter { dbFile ->
            classifyFile(dbFile) == categoryName
        }.map { dbFile ->
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
            
            // Collect SD card files if available
            val sdCardUri = getSdCardUri(context)
            val sdCardFiles = mutableListOf<androidx.documentfile.provider.DocumentFile>()
            if (sdCardUri != null) {
                try {
                    val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, sdCardUri)
                    if (rootDoc != null) {
                        fun traverseDoc(doc: androidx.documentfile.provider.DocumentFile) {
                            if (doc.isDirectory) {
                                val list = doc.listFiles()
                                for (child in list) {
                                    traverseDoc(child)
                                }
                            } else {
                                sdCardFiles.add(doc)
                            }
                        }
                        traverseDoc(rootDoc)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to traverse SD card documents: ${e.message}")
                }
            }
            
            val total = allFiles.size + sdCardFiles.size
            Log.d(TAG, "Found $total files to index (Sandbox: ${allFiles.size}, SD Card: ${sdCardFiles.size}).")
            
            // Clear current records in db to refresh index
            fileDao.clearAllFiles()
            
            val categories = listOf("Photos", "Videos", "Audio", "Documents", "Archives", "APK", "Downloads", "Screenshots", "WhatsApp Media")
            val counts = categories.associateWith { 0 }.toMutableMap()
            val sizes = categories.associateWith { 0L }.toMutableMap()

            val db = AppDatabase.getDatabase(context)
            val batchSize = 50
            var processedCount = 0

            // 1. Index sandbox files
            for (i in allFiles.indices step batchSize) {
                val end = minOf(i + batchSize, allFiles.size)
                val batchFiles = allFiles.subList(i, end)
                
                db.withTransaction {
                    for (file in batchFiles) {
                        val dbFile = DbFile(
                            path = file.absolutePath,
                            name = file.name,
                            size = file.length(),
                            mimeType = getMimeType(file),
                            createdAt = file.lastModified(),
                            modifiedAt = file.lastModified()
                        )
                        val id = fileDao.insertFile(dbFile)

                        // Classify
                        val category = classifyFile(dbFile)
                        counts[category] = (counts[category] ?: 0) + 1
                        sizes[category] = (sizes[category] ?: 0L) + dbFile.size

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
                    }
                }
                
                processedCount += batchFiles.size
                onProgress(processedCount, total)
                kotlinx.coroutines.yield()
                System.gc()
            }

            // 2. Index SD Card files
            for (i in sdCardFiles.indices step batchSize) {
                val end = minOf(i + batchSize, sdCardFiles.size)
                val batchDocs = sdCardFiles.subList(i, end)
                
                db.withTransaction {
                    for (doc in batchDocs) {
                        val dbFile = DbFile(
                            path = doc.uri.toString(),
                            name = doc.name ?: "",
                            size = doc.length(),
                            mimeType = doc.type ?: "",
                            createdAt = doc.lastModified(),
                            modifiedAt = doc.lastModified()
                        )
                        val id = fileDao.insertFile(dbFile)

                        // Classify
                        val category = classifyFile(dbFile)
                        counts[category] = (counts[category] ?: 0) + 1
                        sizes[category] = (sizes[category] ?: 0L) + dbFile.size

                        // If Gemini API Key is available, fetch embedding for SD Card documents
                        if (GeminiService.isApiKeyAvailable() && (dbFile.mimeType.contains("text") || dbFile.mimeType.contains("pdf") || dbFile.name.endsWith(".txt") || dbFile.name.endsWith(".csv"))) {
                            try {
                                val textContent = extractTextContent(doc)
                                if (textContent.isNotEmpty()) {
                                    val vector = GeminiService.getEmbedding(textContent.take(1000))
                                    if (vector != null) {
                                        val vectorString = vector.joinToString(",")
                                        embeddingDao.insertEmbedding(DbEmbedding(fileId = id, vectorData = vectorString))
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed embedding SD card file ${doc.name}: ${e.message}")
                            }
                        }
                    }
                }
                
                processedCount += batchDocs.size
                onProgress(processedCount, total)
                kotlinx.coroutines.yield()
                System.gc()
            }

            // After classification, upsert aggregated counts/sizes into CategoryEntity
            categoryEntityDao.clearAll()
            categories.forEach { categoryName ->
                categoryEntityDao.insertCategory(
                    CategoryEntity(
                        name = categoryName,
                        fileCount = counts[categoryName] ?: 0,
                        totalSize = sizes[categoryName] ?: 0L
                    )
                )
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
        var filesProcessed = 0
        
        suspend fun traverse(dir: File) {
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
                    filesProcessed++
                    if (filesProcessed % 50 == 0) {
                        kotlinx.coroutines.yield()
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

    suspend fun getCachedDuplicates(): Map<String, List<FileItem>> = withContext(Dispatchers.IO) {
        try {
            val entity = secureStateEntityDao.getValue("duplicates_cache") ?: return@withContext emptyMap()
            val moshi = com.squareup.moshi.Moshi.Builder()
                .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                .build()
            val type = com.squareup.moshi.Types.newParameterizedType(
                Map::class.java,
                String::class.java,
                com.squareup.moshi.Types.newParameterizedType(List::class.java, FileItem::class.java)
            )
            val adapter = moshi.adapter<Map<String, List<FileItem>>>(type)
            return@withContext adapter.fromJson(entity.value) ?: emptyMap()
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading cached duplicates", e)
            return@withContext emptyMap()
        }
    }

    suspend fun saveDuplicatesCache(duplicates: Map<String, List<FileItem>>) = withContext(Dispatchers.IO) {
        try {
            val moshi = com.squareup.moshi.Moshi.Builder()
                .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                .build()
            val type = com.squareup.moshi.Types.newParameterizedType(
                Map::class.java,
                String::class.java,
                com.squareup.moshi.Types.newParameterizedType(List::class.java, FileItem::class.java)
            )
            val adapter = moshi.adapter<Map<String, List<FileItem>>>(type)
            val json = adapter.toJson(duplicates)
            secureStateEntityDao.insertValue(SecureStateEntity("duplicates_cache", json))
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving duplicates cache", e)
        }
    }

    suspend fun getCachedSemanticDuplicates(): Map<String, List<FileItem>> = withContext(Dispatchers.IO) {
        try {
            val entity = secureStateEntityDao.getValue("semantic_duplicates_cache") ?: return@withContext emptyMap()
            val moshi = com.squareup.moshi.Moshi.Builder()
                .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                .build()
            val type = com.squareup.moshi.Types.newParameterizedType(
                Map::class.java,
                String::class.java,
                com.squareup.moshi.Types.newParameterizedType(List::class.java, FileItem::class.java)
            )
            val adapter = moshi.adapter<Map<String, List<FileItem>>>(type)
            return@withContext adapter.fromJson(entity.value) ?: emptyMap()
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading cached semantic duplicates", e)
            return@withContext emptyMap()
        }
    }

    suspend fun saveSemanticDuplicatesCache(duplicates: Map<String, List<FileItem>>) = withContext(Dispatchers.IO) {
        try {
            val moshi = com.squareup.moshi.Moshi.Builder()
                .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                .build()
            val type = com.squareup.moshi.Types.newParameterizedType(
                Map::class.java,
                String::class.java,
                com.squareup.moshi.Types.newParameterizedType(List::class.java, FileItem::class.java)
            )
            val adapter = moshi.adapter<Map<String, List<FileItem>>>(type)
            val json = adapter.toJson(duplicates)
            secureStateEntityDao.insertValue(SecureStateEntity("semantic_duplicates_cache", json))
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving semantic duplicates cache", e)
        }
    }

    suspend fun findSemanticDuplicates(): Map<String, List<FileItem>> = withContext(Dispatchers.IO) {
        if (!GeminiService.isApiKeyAvailable()) {
            return@withContext emptyMap()
        }
        val semanticGroups = mutableMapOf<String, MutableList<FileItem>>()
        try {
            val dbEmbeddings = embeddingDao.getAllEmbeddings()
            if (dbEmbeddings.size < 2) return@withContext emptyMap()

            // Resolve file IDs to DbFiles and calculate MD5s to make comparisons robust
            val resolvedEmbeddings = dbEmbeddings.mapNotNull { dbEmb ->
                val dbFile = fileDao.getFileById(dbEmb.fileId) ?: return@mapNotNull null
                val file = File(dbFile.path)
                if (!file.exists()) return@mapNotNull null
                val md5 = calculateMD5(file)
                val vector = dbEmb.vectorData.split(",").map { it.toFloat() }.toFloatArray()
                Triple(dbFile, md5, vector)
            }

            val processedIds = mutableSetOf<Long>()

            for (i in resolvedEmbeddings.indices) {
                val (fileA, md5A, vectorA) = resolvedEmbeddings[i]
                if (processedIds.contains(fileA.id)) continue

                val group = mutableListOf<FileItem>()
                val itemA = FileItem(
                    id = fileA.path,
                    name = fileA.name,
                    path = fileA.path,
                    size = fileA.size,
                    isDirectory = false,
                    mimeType = fileA.mimeType,
                    lastModified = fileA.modifiedAt
                )

                for (j in i + 1 until resolvedEmbeddings.size) {
                    val (fileB, md5B, vectorB) = resolvedEmbeddings[j]
                    if (processedIds.contains(fileB.id)) continue
                    
                    // Skip if they are exact duplicates (same MD5)
                    if (md5A.isNotEmpty() && md5A == md5B) continue

                    // Compare cosine similarity
                    val score = GeminiService.cosineSimilarity(vectorA, vectorB)
                    if (score >= 0.92f) {
                        if (group.isEmpty()) {
                            group.add(itemA)
                            processedIds.add(fileA.id)
                        }
                        group.add(FileItem(
                            id = fileB.path,
                            name = fileB.name,
                            path = fileB.path,
                            size = fileB.size,
                            isDirectory = false,
                            mimeType = fileB.mimeType,
                            lastModified = fileB.modifiedAt
                        ))
                        processedIds.add(fileB.id)
                    }
                }

                if (group.isNotEmpty()) {
                    // Use fileA path as the group key
                    semanticGroups[fileA.path] = group
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding semantic duplicates: ${e.message}", e)
        }
        return@withContext semanticGroups
    }

    /**
     * Scan for junk files (temp, logs, empty folders)
     */
     suspend fun scanJunk(): Map<String, List<FileItem>> = withContext(Dispatchers.IO) {
        val junkMap = mutableMapOf<String, MutableList<FileItem>>()
        junkMap["logs"] = mutableListOf()
        junkMap["temp"] = mutableListOf()
        junkMap["empty"] = mutableListOf()
        junkMap["residual"] = mutableListOf()

        val dbPaths = try {
            fileDao.getAllFilesList().map { it.path }.toSet()
        } catch (e: Exception) {
            emptySet()
        }

        fun traverse(dir: File) {
            val list = dir.listFiles() ?: return
            for (f in list) {
                if (f.isDirectory) {
                    if (isFolderEmptyRecursively(f)) {
                        junkMap["empty"]?.add(
                            FileItem(f.absolutePath, f.name, f.absolutePath, 0L, true, "directory", f.lastModified())
                        )
                    } else {
                        traverse(f)
                    }
                } else {
                    val ext = f.extension.lowercase()
                    val item = FileItem(f.absolutePath, f.name, f.absolutePath, f.length(), false, getMimeType(f), f.lastModified())
                    
                    var addedToJunk = false
                    if (ext == "log" || ext == "bak") {
                        junkMap["logs"]?.add(item)
                        addedToJunk = true
                    } else if (ext == "tmp" || f.name.contains("cache") || f.path.contains("/cache/")) {
                        junkMap["temp"]?.add(item)
                        addedToJunk = true
                    }

                    if (!addedToJunk && !dbPaths.contains(f.absolutePath)) {
                        junkMap["residual"]?.add(item)
                    }
                }
            }
        }

        traverse(sandboxRoot)
        return@withContext junkMap
    }

    private fun isFolderEmptyRecursively(file: File): Boolean {
        val children = file.listFiles() ?: return true
        if (children.isEmpty()) return true
        return children.all { it.isDirectory && isFolderEmptyRecursively(it) }
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

    fun enrichMediaMetadata(item: FileItem): FileItem = try {
        val file = java.io.File(item.path)
        if (!file.exists()) item
        else {
            if (item.mimeType.startsWith("image/")) {
                val exif = android.media.ExifInterface(item.path)
                val width = exif.getAttributeInt(android.media.ExifInterface.TAG_IMAGE_WIDTH, 0).takeIf { it > 0 }
                val height = exif.getAttributeInt(android.media.ExifInterface.TAG_IMAGE_LENGTH, 0).takeIf { it > 0 }
                val dateTakenStr = exif.getAttribute(android.media.ExifInterface.TAG_DATETIME)
                var dateTaken: Long? = null
                if (dateTakenStr != null) {
                    try {
                        val sdf = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.getDefault())
                        dateTaken = sdf.parse(dateTakenStr)?.time
                    } catch (e: Exception) {}
                }
                item.copy(width = width, height = height, dateTaken = dateTaken ?: item.lastModified)
            } else if (item.mimeType.startsWith("video/")) {
                val retriever = android.media.MediaMetadataRetriever()
                var duration: Long? = null
                var resolution: String? = null
                try {
                    retriever.setDataSource(item.path)
                    duration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    val w = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    val h = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    if (w != null && h != null) {
                        resolution = "${w}x${h}"
                    }
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error retrieving video metadata for ${item.path}", e)
                } finally {
                    try { retriever.release() } catch (e: Exception) {}
                }
                item.copy(duration = duration, resolution = resolution)
            } else if (item.mimeType.startsWith("audio/")) {
                val retriever = android.media.MediaMetadataRetriever()
                var duration: Long? = null
                var artist: String? = null
                var album: String? = null
                try {
                    retriever.setDataSource(item.path)
                    duration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    artist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    album = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error retrieving audio metadata for ${item.path}", e)
                } finally {
                    try { retriever.release() } catch (e: Exception) {}
                }
                item.copy(duration = duration, artist = artist, album = album)
            } else {
                item
            }
        }
    } catch (e: Exception) {
        android.util.Log.e(TAG, "Error enriching metadata for ${item.path}", e)
        item
    }

    suspend fun getRecommendationsFromSearchHistory(): List<FileItem> = withContext(Dispatchers.IO) {
        try {
            if (!GeminiService.isApiKeyAvailable()) {
                Log.d("FileRepository", "Gemini API key is not configured. Skipping history-based recommendations.")
                return@withContext emptyList()
            }
            val db = AppDatabase.getDatabase(context)
            val historyList = db.searchHistoryDao().getSearchHistory().first()
            val latest = historyList.firstOrNull() ?: return@withContext emptyList()
            val query = latest.query
            if (query.trim().isEmpty()) return@withContext emptyList()

            val queryEmbedding = GeminiService.getEmbedding(query) ?: return@withContext emptyList()
            val dbEmbeddings = embeddingDao.getAllEmbeddings()
            if (dbEmbeddings.isEmpty()) return@withContext emptyList()

            val scoredFiles = dbEmbeddings.mapNotNull { dbEmb ->
                val dbFile = fileDao.getFileById(dbEmb.fileId) ?: return@mapNotNull null
                val file = File(dbFile.path)
                if (!file.exists()) return@mapNotNull null
                
                val fileVector = dbEmb.vectorData.split(",").map { it.toFloat() }.toFloatArray()
                val score = GeminiService.cosineSimilarity(queryEmbedding, fileVector)
                Pair(dbFile, score)
            }

            val top5 = scoredFiles.sortedByDescending { it.second }.take(5)
            return@withContext top5.map { (dbFile, _) ->
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
        } catch (e: Exception) {
            Log.e("FileRepository", "Error in getRecommendationsFromSearchHistory: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getCategoryBasedRecommendations(): List<FileItem> = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val categories = db.categoryEntityDao().getAllCategories().first()
            val topCategory = categories.maxByOrNull { it.fileCount } ?: return@withContext emptyList()
            if (topCategory.fileCount == 0) return@withContext emptyList()

            val filesInCategory = getFilesByCategory(topCategory.name)
            val sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
            val filtered = filesInCategory.filter { it.lastModified < sevenDaysAgo }
            
            return@withContext filtered.take(5)
        } catch (e: Exception) {
            Log.e("FileRepository", "Error in getCategoryBasedRecommendations: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getLargeFilesRecommendation(): List<FileItem> = withContext(Dispatchers.IO) {
        try {
            val allDbFiles = fileDao.getAllFilesList()
            if (allDbFiles.isEmpty()) return@withContext emptyList()

            val sortedBySec = allDbFiles.sortedByDescending { it.size }
            val n = sortedBySec.size
            val top1PercentCount = maxOf(1, (n * 0.01).toInt())
            val top1PercentFiles = sortedBySec.take(top1PercentCount)

            val ninetyDaysAgo = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000)
            val filtered = top1PercentFiles.filter { it.modifiedAt < ninetyDaysAgo }

            return@withContext filtered.map { dbFile ->
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
        } catch (e: Exception) {
            Log.e("FileRepository", "Error in getLargeFilesRecommendation: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun extractTextContent(filePath: String, mimeType: String): String = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists() || file.isDirectory) return@withContext ""

            when {
                mimeType == "text/plain" || mimeType == "text/csv" || filePath.endsWith(".txt", ignoreCase = true) || filePath.endsWith(".csv", ignoreCase = true) -> {
                    val content = file.readText(Charsets.UTF_8)
                    if (content.length > 50000) {
                        content.substring(0, 50000)
                    } else {
                        content
                    }
                }
                mimeType == "application/pdf" || filePath.endsWith(".pdf", ignoreCase = true) -> {
                    val builder = StringBuilder()
                    try {
                        val bytes = file.readBytes()
                        var i = 0
                        while (i < bytes.size && builder.length < 50000) {
                            if (bytes[i] == '('.code.toByte()) {
                                i++
                                val temp = StringBuilder()
                                var escaped = false
                                while (i < bytes.size) {
                                    val b = bytes[i]
                                    if (escaped) {
                                        temp.append(b.toInt().toChar())
                                        escaped = false
                                    } else if (b == '\\'.code.toByte()) {
                                        escaped = true
                                    } else if (b == ')'.code.toByte()) {
                                        break
                                    } else {
                                        temp.append(b.toInt().toChar())
                                    }
                                    i++
                                }
                                val str = temp.toString().trim()
                                if (str.length > 1 && str.any { it.isLetterOrDigit() }) {
                                    builder.append(str).append(" ")
                                }
                            }
                            i++
                        }
                    } catch (e: Exception) {
                        Log.e("FileRepository", "Raw PDF parsing failed: ${e.message}")
                    }

                    val fullPdfText = builder.toString().trim()
                    if (fullPdfText.length < 100) {
                        "PDF text extraction is not supported without a third-party library. Only the file name and metadata can be summarized."
                    } else if (fullPdfText.length > 50000) {
                        fullPdfText.substring(0, 50000)
                    } else {
                        fullPdfText
                    }
                }
                mimeType.contains("wordprocessingml") || filePath.endsWith(".docx", ignoreCase = true) -> {
                    val builder = StringBuilder()
                    ZipInputStream(FileInputStream(file)).use { zip ->
                        var entry: ZipEntry? = zip.nextEntry
                        while (entry != null) {
                            if (entry.name == "word/document.xml") {
                                val buffer = ByteArray(4096)
                                val out = java.io.ByteArrayOutputStream()
                                var len = zip.read(buffer)
                                while (len > 0) {
                                    out.write(buffer, 0, len)
                                    len = zip.read(buffer)
                                }
                                val rawXml = out.toString("UTF-8")
                                val cleanText = rawXml.replace(Regex("<[^>]+>"), " ")
                                builder.append(cleanText)
                                break
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                    val docxText = builder.toString().replace(Regex("\\s+"), " ").trim()
                    if (docxText.length > 50000) {
                        docxText.substring(0, 50000)
                    } else {
                        docxText
                    }
                }
                else -> ""
            }
        } catch (e: Exception) {
            Log.e("FileRepository", "Error extracting text content: ${e.message}", e)
            ""
        }
    }

    fun getSdCardUri(context: Context): Uri? {
        val prefs = context.getSharedPreferences("vvf_api_prefs", Context.MODE_PRIVATE)
        val uriStr = prefs.getString("sd_card_uri", null)
        return if (uriStr != null) Uri.parse(uriStr) else null
    }

    suspend fun listSdCardFiles(context: Context, folderUri: Uri): List<FileItem> = withContext(Dispatchers.IO) {
        val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext emptyList()
        val files = rootDoc.listFiles()
        return@withContext files.map { doc ->
            FileItem(
                id = doc.uri.toString(),
                name = doc.name ?: "",
                path = doc.uri.toString(),
                size = if (doc.isDirectory) 0L else doc.length(),
                isDirectory = doc.isDirectory,
                mimeType = doc.type ?: (if (doc.isDirectory) "directory" else ""),
                lastModified = doc.lastModified()
            )
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name?.lowercase() ?: "" }))
    }

    suspend fun extractTextContent(doc: androidx.documentfile.provider.DocumentFile): String = withContext(Dispatchers.IO) {
        try {
            val uri = doc.uri
            val mimeType = doc.type ?: ""
            val name = doc.name ?: ""
            
            val isTxtOrCsv = mimeType == "text/plain" || mimeType == "text/csv" || name.endsWith(".txt", ignoreCase = true) || name.endsWith(".csv", ignoreCase = true)
            val isPdf = mimeType == "application/pdf" || name.endsWith(".pdf", ignoreCase = true)
            val isDocx = mimeType.contains("wordprocessingml") || name.endsWith(".docx", ignoreCase = true)

            if (!isTxtOrCsv && !isPdf && !isDocx) return@withContext ""

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                when {
                    isTxtOrCsv -> {
                        val content = inputStream.bufferedReader(Charsets.UTF_8).readText()
                        if (content.length > 50000) {
                            content.substring(0, 50000)
                        } else {
                            content
                        }
                    }
                    isPdf -> {
                        val builder = StringBuilder()
                        try {
                            val bytes = inputStream.readBytes()
                            var i = 0
                            while (i < bytes.size && builder.length < 50000) {
                                if (bytes[i] == '('.code.toByte()) {
                                    i++
                                    val temp = StringBuilder()
                                    var escaped = false
                                    while (i < bytes.size) {
                                        val b = bytes[i]
                                        if (escaped) {
                                            temp.append(b.toInt().toChar())
                                            escaped = false
                                        } else if (b == '\\'.code.toByte()) {
                                            escaped = true
                                        } else if (b == ')'.code.toByte()) {
                                            break
                                        } else {
                                            temp.append(b.toInt().toChar())
                                        }
                                        i++
                                    }
                                    val str = temp.toString().trim()
                                    if (str.length > 1 && str.any { it.isLetterOrDigit() }) {
                                        builder.append(str).append(" ")
                                    }
                                }
                                i++
                            }
                        } catch (e: Exception) {
                            Log.e("FileRepository", "Raw PDF parsing failed: ${e.message}")
                        }

                        val fullPdfText = builder.toString().trim()
                        if (fullPdfText.length < 100) {
                            "PDF text extraction is not supported without a third-party library. Only the file name and metadata can be summarized."
                        } else if (fullPdfText.length > 50000) {
                            fullPdfText.substring(0, 50000)
                        } else {
                            fullPdfText
                        }
                    }
                    isDocx -> {
                        val builder = StringBuilder()
                        java.util.zip.ZipInputStream(inputStream).use { zip ->
                            var entry = zip.nextEntry
                            while (entry != null) {
                                if (entry.name == "word/document.xml") {
                                    val buffer = ByteArray(4096)
                                    val out = java.io.ByteArrayOutputStream()
                                    var len = zip.read(buffer)
                                    while (len > 0) {
                                        out.write(buffer, 0, len)
                                        len = zip.read(buffer)
                                    }
                                    val rawXml = out.toString("UTF-8")
                                    val cleanText = rawXml.replace(Regex("<[^>]+>"), " ")
                                    builder.append(cleanText)
                                    break
                                }
                                entry = zip.nextEntry
                            }
                        }
                        val result = builder.toString().trim()
                        if (result.length > 50000) {
                            result.substring(0, 50000)
                        } else {
                            result
                        }
                    }
                    else -> ""
                }
            } ?: ""
        } catch (e: Exception) {
            Log.e("FileRepository", "Failed to extract text from DocumentFile: ${e.message}")
            ""
        }
    }
}
