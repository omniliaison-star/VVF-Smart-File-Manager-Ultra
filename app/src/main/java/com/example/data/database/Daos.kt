package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import androidx.paging.PagingSource

@Dao
interface FileDao {
    @Query("SELECT * FROM files ORDER BY name ASC")
    fun getAllFiles(): Flow<List<DbFile>>

    @Query("SELECT * FROM files ORDER BY name ASC")
    fun getAllFilesPaged(): PagingSource<Int, DbFile>

    @Query("SELECT * FROM files ORDER BY name ASC")
    suspend fun getAllFilesList(): List<DbFile>

    @Query("SELECT * FROM files WHERE id = :id")
    suspend fun getFileById(id: Long): DbFile?

    @Query("SELECT * FROM files WHERE path = :path")
    suspend fun getFileByPath(path: String): DbFile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: DbFile): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<DbFile>)

    @Update
    suspend fun updateFile(file: DbFile)

    @Delete
    suspend fun deleteFile(file: DbFile)

    @Query("DELETE FROM files WHERE path = :path")
    suspend fun deleteFileByPath(path: String)

    @Query("DELETE FROM files")
    suspend fun clearAllFiles()

    @Query("SELECT * FROM files WHERE name LIKE :query OR path LIKE :query")
    suspend fun searchFilesLike(query: String): List<DbFile>
}

@Dao
interface EmbeddingDao {
    @Query("SELECT * FROM embeddings")
    suspend fun getAllEmbeddings(): List<DbEmbedding>

    @Query("SELECT * FROM embeddings WHERE fileId = :fileId")
    suspend fun getEmbeddingForFile(fileId: Long): DbEmbedding?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmbedding(embedding: DbEmbedding)

    @Query("DELETE FROM embeddings WHERE fileId = :fileId")
    suspend fun deleteEmbeddingForFile(fileId: Long)

    @Query("DELETE FROM embeddings")
    suspend fun clearAllEmbeddings()
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 50")
    fun getSearchHistory(): Flow<List<DbSearchHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: DbSearchHistory)

    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteHistory(id: Long)

    @Query("DELETE FROM search_history")
    suspend fun clearHistory()
}

@Dao
interface FileEntityDao {
    @Query("SELECT * FROM file_entities ORDER BY name ASC")
    fun getAllFileEntities(): Flow<List<FileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFileEntity(entity: FileEntity)

    @Query("DELETE FROM file_entities WHERE path = :path")
    suspend fun deleteFileEntityByPath(path: String)
}

@Dao
interface CategoryEntityDao {
    @Query("SELECT * FROM category_entities ORDER BY name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(entity: CategoryEntity)

    @Query("DELETE FROM category_entities")
    suspend fun clearAll()
}

@Dao
interface SecureStateEntityDao {
    @Query("SELECT * FROM secure_state_entities WHERE `key` = :key")
    suspend fun getValue(key: String): SecureStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertValue(entity: SecureStateEntity)
}

@Dao
interface ChatMessageEntityDao {
    @Query("SELECT * FROM chat_message_entities ORDER BY timestamp ASC")
    fun getAllChatMessages(): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(entity: ChatMessageEntity)

    @Query("DELETE FROM chat_message_entities")
    suspend fun clearChatMessages()
}

