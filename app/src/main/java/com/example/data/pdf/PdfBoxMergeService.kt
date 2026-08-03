package com.example.data.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object PdfBoxMergeService {

    /**
     * Merges multiple PDF files specified by URIs into a single PDF output file
     * using PDFBox PDFMergerUtility with fallback to direct PDDocument appending.
     */
    suspend fun mergePdfFiles(
        context: Context,
        pdfUris: List<Uri>,
        outputName: String
    ): File = withContext(Dispatchers.IO) {
        require(pdfUris.isNotEmpty()) { "At least one PDF file must be provided for merging." }

        val sanitizeName = outputName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val finalFileName = if (sanitizeName.endsWith(".pdf", ignoreCase = true)) sanitizeName else "$sanitizeName.pdf"
        val outputDir = File(context.filesDir, "pdfbox_merged").apply { mkdirs() }
        val outputFile = File(outputDir, finalFileName)

        // Standard PDFMergerUtility approach
        try {
            val merger = PDFMergerUtility()
            merger.destinationFileName = outputFile.absolutePath

            val tempFilesToClean = mutableListOf<File>()

            for (uri in pdfUris) {
                val tempFile = PdfEngine.getLocalFileFromUri(context, uri)
                merger.addSource(tempFile)
                tempFilesToClean.add(tempFile)
            }

            merger.mergeDocuments(null)

            // Cleanup temp files if created in cache
            for (temp in tempFilesToClean) {
                if (temp.parentFile?.name == "pdf_temp_sources") {
                    temp.delete()
                }
            }

            if (outputFile.exists() && outputFile.length() > 0) {
                return@withContext outputFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback approach: Direct PDDocument appending
        val destinationDoc = PDDocument()
        try {
            for (uri in pdfUris) {
                val file = PdfEngine.getLocalFileFromUri(context, uri)
                FileInputStream(file).use { inputStream ->
                    PDDocument.load(inputStream).use { sourceDoc ->
                        for (i in 0 until sourceDoc.numberOfPages) {
                            destinationDoc.addPage(sourceDoc.getPage(i))
                        }
                    }
                }
                if (file.parentFile?.name == "pdf_temp_sources") {
                    file.delete()
                }
            }
            FileOutputStream(outputFile).use { outStream ->
                destinationDoc.save(outStream)
            }
        } finally {
            destinationDoc.close()
        }

        outputFile
    }
}
