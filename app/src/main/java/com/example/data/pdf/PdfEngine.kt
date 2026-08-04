package com.example.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PdfPageSourceItem(
    val id: String,
    val sourceTitle: String,
    val sourceUri: Uri,
    val originalPageIndex: Int,
    var rotationDegrees: Int = 0
)

data class CompressionPreset(
    val title: String,
    val description: String,
    val targetDpi: Int,
    val jpegQuality: Int,
    val estimatedSavings: String
)

data class PdfMetadata(
    val fileName: String,
    val filePath: String,
    val fileSizeFormatted: String,
    val fileSizeBytes: Long,
    val title: String?,
    val author: String?,
    val subject: String?,
    val keywords: String?,
    val creator: String?,
    val producer: String?,
    val pageCount: Int,
    val creationDateFormatted: String?,
    val modDateFormatted: String?,
    val isEncrypted: Boolean
)

object PdfEngine {
    private const val TAG = "PdfEngine"
    private val thumbnailCache = LruCache<String, Bitmap>(35)

    val COMPRESSION_PRESETS = listOf(
        CompressionPreset(
            title = "Max Quality",
            description = "High resolution for printing (300 DPI)",
            targetDpi = 240,
            jpegQuality = 90,
            estimatedSavings = "~15-30%"
        ),
        CompressionPreset(
            title = "Balanced",
            description = "Ideal for sharing and email (150 DPI)",
            targetDpi = 140,
            jpegQuality = 70,
            estimatedSavings = "~50-65%"
        ),
        CompressionPreset(
            title = "Compact / Web",
            description = "Maximum size reduction (100 DPI)",
            targetDpi = 96,
            jpegQuality = 45,
            estimatedSavings = "~75-85%"
        )
    )

    /**
     * Copies a Uri content stream to a temporary local cache file.
     */
    suspend fun getLocalFileFromUri(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        if (uri.scheme == "file" && uri.path != null) {
            return@withContext File(uri.path!!)
        }
        val cacheDir = File(context.cacheDir, "imported_pdfs").apply { mkdirs() }
        val tempFile = File(cacheDir, "doc_${System.currentTimeMillis()}_${(1000..9999).random()}.pdf")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        tempFile
    }

    /**
     * Renders a specific page thumbnail from PDF File.
     */
    suspend fun renderPageThumbnail(
        context: Context,
        file: File,
        pageIndex: Int,
        targetWidthDp: Int = 220
    ): Bitmap? = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null

            page = renderer.openPage(pageIndex)
            val density = context.resources.displayMetrics.density
            val targetWidthPx = (targetWidthDp * density).toInt()
            val aspectRatio = page.height.toFloat() / page.width.toFloat()
            val targetHeightPx = (targetWidthPx * aspectRatio).toInt().coerceAtLeast(100)

