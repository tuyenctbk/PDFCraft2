package com.example.data.local

import android.content.Context
import android.net.Uri
import com.example.data.pdf.CompressionPreset
import com.example.data.pdf.PdfEngine
import com.example.data.pdf.PdfPageSourceItem
import kotlinx.coroutines.flow.Flow
import java.io.File

class PdfRepository(
    private val dao: PdfProjectDao,
    private val recentDao: RecentFileDao
) {

    val allProjects: Flow<List<PdfProjectEntity>> = dao.getAllProjects()
    val favoriteProjects: Flow<List<PdfProjectEntity>> = dao.getFavoriteProjects()
    val recentFiles: Flow<List<RecentFileEntity>> = recentDao.getRecentFiles()

    suspend fun recordRecentFileAccess(
        filePath: String,
        fileName: String,
        fileSizeFormatted: String = "",
        pageCount: Int = 0
    ) {
        recentDao.insertOrUpdate(
            RecentFileEntity(
                filePath = filePath,
                fileName = fileName,
                fileSizeFormatted = fileSizeFormatted,
                pageCount = pageCount,
                lastAccessedTimestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun removeRecentFile(filePath: String) {
        recentDao.deleteByPath(filePath)
    }

    suspend fun clearRecentFiles() {
        recentDao.clearAll()
    }

    fun getProjectsByType(type: PdfOperationType): Flow<List<PdfProjectEntity>> =
        dao.getProjectsByType(type)

    suspend fun saveProjectRecord(project: PdfProjectEntity): Long {
        val id = dao.insertProject(project)
        if (project.outputFilePath.isNotBlank()) {
            recordRecentFileAccess(
                filePath = project.outputFilePath,
                fileName = project.title,
                fileSizeFormatted = PdfEngine.formatFileSize(project.resultSizeBytes),
                pageCount = project.pageCount
            )
        }
        return id
    }

    suspend fun toggleFavorite(id: Long, isFavorite: Boolean) =
        dao.setFavorite(id, isFavorite)

    suspend fun deleteProject(id: Long) =
        dao.deleteProjectById(id)

    suspend fun clearHistory() =
        dao.clearAll()

    // Engine Operations Wrappers
    suspend fun generateSamples(context: Context): List<File> =
        PdfEngine.generateSamplePdfs(context)

    suspend fun renderThumbnail(context: Context, file: File, pageIndex: Int, targetWidthDp: Int = 220) =
        PdfEngine.renderPageThumbnail(context, file, pageIndex, targetWidthDp)

    suspend fun mergePages(
        context: Context,
        pageItems: List<PdfPageSourceItem>,
        outputName: String,
        originalSizeBytes: Long
    ): File {
        val outputFile = PdfEngine.mergeAndReorderPages(context, pageItems, outputName)
        saveProjectRecord(
            PdfProjectEntity(
                title = outputFile.name,
                operationType = PdfOperationType.MERGE,
                pageCount = pageItems.size,
                originalSizeBytes = originalSizeBytes,
                resultSizeBytes = outputFile.length(),
                outputFilePath = outputFile.absolutePath,
                summaryDetails = "Merged ${pageItems.size} pages into ${outputFile.name}"
            )
        )
        return outputFile
    }

    suspend fun splitPdf(
        context: Context,
        sourceUri: Uri,
        selectedIndices: List<Int>,
        outputName: String,
        originalSizeBytes: Long
    ): File {
        val outputFile = PdfEngine.splitPdf(context, sourceUri, selectedIndices, outputName)
        saveProjectRecord(
            PdfProjectEntity(
                title = outputFile.name,
                operationType = PdfOperationType.SPLIT,
                pageCount = selectedIndices.size,
                originalSizeBytes = originalSizeBytes,
                resultSizeBytes = outputFile.length(),
                outputFilePath = outputFile.absolutePath,
                summaryDetails = "Extracted ${selectedIndices.size} pages into ${outputFile.name}"
            )
        )
        return outputFile
    }

    suspend fun splitPdfToSeparate(
        context: Context,
        sourceUri: Uri,
        selectedIndices: List<Int>,
        baseOutputName: String,
        originalSizeBytes: Long
    ): List<File> {
        val outputFiles = PdfEngine.splitPdfToIndividualPages(context, sourceUri, selectedIndices, baseOutputName)
        outputFiles.forEach { outputFile ->
            saveProjectRecord(
                PdfProjectEntity(
                    title = outputFile.name,
                    operationType = PdfOperationType.SPLIT,
                    pageCount = 1,
                    originalSizeBytes = originalSizeBytes,
                    resultSizeBytes = outputFile.length(),
                    outputFilePath = outputFile.absolutePath,
                    summaryDetails = "Extracted page from ${baseOutputName}"
                )
            )
        }
        return outputFiles
    }

    suspend fun compressPdf(
        context: Context,
        sourceUri: Uri,
        preset: CompressionPreset,
        outputName: String,
        originalSizeBytes: Long
    ): File {
        val outputFile = PdfEngine.compressPdf(context, sourceUri, preset, outputName)
        val savedBytes = originalSizeBytes - outputFile.length()
        val percentSaved = if (originalSizeBytes > 0) ((savedBytes.toDouble() / originalSizeBytes) * 100).toInt() else 0
        saveProjectRecord(
            PdfProjectEntity(
                title = outputFile.name,
                operationType = PdfOperationType.COMPRESS,
                pageCount = 1,
                originalSizeBytes = originalSizeBytes,
                resultSizeBytes = outputFile.length(),
                outputFilePath = outputFile.absolutePath,
                summaryDetails = "Compressed with ${preset.title} ($percentSaved% size reduction)"
            )
        )
        return outputFile
    }

    suspend fun extractImages(
        context: Context,
        sourceUri: Uri,
        selectedIndices: List<Int>,
        format: String,
        originalSizeBytes: Long
    ): List<File> {
        val images = PdfEngine.extractImages(context, sourceUri, selectedIndices, format)
        val totalImageSize = images.sumOf { it.length() }
        if (images.isNotEmpty()) {
            saveProjectRecord(
                PdfProjectEntity(
                    title = "Extracted_${images.size}_Images",
                    operationType = PdfOperationType.EXTRACT,
                    pageCount = images.size,
                    originalSizeBytes = originalSizeBytes,
                    resultSizeBytes = totalImageSize,
                    outputFilePath = images.first().parentFile?.absolutePath ?: "",
                    summaryDetails = "Saved ${images.size} high-res $format images"
                )
            )
        }
        return images
    }

    suspend fun mergePdfFilesWithPdfBox(
        context: Context,
        pdfUris: List<Uri>,
        outputName: String,
        originalSizeBytes: Long
    ): File {
        val outputFile = com.example.data.pdf.PdfBoxMergeService.mergePdfFiles(context, pdfUris, outputName)
        saveProjectRecord(
            PdfProjectEntity(
                title = outputFile.name,
                operationType = PdfOperationType.MERGE,
                pageCount = pdfUris.size,
                originalSizeBytes = originalSizeBytes,
                resultSizeBytes = outputFile.length(),
                outputFilePath = outputFile.absolutePath,
                summaryDetails = "Merged ${pdfUris.size} files using PDFBox Engine into ${outputFile.name}"
            )
        )
        return outputFile
    }
}
