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
