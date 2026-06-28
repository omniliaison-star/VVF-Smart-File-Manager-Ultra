package com.example.util

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.data.model.FileItem
import com.example.data.repository.FileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

class FilePagingSource(
    private val fileRepository: FileRepository,
    private val dirPath: String
) : PagingSource<Int, FileItem>() {

    override fun getRefreshKey(state: PagingState<Int, FileItem>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, FileItem> = withContext(Dispatchers.IO) {
        try {
            val pageNumber = params.key ?: 0
            val pageSize = params.loadSize
            val start = pageNumber * pageSize

            val targetDir = if (dirPath.isEmpty()) fileRepository.sandboxRoot else File(dirPath)
            if (!targetDir.exists() || !targetDir.isDirectory) {
                return@withContext LoadResult.Page(
                    data = emptyList(),
                    prevKey = if (pageNumber > 0) pageNumber - 1 else null,
                    nextKey = null
                )
            }

            val path = Paths.get(targetDir.absolutePath)
            val list = mutableListOf<FileItem>()
            var count = 0
            var hasMore = false

            Files.newDirectoryStream(path).use { stream ->
                val iterator = stream.iterator()
                // Skip to start
                while (iterator.hasNext() && count < start) {
                    iterator.next()
                    count++
                }
                // Take pageSize elements
                while (iterator.hasNext() && list.size < pageSize) {
                    val entry = iterator.next().toFile()
                    list.add(
                        FileItem(
                            id = entry.absolutePath,
                            name = entry.name,
                            path = entry.absolutePath,
                            size = if (entry.isDirectory) 0L else entry.length(),
                            isDirectory = entry.isDirectory,
                            mimeType = fileRepository.getMimeType(entry),
                            lastModified = entry.lastModified()
                        )
                    )
                }
                hasMore = iterator.hasNext()
            }

            // Present folders first, then files
            val sortedData = list.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

            LoadResult.Page(
                data = sortedData,
                prevKey = if (pageNumber > 0) pageNumber - 1 else null,
                nextKey = if (hasMore) pageNumber + 1 else null
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
