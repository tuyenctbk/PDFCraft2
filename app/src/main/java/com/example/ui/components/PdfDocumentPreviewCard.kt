package com.example.ui.components

import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.pdf.PdfBoxPreviewService
import com.example.data.pdf.PdfEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Built-in PDF Document Preview Component.
 * Allows users to view, navigate, zoom, rotate, and inspect PDF documents directly within the app before performing operations.
 */
@Composable
fun PdfDocumentPreviewCard(
    file: File? = null,
    uri: Uri? = null,
    title: String = "Document Preview",
    modifier: Modifier = Modifier,
    initialPageIndex: Int = 0,
    showThumbnailsStrip: Boolean = true,
    showInfoBar: Boolean = true,
    onFullScreenRequested: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activeFile by remember(file, uri) {
        mutableStateOf<File?>(file)
    }

    var totalPages by remember { mutableIntStateOf(1) }
    var currentPageIndex by remember { mutableIntStateOf(initialPageIndex) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Navigation & View state
    var scaleFactor by remember { mutableFloatStateOf(1.0f) }
    var rotationDegrees by remember { mutableIntStateOf(0) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }

    var showDetailsDialog by remember { mutableStateOf(false) }
    var showThumbnailDrawer by remember { mutableStateOf(showThumbnailsStrip) }
    var thumbnailBitmaps by remember { mutableStateOf<Map<Int, Bitmap>>(emptyMap()) }

    var fullScreenDialogVisible by remember { mutableStateOf(false) }

    // Resolve file if uri is provided and file is null
    LaunchedEffect(file, uri) {
        if (file != null) {
            activeFile = file
        } else if (uri != null) {
            isLoading = true
            withContext(Dispatchers.IO) {
                try {
                    val resolved = PdfEngine.getLocalFileFromUri(context, uri)
                    activeFile = resolved
                } catch (e: Exception) {
                    errorMessage = "Failed to load document: ${e.localizedMessage}"
                }
            }
        }
    }

    // Load PDF total pages and current page bitmap when activeFile or currentPageIndex changes
    LaunchedEffect(activeFile, currentPageIndex) {
        val pdfFile = activeFile ?: return@LaunchedEffect
        isLoading = true
        errorMessage = null

        withContext(Dispatchers.IO) {
            try {
                val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = android.graphics.pdf.PdfRenderer(pfd)
                totalPages = renderer.pageCount.coerceAtLeast(1)

                if (currentPageIndex in 0 until totalPages) {
                    val page = renderer.openPage(currentPageIndex)
                    val density = context.resources.displayMetrics.density
                    val renderWidth = (360 * density * scaleFactor.coerceIn(1f, 2.5f)).toInt().coerceAtLeast(100)
                    val pw = page.width.coerceAtLeast(1)
                    val ph = page.height.coerceAtLeast(1)
                    val pageAspect = (ph.toFloat() / pw.toFloat()).coerceIn(0.5f, 3.0f)
                    val renderHeight = (renderWidth * pageAspect).toInt().coerceIn(100, 2400)

                    val bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)

                    page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    currentBitmap = bitmap
                }
                renderer.close()
                pfd.close()
            } catch (t: Throwable) {
                errorMessage = "Render error: ${t.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    // Pre-cache page thumbnails for horizontal drawer
    LaunchedEffect(activeFile, totalPages, showThumbnailDrawer) {
        val pdfFile = activeFile ?: return@LaunchedEffect
        if (!showThumbnailDrawer) return@LaunchedEffect

        withContext(Dispatchers.IO) {
            val map = mutableMapOf<Int, Bitmap>()
            val maxThumbnails = totalPages.coerceAtMost(15)
            for (i in 0 until maxThumbnails) {
                val bmp = PdfEngine.renderPageThumbnail(context, pdfFile, i, targetWidthDp = 70)
                if (bmp != null) {
                    map[i] = bmp
                }
            }
            withContext(Dispatchers.Main) {
                thumbnailBitmaps = map
            }
        }
    }

    if (activeFile == null) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Document Selected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Select a PDF file to enable live built-in content preview",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth().testTag("builtin_pdf_preview_card")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // --- HEADER BAR ---
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = activeFile?.name ?: title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Page ${currentPageIndex + 1} of $totalPages • ${PdfEngine.formatFileSize(activeFile?.length() ?: 0L)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Toggle Thumbnail strip
                        IconButton(
                            onClick = { showThumbnailDrawer = !showThumbnailDrawer },
                            modifier = Modifier.size(36.dp).testTag("toggle_thumbnails_button")
                        ) {
                            Icon(
                                imageVector = if (showThumbnailDrawer) Icons.Default.ViewCarousel else Icons.Default.GridOn,
                                contentDescription = "Page Thumbnails",
                                tint = if (showThumbnailDrawer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Rotate Preview
                        IconButton(
                            onClick = { rotationDegrees = (rotationDegrees + 90) % 360 },
                            modifier = Modifier.size(36.dp).testTag("rotate_preview_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Rotate Page",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Info Button
                        if (showInfoBar) {
                            IconButton(
                                onClick = { showDetailsDialog = true },
                                modifier = Modifier.size(36.dp).testTag("doc_info_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Document Info",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Full Screen Mode
                        IconButton(
                            onClick = {
                                if (onFullScreenRequested != null) {
                                    onFullScreenRequested()
                                } else {
                                    fullScreenDialogVisible = true
                                }
                            },
                            modifier = Modifier.size(36.dp).testTag("fullscreen_preview_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Full Screen Preview",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant)

            // --- CANVAS PAGE DISPLAY AREA ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .background(Color(0xFF1E1E1E))
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scaleFactor = (scaleFactor * zoom).coerceIn(0.8f, 3.5f)
                            panOffsetX += pan.x
                            panOffsetY += pan.y
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Rendering page content...",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                } else if (errorMessage != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = errorMessage ?: "Unable to render page", color = Color.White, fontSize = 13.sp)
                    }
                } else if (currentBitmap != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier
                            .graphicsLayer(
                                scaleX = scaleFactor,
                                scaleY = scaleFactor,
                                rotationZ = rotationDegrees.toFloat(),
                                translationX = panOffsetX,
                                translationY = panOffsetY
                            )
                            .padding(16.dp)
                    ) {
                        Image(
                            bitmap = currentBitmap!!.asImageBitmap(),
                            contentDescription = "PDF Page ${currentPageIndex + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(4.dp)
                        )
                    }
                }

                // Page badge on top right of canvas
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "${currentPageIndex + 1} / $totalPages",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                // Zoom controls floating on canvas
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        IconButton(
                            onClick = {
                                scaleFactor = (scaleFactor - 0.25f).coerceAtLeast(0.8f)
                            },
                            modifier = Modifier.size(32.dp).testTag("preview_zoom_out")
                        ) {
                            Icon(imageVector = Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = Color.White, modifier = Modifier.size(16.dp))
                        }

                        Text(
                            text = "${(scaleFactor * 100).toInt()}%",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                scaleFactor = 1.0f
                                panOffsetX = 0f
                                panOffsetY = 0f
                            }
                        )

                        IconButton(
                            onClick = {
                                scaleFactor = (scaleFactor + 0.25f).coerceAtMost(3.5f)
                            },
                            modifier = Modifier.size(32.dp).testTag("preview_zoom_in")
                        ) {
                            Icon(imageVector = Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = Color.White, modifier = Modifier.size(16.dp))
                        }

                        if (scaleFactor != 1.0f || panOffsetX != 0f || panOffsetY != 0f) {
                            IconButton(
                                onClick = {
                                    scaleFactor = 1.0f
                                    panOffsetX = 0f
                                    panOffsetY = 0f
                                },
                                modifier = Modifier.size(32.dp).testTag("preview_reset_zoom")
                            ) {
                                Icon(imageVector = Icons.Default.RestartAlt, contentDescription = "Reset Zoom", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // --- PAGE NAVIGATION CONTROLS ---
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { if (currentPageIndex > 0) currentPageIndex-- },
                        enabled = currentPageIndex > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("preview_prev_page_button")
                    ) {
                        Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Previous", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Text(
                        text = "Page ${currentPageIndex + 1} of $totalPages",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Button(
                        onClick = { if (currentPageIndex < totalPages - 1) currentPageIndex++ },
                        enabled = currentPageIndex < totalPages - 1,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("preview_next_page_button")
                    ) {
                        Text("Next", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // --- HORIZONTAL PAGE THUMBNAILS DRAWER ---
            AnimatedVisibility(visible = showThumbnailDrawer && totalPages > 1) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(
                                text = "Jump to Page:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(totalPages) { pageIdx ->
                                    val isSelected = pageIdx == currentPageIndex
                                    val bmp = thumbnailBitmaps[pageIdx]

                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                        ),
                                        border = BorderStroke(
                                            if (isSelected) 2.dp else 1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .width(58.dp)
                                            .height(72.dp)
                                            .clickable { currentPageIndex = pageIdx }
                                            .testTag("thumbnail_strip_item_$pageIdx")
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (bmp != null) {
                                                Image(
                                                    bitmap = bmp.asImageBitmap(),
                                                    contentDescription = "Thumbnail ${pageIdx + 1}",
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(2.dp)
                                                )
                                            } else {
                                                Text(
                                                    text = "${pageIdx + 1}",
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            // Small page label overlay
                                            Surface(
                                                color = Color.Black.copy(alpha = 0.6f),
                                                shape = RoundedCornerShape(4.dp),
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .padding(2.dp)
                                            ) {
                                                Text(
                                                    text = "${pageIdx + 1}",
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Full Screen Preview Dialog fallback if requested
    if (fullScreenDialogVisible && activeFile != null) {
        PdfBoxPreviewDialog(
            pdfUri = Uri.fromFile(activeFile!!),
            documentTitle = activeFile?.name ?: title,
            onDismissRequest = { fullScreenDialogVisible = false }
        )
    }

    // Document Info Popup Dialog
    if (showDetailsDialog && activeFile != null) {
        AlertDialog(
            onDismissRequest = { showDetailsDialog = false },
            icon = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Document Properties") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("File Name: ${activeFile?.name}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Total Pages: $totalPages", fontSize = 13.sp)
                    Text("File Size: ${PdfEngine.formatFileSize(activeFile?.length() ?: 0L)}", fontSize = 13.sp)
                    Text("File Path: ${activeFile?.absolutePath}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetailsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
