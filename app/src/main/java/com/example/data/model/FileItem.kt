package com.example.data.model

data class FileItem(
    val id: String, // absolute path or unique identifier
    val name: String,
    val path: String,
    val size: Long,
    val isDirectory: Boolean,
    val mimeType: String,
    val lastModified: Long,
    val isSelected: Boolean = false,
    val isVaultItem: Boolean = false
)