            val bitmap = Bitmap.createBitmap(targetWidthPx, targetHeightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE) // White page background

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering page thumbnail", e)
            null
        } finally {
            page?.close()
            renderer?.close()
            pfd?.close()
        }
    }

    /**
     * Copies an Image Uri content stream to a temporary local cache file.
     */
    suspend fun getLocalImageFileFromUri(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        if (uri.scheme == "file" && uri.path != null) {
            return@withContext File(uri.path!!)
        }
        val cacheDir = File(context.cacheDir, "imported_images").apply { mkdirs() }
        val ext = when (context.contentResolver.getType(uri)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "jpg"
        }
        val tempFile = File(cacheDir, "img_${System.currentTimeMillis()}_${(1000..9999).random()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        tempFile
    }

    /**
     * Renders/downscales an image file into a thumbnail bitmap.
     */
    suspend fun renderImageThumbnail(
        context: Context,
        file: File,
        targetWidthDp: Int = 220
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
            
            val density = context.resources.displayMetrics.density
            val targetWidthPx = (targetWidthDp * density).toInt()
            
            // Calculate scale
            var scale = 1
            while (options.outWidth / scale / 2 >= targetWidthPx) {
                scale *= 2
            }
            
            val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = scale
            }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering image thumbnail", e)
            null
        }
    }

    /**
     * Merges a list of ordered PdfPageSourceItems into a unified PDF file.
     * Supports both PDF page sources and Image sources (originalPageIndex = -1).
     */
    suspend fun mergeAndReorderPages(
        context: Context,
        pageItems: List<PdfPageSourceItem>,
        outputFileName: String
    ): File = withContext(Dispatchers.IO) {
        val outputDir = File(context.filesDir, "exports").apply { mkdirs() }
        val outputFile = File(outputDir, if (outputFileName.endsWith(".pdf")) outputFileName else "$outputFileName.pdf")
        
        val pdfDocument = PdfDocument()

        // Cache file descriptors and renderers per source file to avoid reopening
        val rendererMap = mutableMapOf<Uri, Pair<ParcelFileDescriptor, PdfRenderer>>()

        try {
            pageItems.forEachIndexed { newIndex, item ->
                if (item.originalPageIndex == -1) {
                    // This item is an Image source!
                    try {
                        val file = File(item.sourceUri.path ?: "")
                        val bitmap = if (file.exists()) {
                            android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                        } else {
                            context.contentResolver.openInputStream(item.sourceUri)?.use { input ->
                                android.graphics.BitmapFactory.decodeStream(input)
                            }
                        }

                        if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                            // Apply rotation if requested
                            val finalBitmap = if (item.rotationDegrees % 360 != 0) {
                                val matrix = Matrix().apply { postRotate(item.rotationDegrees.toFloat()) }
                                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                            } else {
                                bitmap
                            }

                            if (finalBitmap.width > 0 && finalBitmap.height > 0) {
                                // Create PDF page with image dimensions
                                val pageInfo = PdfDocument.PageInfo.Builder(finalBitmap.width, finalBitmap.height, newIndex + 1).create()
                                val pdfPage = pdfDocument.startPage(pageInfo)
                                pdfPage.canvas.drawBitmap(finalBitmap, 0f, 0f, null)
                                pdfDocument.finishPage(pdfPage)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to write image item to PDF page", e)
                    }
                } else {
                    // This item is a PDF page source!
                    var pair = rendererMap[item.sourceUri]
                    if (pair == null) {
                        val localFile = getLocalFileFromUri(context, item.sourceUri)
                        val pfd = ParcelFileDescriptor.open(localFile, ParcelFileDescriptor.MODE_READ_ONLY)
                        val renderer = PdfRenderer(pfd)
                        pair = Pair(pfd, renderer)
                        rendererMap[item.sourceUri] = pair
                    }

                    val renderer = pair.second
                    if (item.originalPageIndex >= 0 && item.originalPageIndex < renderer.pageCount) {
                        val page = renderer.openPage(item.originalPageIndex)

                        val pageWidth = page.width
                        val pageHeight = page.height

                        // Render source page into bitmap
                        val bitmap = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()

                        // Apply rotation if requested
                        val finalBitmap = if (item.rotationDegrees % 360 != 0) {
                            val matrix = Matrix().apply { postRotate(item.rotationDegrees.toFloat()) }
                            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                        } else {
                            bitmap
                        }

                        // Create PdfDocument page
                        val pageInfo = PdfDocument.PageInfo.Builder(finalBitmap.width, finalBitmap.height, newIndex + 1).create()
                        val pdfPage = pdfDocument.startPage(pageInfo)
                        pdfPage.canvas.drawBitmap(finalBitmap, 0f, 0f, null)
                        pdfDocument.finishPage(pdfPage)
                    }
                }
            }

            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }
        } finally {
            pdfDocument.close()
            rendererMap.values.forEach { (pfd, renderer) ->
                try { renderer.close() } catch (ignored: Exception) {}
                try { pfd.close() } catch (ignored: Exception) {}
            }
        }

        outputFile
    }

    /**
     * Splits a PDF file by selecting specific page indices.
     */
    suspend fun splitPdf(
        context: Context,
        sourceUri: Uri,
        selectedPageIndices: List<Int>,
        outputFileName: String
    ): File = withContext(Dispatchers.IO) {
        val localFile = getLocalFileFromUri(context, sourceUri)
        val pageItems = selectedPageIndices.mapIndexed { index, pageIdx ->
            PdfPageSourceItem(
                id = "split_$index",
                sourceTitle = localFile.name,
                sourceUri = sourceUri,
                originalPageIndex = pageIdx,
                rotationDegrees = 0
            )
        }
        mergeAndReorderPages(context, pageItems, outputFileName)
    }

    /**
     * Splits a PDF file into individual page PDF files.
     * Returns a list of generated individual page files.
     */
    suspend fun splitPdfToIndividualPages(
        context: Context,
        sourceUri: Uri,
        selectedPageIndices: List<Int>,
        baseOutputName: String
    ): List<File> = withContext(Dispatchers.IO) {
        val localFile = getLocalFileFromUri(context, sourceUri)
        val generatedFiles = mutableListOf<File>()
        
        selectedPageIndices.forEach { pageIdx ->
            val pageItem = PdfPageSourceItem(
                id = "split_single_$pageIdx",
                sourceTitle = localFile.name,
                sourceUri = sourceUri,
                originalPageIndex = pageIdx,
                rotationDegrees = 0
            )
            val cleanBaseName = baseOutputName.removeSuffix(".pdf")
            val outputFileName = "${cleanBaseName}_Page_${pageIdx + 1}.pdf"
            val file = mergeAndReorderPages(context, listOf(pageItem), outputFileName)
            generatedFiles.add(file)
        }
        
        generatedFiles
    }

    /**
     * Compresses a PDF file by re-sampling pages at a target DPI & JPEG quality.
     */
    suspend fun compressPdf(
        context: Context,
        sourceUri: Uri,
        preset: CompressionPreset,
        outputFileName: String
    ): File = withContext(Dispatchers.IO) {
        val localFile = getLocalFileFromUri(context, sourceUri)
        val pfd = ParcelFileDescriptor.open(localFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val pdfDocument = PdfDocument()

        val outputDir = File(context.filesDir, "exports").apply { mkdirs() }
        val outputFile = File(outputDir, if (outputFileName.endsWith(".pdf")) outputFileName else "$outputFileName.pdf")

        try {
            val scaleFactor = preset.targetDpi.toFloat() / 200f
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val targetW = (page.width * scaleFactor).toInt().coerceAtLeast(100)
                val targetH = (page.height * scaleFactor).toInt().coerceAtLeast(100)

                val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                // Re-encode via compressed JPEG stream to strip vector bloated streams
                val compressedStream = java.io.ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, preset.jpegQuality, compressedStream)
                val compressedBytes = compressedStream.toByteArray()
                val compressedBitmap = android.graphics.BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)

                val pageInfo = PdfDocument.PageInfo.Builder(page.width, page.height, i + 1).create()
                val pdfPage = pdfDocument.startPage(pageInfo)
                val destRect = Rect(0, 0, page.width, page.height)
                val bitmapToDraw = compressedBitmap ?: bitmap
                pdfPage.canvas.drawBitmap(bitmapToDraw, null, destRect, null)
                pdfDocument.finishPage(pdfPage)
            }

            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }
        } finally {
            pdfDocument.close()
            renderer.close()
            pfd.close()
        }

        outputFile
    }

    /**
     * Extracts images from specified pages of a PDF and saves them as PNG/JPG files.
     */
    suspend fun extractImages(
        context: Context,
        sourceUri: Uri,
        selectedPageIndices: List<Int>,
        format: String = "PNG" // PNG or JPEG
    ): List<File> = withContext(Dispatchers.IO) {
        val localFile = getLocalFileFromUri(context, sourceUri)
        val pfd = ParcelFileDescriptor.open(localFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)

        val outputDir = File(context.filesDir, "extracted_images").apply { mkdirs() }
        val extractedFiles = mutableListOf<File>()

        val ext = if (format.equals("JPEG", ignoreCase = true)) "jpg" else "png"
        val compressFormat = if (format.equals("JPEG", ignoreCase = true)) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG

        try {
            selectedPageIndices.forEach { pageIdx ->
                if (pageIdx in 0 until renderer.pageCount) {
                    val page = renderer.openPage(pageIdx)
                    // High resolution render for extraction (300 DPI quality: 2x scale)
                    val width = page.width * 2
                    val height = page.height * 2
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val imageFile = File(outputDir, "${localFile.nameWithoutExtension}_page_${pageIdx + 1}.$ext")
                    FileOutputStream(imageFile).use { out ->
                        bitmap.compress(compressFormat, 95, out)
                    }
                    extractedFiles.add(imageFile)
                }
            }
        } finally {
            renderer.close()
            pfd.close()
        }

        extractedFiles
    }

    /**
     * Generates a set of realistic local sample PDFs for instant testing.
     */
    suspend fun generateSamplePdfs(context: Context): List<File> = withContext(Dispatchers.IO) {
        val samplesDir = File(context.filesDir, "sample_pdfs").apply { mkdirs() }
        val samples = mutableListOf<File>()

        // 1. Financial Invoice Sample
        val invoiceFile = File(samplesDir, "Sample_Financial_Invoice_2026.pdf")
        if (!invoiceFile.exists()) {
            val doc = PdfDocument()
            
            // Page 1: Invoice Header & Summary
            val pageInfo1 = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 standard
            val page1 = doc.startPage(pageInfo1)
            val c1 = page1.canvas
            
            val paintTitle = Paint().apply { color = Color.parseColor("#0F172A"); textSize = 24f; isFakeBoldText = true }
            val paintSub = Paint().apply { color = Color.parseColor("#059669"); textSize = 14f; isFakeBoldText = true }
            val paintBody = Paint().apply { color = Color.parseColor("#334155"); textSize = 12f }
            val paintHeaderBg = Paint().apply { color = Color.parseColor("#F1F5F9") }
            val paintBorder = Paint().apply { color = Color.parseColor("#CBD5E1"); style = Paint.Style.STROKE; strokeWidth = 1f }

            c1.drawRect(0f, 0f, 595f, 100f, Paint().apply { color = Color.parseColor("#064E3B") })
            val paintWhite = Paint().apply { color = Color.WHITE; textSize = 22f; isFakeBoldText = true }
            c1.drawText("PDFCraft Financial Studio", 40f, 50f, paintWhite)
            c1.drawText("INVOICE #INV-2026-88901", 40f, 80f, Paint().apply { color = Color.parseColor("#A7F3D0"); textSize = 12f })

            c1.drawText("Billed To: ACME Enterprise Corp", 40f, 140f, paintSub)
            c1.drawText("Date: July 29, 2026", 40f, 160f, paintBody)
            c1.drawText("Status: Paid (On-Device Local)", 40f, 180f, paintBody)

            c1.drawRect(40f, 210f, 555f, 240f, paintHeaderBg)
            c1.drawText("Item Description", 50f, 230f, paintSub)
            c1.drawText("Qty", 380f, 230f, paintSub)
            c1.drawText("Amount", 480f, 230f, paintSub)

            c1.drawText("On-Device PDF Merge & Compression Suite", 50f, 270f, paintBody)
            c1.drawText("1", 385f, 270f, paintBody)
            c1.drawText("$450.00", 480f, 270f, paintBody)

            c1.drawText("Local Image Asset Extractor & Splitter", 50f, 300f, paintBody)
            c1.drawText("2", 385f, 300f, paintBody)
            c1.drawText("$280.00", 480f, 300f, paintBody)

            c1.drawLine(40f, 330f, 555f, 330f, paintBorder)
            c1.drawText("Total Amount Due: $730.00", 350f, 360f, paintTitle)

            doc.finishPage(page1)

            // Page 2: Terms & Verification Signature
            val pageInfo2 = PdfDocument.PageInfo.Builder(595, 842, 2).create()
            val page2 = doc.startPage(pageInfo2)
            val c2 = page2.canvas
            c2.drawText("Page 2: Terms & Confidential Verification", 40f, 60f, paintTitle)
            c2.drawText("All operations performed locally on device with zero cloud network risk.", 40f, 100f, paintBody)
            c2.drawText("Authorized Signature:", 40f, 200f, paintSub)
            c2.drawLine(40f, 260f, 240f, 260f, paintBorder)
            c2.drawText("Chief Document Auditor", 40f, 280f, paintBody)

            doc.finishPage(page2)

            FileOutputStream(invoiceFile).use { doc.writeTo(it) }
            doc.close()
        }
        samples.add(invoiceFile)

        // 2. Lecture Slides Sample
        val lectureFile = File(samplesDir, "Sample_AI_Lecture_Slides.pdf")
        if (!lectureFile.exists()) {
            val doc = PdfDocument()

            // Page 1
            val p1 = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val c1 = p1.canvas
            c1.drawColor(Color.parseColor("#0F172A"))
            val pText = Paint().apply { color = Color.WHITE; textSize = 26f; isFakeBoldText = true }
            val pSub = Paint().apply { color = Color.parseColor("#38BDF8"); textSize = 16f }
            c1.drawText("Lecture 04: On-Device Neural Engines", 40f, 100f, pText)
            c1.drawText("High-performance zero-latency local execution", 40f, 140f, pSub)
            c1.drawRect(40f, 200f, 555f, 500f, Paint().apply { color = Color.parseColor("#1E293B") })
            c1.drawText("Diagram: Local PDF Pipeline Flow", 60f, 240f, pSub)
            doc.finishPage(p1)

            // Page 2
            val p2 = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 2).create())
            val c2 = p2.canvas
            c2.drawColor(Color.parseColor("#0F172A"))
            c2.drawText("Key Takeaways & Summary", 40f, 100f, pText)
            c2.drawText("1. Always process sensitive PDFs offline", 40f, 180f, pSub)
            c2.drawText("2. Reorder, rotate, split and compress in milliseconds", 40f, 220f, pSub)
            doc.finishPage(p2)

            // Page 3
            val p3 = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 3).create())
            val c3 = p3.canvas
            c3.drawColor(Color.parseColor("#0F172A"))
            c3.drawText("Q&A & Reference Materials", 40f, 100f, pText)
            c3.drawText("PDFCraft local C++ PDFium Rendering", 40f, 180f, pSub)
            doc.finishPage(p3)

            FileOutputStream(lectureFile).use { doc.writeTo(it) }
            doc.close()
        }
        samples.add(lectureFile)

        samples
    }

    /**
     * Helper to format bytes to human readable string.
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format(Locale.US, "%.2f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    /**
     * Extracts document metadata (Title, Author, Subject, Keywords, Creator, Producer, Page Count, Dates)
     * using PDFBox with fallback to android.graphics.pdf.PdfRenderer.
     */
    suspend fun extractMetadata(file: File): PdfMetadata = withContext(Dispatchers.IO) {
        if (!file.exists()) {
            return@withContext PdfMetadata(
                fileName = file.name,
                filePath = file.absolutePath,
                fileSizeFormatted = "0 B",
                fileSizeBytes = 0L,
                title = null,
                author = null,
                subject = null,
                keywords = null,
                creator = null,
                producer = null,
                pageCount = 0,
                creationDateFormatted = null,
                modDateFormatted = null,
                isEncrypted = false
            )
        }

        val fileSize = file.length()
        val formattedSize = formatFileSize(fileSize)
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

        var docTitle: String? = null
        var docAuthor: String? = null
        var docSubject: String? = null
        var docKeywords: String? = null
        var docCreator: String? = null
        var docProducer: String? = null
        var pageCount = 0
        var creationDateStr: String? = null
        var modDateStr: String? = null
        var encrypted = false

        try {
            com.tom_roush.pdfbox.pdmodel.PDDocument.load(file).use { doc ->
                encrypted = doc.isEncrypted
                pageCount = doc.numberOfPages
                val info = doc.documentInformation
                if (info != null) {
                    docTitle = info.title?.takeIf { it.isNotBlank() }
                    docAuthor = info.author?.takeIf { it.isNotBlank() }
                    docSubject = info.subject?.takeIf { it.isNotBlank() }
                    docKeywords = info.keywords?.takeIf { it.isNotBlank() }
                    docCreator = info.creator?.takeIf { it.isNotBlank() }
                    docProducer = info.producer?.takeIf { it.isNotBlank() }

                    info.creationDate?.let { cal ->
                        creationDateStr = dateFormat.format(cal.time)
                    }
                    info.modificationDate?.let { cal ->
                        modDateStr = dateFormat.format(cal.time)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting PDFBox metadata: ${e.localizedMessage}")
            try {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                pageCount = renderer.pageCount
                renderer.close()
                pfd.close()
            } catch (t: Throwable) {
                Log.e(TAG, "Fallback PdfRenderer error: ${t.localizedMessage}")
            }
        }

        if (modDateStr == null && file.lastModified() > 0) {
            modDateStr = dateFormat.format(Date(file.lastModified()))
        }

        PdfMetadata(
            fileName = file.name,
            filePath = file.absolutePath,
            fileSizeFormatted = formattedSize,
            fileSizeBytes = fileSize,
            title = docTitle,
            author = docAuthor,
            subject = docSubject,
            keywords = docKeywords,
            creator = docCreator,
            producer = docProducer,
            pageCount = pageCount,
            creationDateFormatted = creationDateStr,
            modDateFormatted = modDateStr,
            isEncrypted = encrypted
        )
    }

    /**
     * Renders page 0 of a PDF file into a crisp thumbnail Bitmap with memory caching.
     */
    suspend fun generateFirstPageThumbnail(file: File, widthPx: Int = 220): Bitmap? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null

        val cacheKey = "${file.absolutePath}_${file.lastModified()}_$widthPx"
        thumbnailCache.get(cacheKey)?.let { cached ->
            if (!cached.isRecycled) return@withContext cached
        }

        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            if (renderer.pageCount > 0) {
                val page = renderer.openPage(0)
                val pw = if (page.width > 0) page.width else 100
                val ph = if (page.height > 0) page.height else 100
                val aspectRatio = (ph.toFloat() / pw.toFloat()).takeIf { !it.isNaN() && !it.isInfinite() } ?: 1.414f
                val targetHeight = (widthPx * aspectRatio).toInt().coerceIn(80, 500)

                val bitmap = Bitmap.createBitmap(widthPx, targetHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                thumbnailCache.put(cacheKey, bitmap)
                return@withContext bitmap
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error generating page 1 thumbnail for ${file.name}: ${t.localizedMessage}")
        } finally {
            try { renderer?.close() } catch (_: Throwable) {}
            try { pfd?.close() } catch (_: Throwable) {}
        }
        return@withContext null
    }
}
