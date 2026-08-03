package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class PdfOperationType {
    MERGE,
    SPLIT,
    COMPRESS,
    EXTRACT
}

@Entity(tableName = "pdf_projects")
data class PdfProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val operationType: PdfOperationType,
    val pageCount: Int,
    val originalSizeBytes: Long,
    val resultSizeBytes: Long,
    val outputFilePath: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val summaryDetails: String = ""
)
