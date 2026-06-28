package com.example.util

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.data.model.FileItem

class SimpleListPagingSource(
    private val allFiles: List<FileItem>
) : PagingSource<Int, FileItem>() {

    override fun getRefreshKey(state: PagingState<Int, FileItem>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, FileItem> {
        val pageNumber = params.key ?: 0
        val pageSize = params.loadSize
        
        val start = pageNumber * pageSize
        val end = (start + pageSize).coerceAtMost(allFiles.size)
        
        return if (start > allFiles.size) {
            LoadResult.Page(
                data = emptyList(),
                prevKey = if (pageNumber > 0) pageNumber - 1 else null,
                nextKey = null
            )
        } else {
            val data = allFiles.subList(start, end)
            LoadResult.Page(
                data = data,
                prevKey = if (pageNumber > 0) pageNumber - 1 else null,
                nextKey = if (end < allFiles.size) pageNumber + 1 else null
            )
        }
    }
}
