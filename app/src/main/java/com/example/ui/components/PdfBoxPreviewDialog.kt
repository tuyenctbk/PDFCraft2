package com.example.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.pdf.PdfBoxDocumentMetadata
import com.example.data.pdf.PdfBoxPreviewService
import com.example.ui.theme.CharcoalBackground
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalCardBorder
import com.example.ui.theme.CharcoalHighlight
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.RedDelete
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

@Composable
fun PdfBoxPreviewDialog(
    pdfUri: Uri,
    documentTitle: String = "PDF Preview",
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var currentPageIndex by remember { mutableIntStateOf(0) }
    var totalPages by remember { mutableIntStateOf(1) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingPage by remember { mutableStateOf(true) }
    var metadata by remember { mutableStateOf<PdfBoxDocumentMetadata?>(null) }
    var showMetadataDetails by remember { mutableStateOf(false) }

    var rotationDegrees by remember { mutableIntStateOf(0) }
    var scaleFactor by remember { mutableFloatStateOf(1.2f) }

    LaunchedEffect(pdfUri) {
        isLoadingPage = true
        val meta = PdfBoxPreviewService.getDocumentMetadataWithPdfBox(context, pdfUri)
        metadata = meta
        if (meta != null && meta.totalPages > 0) {
            totalPages = meta.totalPages
        }
        val bmp = PdfBoxPreviewService.renderPageBitmapWithPdfBox(context, pdfUri, currentPageIndex, scaleFactor)
        currentBitmap = bmp
        isLoadingPage = false
    }

    LaunchedEffect(currentPageIndex, scaleFactor) {
        isLoadingPage = true
        val bmp = PdfBoxPreviewService.renderPageBitmapWithPdfBox(context, pdfUri, currentPageIndex, scaleFactor)
        currentBitmap = bmp
        isLoadingPage = false
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = CharcoalBackground,
            shape = RoundedCornerShape(16.dp),
            modifier = modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .border(1.dp, CharcoalCardBorder, RoundedCornerShape(16.dp))
                .testTag("pdfbox_preview_dialog")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    color = CharcoalCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = EmeraldPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {
                                Text(
                                    text = metadata?.title ?: documentTitle,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    ),
                                    maxLines = 1
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Apache PDFBox Preview",
                                        fontSize = 11.sp,
                                        color = EmeraldPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    metadata?.fileSizeFormatted?.let { size ->
                                        Text(text = " • $size", fontSize = 11.sp, color = TextMuted)
                                    }
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { showMetadataDetails = !showMetadataDetails },
                                modifier = Modifier.testTag("pdfbox_info_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Document Info",
                                    tint = if (showMetadataDetails) EmeraldPrimary else TextMuted
                                )
                            }

                            IconButton(
                                onClick = onDismissRequest,
                                modifier = Modifier.testTag("pdfbox_close_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Preview",
                                    tint = TextPrimary
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = showMetadataDetails) {
                    Surface(
                        color = CharcoalCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Document Properties (PDFBox Engine)",
                                fontWeight = FontWeight.Bold,
                                color = EmeraldPrimary,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            metadata?.let { meta ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Total Pages: ${meta.totalPages}", fontSize = 12.sp, color = TextPrimary)
                                    Text("File Size: ${meta.fileSizeFormatted}", fontSize = 12.sp, color = TextPrimary)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("PDF Version: ${meta.version}", fontSize = 12.sp, color = TextMuted)
                                    Text(
                                        if (meta.isEncrypted) "Protected / Encrypted" else "Standard Unencrypted",
                                        fontSize = 12.sp,
                                        color = if (meta.isEncrypted) RedDelete else EmeraldPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(CharcoalBackground)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoadingPage && currentBitmap == null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = EmeraldPrimary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Rendering page with PDFBox...", color = TextMuted, fontSize = 13.sp)
                        }
                    } else if (currentBitmap != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .rotate(rotationDegrees.toFloat())
                                .fillMaxSize()
                        ) {
                            Image(
                                bitmap = currentBitmap!!.asImageBitmap(),
                                contentDescription = "Page ${currentPageIndex + 1} Preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        if (isLoadingPage) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.align(Alignment.TopCenter)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        color = EmeraldPrimary,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Updating...", color = Color.White, fontSize = 11.sp)
                                }
                            }
                        }
                    } else {
                        Text("Unable to render page preview.", color = TextMuted)
                    }
                }

                Surface(
                    color = CharcoalCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { if (currentPageIndex > 0) currentPageIndex-- },
                                enabled = currentPageIndex > 0,
                                modifier = Modifier.testTag("pdfbox_prev_page_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Previous Page",
                                    tint = if (currentPageIndex > 0) EmeraldPrimary else TextMuted.copy(alpha = 0.3f)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(CharcoalHighlight)
                                    .border(1.dp, CharcoalCardBorder, RoundedCornerShape(20.dp))
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Page ${currentPageIndex + 1} of $totalPages",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            IconButton(
                                onClick = { if (currentPageIndex < totalPages - 1) currentPageIndex++ },
                                enabled = currentPageIndex < totalPages - 1,
                                modifier = Modifier.testTag("pdfbox_next_page_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Next Page",
                                    tint = if (currentPageIndex < totalPages - 1) EmeraldPrimary else TextMuted.copy(alpha = 0.3f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { if (scaleFactor > 0.8f) scaleFactor -= 0.3f },
                                modifier = Modifier.testTag("pdfbox_zoom_out_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ZoomOut,
                                    contentDescription = "Zoom Out",
                                    tint = TextPrimary
                                )
                            }

                            Text(
                                text = "${(scaleFactor * 100 / 1.2f).toInt()}%",
                                fontSize = 12.sp,
                                color = TextMuted,
                                fontWeight = FontWeight.SemiBold
                            )

                            IconButton(
                                onClick = { if (scaleFactor < 2.5f) scaleFactor += 0.3f },
                                modifier = Modifier.testTag("pdfbox_zoom_in_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ZoomIn,
                                    contentDescription = "Zoom In",
                                    tint = TextPrimary
                                )
                            }

                            IconButton(
                                onClick = { rotationDegrees = (rotationDegrees + 90) % 360 },
                                modifier = Modifier.testTag("pdfbox_rotate_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.RotateRight,
                                    contentDescription = "Rotate View",
                                    tint = EmeraldPrimary
                                )
                            }

                            IconButton(
                                onClick = {
                                    scaleFactor = 1.2f
                                    rotationDegrees = 0
                                },
                                modifier = Modifier.testTag("pdfbox_reset_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reset View",
                                    tint = TextMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
