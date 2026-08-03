package com.example.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class PdfBoxDocumentMetadata(
    val title: String,
    val totalPages: Int,
    val fileSizeFormatted: String,
    val fileSizeBytes: Long,
    val isEncrypted: Boolean,
    val version: Float
)

object PdfBoxPreviewService {

    /**
     * Renders a specific page using PDFBox PDFRenderer into a high-quality Bitmap.
     */
    suspend fun renderPageBitmapWithPdfBox(
        context: Context,
        pdfUri: Uri,
        pageIndex: Int,
        scale: Float = 1.5f
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val file = PdfEngine.getLocalFileFromUri(context, pdfUri)
            PDDocument.load(file).use { pdDocument ->
                if (pageIndex in 0 until pdDocument.numberOfPages) {
                    val pdfRenderer = PDFRenderer(pdDocument)
                    return@withContext pdfRenderer.renderImage(pageIndex, scale)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    /**
     * Extract metadata of a PDF document using Apache PDFBox.
     */
    suspend fun getDocumentMetadataWithPdfBox(
        context: Context,
        pdfUri: Uri
    ): PdfBoxDocumentMetadata? = withContext(Dispatchers.IO) {
        try {
            val file = PdfEngine.getLocalFileFromUri(context, pdfUri)
            PDDocument.load(file).use { pdDocument ->
                val info = pdDocument.documentInformation
                return@withContext PdfBoxDocumentMetadata(
                    title = info?.title?.takeIf { it.isNotBlank() } ?: file.name,
                    totalPages = pdDocument.numberOfPages,
                    fileSizeFormatted = PdfEngine.formatFileSize(file.length()),
                    fileSizeBytes = file.length(),
                    isEncrypted = pdDocument.isEncrypted,
                    version = pdDocument.version
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
}
