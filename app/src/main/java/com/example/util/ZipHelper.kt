package com.example.util

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipHelper {

    /**
     * Prevents ZIP Slip attacks by verifying if the entry path stays within the target directory.
     */
    private fun isSafePath(destinationDir: File, fileToExtract: File): Boolean {
        val canonicalDestinationPath = destinationDir.canonicalPath
        val canonicalFileToExtractPath = fileToExtract.canonicalPath
        return canonicalFileToExtractPath.startsWith(canonicalDestinationPath + File.separator) || 
               canonicalFileToExtractPath == canonicalDestinationPath
    }

    /**
     * Compresses a file or directory into a ZIP archive.
     */
    fun compressToZip(sourceFile: File, zipFile: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            zipFileOrDirectory(sourceFile, sourceFile.name, zos)
        }
    }

    private fun zipFileOrDirectory(file: File, entryPath: String, zos: ZipOutputStream) {
        if (file.isDirectory) {
            val children = file.listFiles() ?: return
            for (child in children) {
                zipFileOrDirectory(child, entryPath + File.separator + child.name, zos)
            }
        } else {
            val buffer = ByteArray(4096)
            FileInputStream(file).use { fis ->
                val entry = ZipEntry(entryPath)
                zos.putNextEntry(entry)
                var length: Int
                while (fis.read(buffer).also { length = it } > 0) {
                    zos.write(buffer, 0, length)
                }
                zos.closeEntry()
            }
        }
    }

    /**
     * Extracts a ZIP file into a target directory.
     * Implements strict Zip Slip protection.
     */
    fun extractZip(zipFile: File, destDir: File) {
        if (!destDir.exists()) {
            destDir.mkdirs()
        }
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            val buffer = ByteArray(4096)
            while (entry != null) {
                val newFile = File(destDir, entry.name)
                
                // ZIP SLIP PROTECTION
                if (!isSafePath(destDir, newFile)) {
                    throw SecurityException("Security Exception: Path Traversal detected in ZIP entry ${entry.name}")
                }

                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    val parent = newFile.parentFile
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs()
                    }
                    FileOutputStream(newFile).use { fos ->
                        var len: Int
                        while (zis.read(buffer).also { len = it } > 0) {
                            fos.write(buffer, 0, len)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    /**
     * Preview ZIP entries.
     */
    fun previewZip(zipFile: File): List<String> {
        val entriesList = mutableListOf<String>()
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                entriesList.add(entry.name)
                entry = zis.nextEntry
            }
        }
        return entriesList
    }

    /**
     * Compresses a file using GZIP.
     */
    fun compressGzip(sourceFile: File, destFile: File) {
        FileInputStream(sourceFile).use { fis ->
            GZIPOutputStream(FileOutputStream(destFile)).use { gzos ->
                val buffer = ByteArray(4096)
                var len: Int
                while (fis.read(buffer).also { len = it } > 0) {
                    gzos.write(buffer, 0, len)
                }
            }
        }
    }

    /**
     * Extracts a GZIP file.
     */
    fun extractGzip(gzipFile: File, destFile: File) {
        GZIPInputStream(FileInputStream(gzipFile)).use { gzis ->
            FileOutputStream(destFile).use { fos ->
                val buffer = ByteArray(4096)
                var len: Int
                while (gzis.read(buffer).also { len = it } > 0) {
                    fos.write(buffer, 0, len)
                }
            }
        }
    }
}
