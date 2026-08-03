package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_files")
data class RecentFileEntity(
    @PrimaryKey
    val filePath: String,
    val fileName: String,
    val fileSizeFormatted: String = "",
    val pageCount: Int = 0,
    val lastAccessedTimestamp: Long = System.currentTimeMillis()
)
