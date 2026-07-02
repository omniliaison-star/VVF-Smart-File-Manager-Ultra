package com.example.data.database

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Entity(tableName = "files")
data class DbFile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val path: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val createdAt: Long,
    val modifiedAt: Long
)

@Entity(tableName = "embeddings")
data class DbEmbedding(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileId: Long,
    val vectorData: String // Serialized float array (comma-separated or JSON)
)

@Entity(tableName = "search_history")
data class DbSearchHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Fts4(contentEntity = DbFile::class)
@Entity(tableName = "files_fts")
data class DbFileFts(
    val name: String,
    val path: String
)

@Entity(tableName = "file_entities")
data class FileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val path: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val createdAt: Long,
    val modifiedAt: Long
)

@Entity(tableName = "category_entities")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val fileCount: Int,
    val totalSize: Long
)

@Entity(tableName = "secure_state_entities")
data class SecureStateEntity(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "chat_message_entities")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String, // "user" or "model"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "drive_files")
data class DbDriveFile(
    @PrimaryKey val id: String,
    val name: String,
    val mimeType: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val path: String
)

