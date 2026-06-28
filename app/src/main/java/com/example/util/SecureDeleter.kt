package com.example.util

import android.util.Log
import java.io.File
import java.io.RandomAccessFile

object SecureDeleter {
    private const val TAG = "SecureDeleter"

    /**
     * Overwrites the file contents with zero bytes using RandomAccessFile in 'rws' mode,
     * and then deletes the file. If the file is a directory, it recursively securely deletes all files inside.
     *
     * @param file The file or directory to securely delete.
     * @return True if the deletion was successful, false otherwise.
     */
    fun secureDelete(file: File): Boolean {
        try {
            if (!file.exists()) return false

            if (file.isDirectory) {
                val children = file.listFiles()
                var allSuccess = true
                if (children != null) {
                    for (child in children) {
                        if (!secureDelete(child)) {
                            allSuccess = false
                        }
                    }
                }
                return file.delete() && allSuccess
            } else {
                // Securely overwrite before deleting
                securelyWipe(file)
                return file.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to secure delete file: ${file.absolutePath}", e)
            // Fallback to normal delete in case of failure to wipe (to avoid leaving file if possible)
            return try {
                file.delete()
            } catch (ex: Exception) {
                false
            }
        }
    }

    /**
     * Helper method to securely overwrite a file with zero bytes using RandomAccessFile in 'rws' mode.
     */
    fun securelyWipe(file: File) {
        try {
            if (file.exists() && file.isFile && file.canWrite()) {
                val length = file.length()
                if (length > 0) {
                    val zeros = ByteArray(8192)
                    var remaining = length
                    RandomAccessFile(file, "rws").use { raf ->
                        raf.seek(0)
                        while (remaining > 0) {
                            val writeSize = remaining.coerceAtMost(zeros.size.toLong()).toInt()
                            raf.write(zeros, 0, writeSize)
                            remaining -= writeSize
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Secure wipe failed for file: ${file.absolutePath}", e)
        }
    }
}
