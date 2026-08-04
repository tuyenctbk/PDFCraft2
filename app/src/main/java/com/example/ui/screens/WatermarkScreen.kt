package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.pdf.*
import com.example.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatermarkScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val sourceUri by viewModel.watermarkSourceUri.collectAsState()
    val sourceFile by viewModel.watermarkSourceFile.collectAsState()
    val pageCount by viewModel.watermarkPageCount.collectAsState()

    val watermarkType by viewModel.watermarkType.collectAsState()
    val watermarkText by viewModel.watermarkText.collectAsState()
    val watermarkTextSize by viewModel.watermarkTextSize.collectAsState()
    val watermarkTextColorRgb by viewModel.watermarkTextColorRgb.collectAsState()
    val watermarkImageUri by viewModel.watermarkImageUri.collectAsState()
    val watermarkImageScale by viewModel.watermarkImageScale.collectAsState()
    val watermarkOpacity by viewModel.watermarkOpacity.collectAsState()
    val watermarkRotation by viewModel.watermarkRotation.collectAsState()
    val watermarkPosition by viewModel.watermarkPosition.collectAsState()
    val watermarkPageRange by viewModel.watermarkPageRange.collectAsState()
    val outputName by viewModel.watermarkOutputName.collectAsState()

    val previewBitmap by viewModel.watermarkPreviewBitmap.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val processingMessage by viewModel.processingMessage.collectAsState()

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadPdfForWatermark(it) }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.setWatermarkImageUri(it) }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isWide = maxWidth > 600.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .align(Alignment.TopCenter)
        ) {
            // --- HEADER BAR ---
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PDF Watermark Studio",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = sourceFile?.let { "${it.name} (${pageCount} pages • ${PdfEngine.formatFileSize(it.length())})" }
                                ?: "Brand & secure documents with custom text or image watermarks",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }

                    Button(
                        onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf", "image/*", "text/plain", "*/*")) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("select_watermark_pdf_button")
                    ) {
                        Icon(imageVector = Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (sourceFile == null) "Select File" else "Change", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        AnimatedVisibility(visible = isProcessing, enter = fadeIn(), exit = fadeOut()) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = processingMessage, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        if (sourceFile == null) {
            // --- EMPTY STATE ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.BrandingWatermark, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = "Brand & Protect PDF Documents",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Add custom text watermarks (e.g. CONFIDENTIAL, DRAFT, APPROVED) or company image logos with full control over opacity, position, scale, and angle.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf", "image/*", "text/plain", "*/*")) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("watermark_empty_import_button")
                        ) {
                            Icon(imageVector = Icons.Default.FileUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Document File", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // --- WATERMARK CONFIGURATION FORM ---
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // --- MODE SELECTOR TABS ---
                item {
                    TabRow(
                        selectedTabIndex = if (watermarkType == WatermarkType.TEXT) 0 else 1,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    ) {
                        Tab(
                            selected = watermarkType == WatermarkType.TEXT,
                            onClick = { viewModel.setWatermarkType(WatermarkType.TEXT) },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Text Watermark", fontWeight = FontWeight.Bold)
                                }
                            }
                        )
                        Tab(
                            selected = watermarkType == WatermarkType.IMAGE,
                            onClick = { viewModel.setWatermarkType(WatermarkType.IMAGE) },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Image Watermark", fontWeight = FontWeight.Bold)
                                }
                            }
                        )
                    }
                }

                // --- LIVE REAL-TIME PREVIEW CARD ---
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.RemoveRedEye, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Live Document Watermark Preview", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                }
                                AssistChip(
                                    onClick = { viewModel.triggerWatermarkPreviewUpdate() },
                                    label = { Text("Refresh") },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Box(
                                modifier = Modifier
                                    .height(240.dp)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (previewBitmap != null) {
                                    Image(
                                        bitmap = previewBitmap!!.asImageBitmap(),
                                        contentDescription = "Live Watermark Preview",
                                        modifier = Modifier.fillMaxHeight().padding(8.dp).clip(RoundedCornerShape(6.dp))
                                    )
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Generating live preview...", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Badges summary
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                SuggestionChip(
                                    onClick = { },
                                    label = { Text(if (watermarkType == WatermarkType.TEXT) "Text: ${watermarkText.ifBlank { "CONFIDENTIAL" }}" else "Image Logo") },
                                    modifier = Modifier.weight(1f)
                                )
                                SuggestionChip(
                                    onClick = { },
                                    label = { Text("Opacity: ${(watermarkOpacity * 100).toInt()}%") }
                                )
                                SuggestionChip(
                                    onClick = { },
                                    label = { Text("Angle: ${watermarkRotation.toInt()}°") }
                                )
                            }
                        }
                    }
                }

                // --- TEXT WATERMARK OPTIONS ---
                if (watermarkType == WatermarkType.TEXT) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Watermark Text & Typography", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = watermarkText,
                                    onValueChange = { viewModel.setWatermarkText(it) },
                                    label = { Text("Watermark Text") },
                                    placeholder = { Text("e.g. CONFIDENTIAL, DRAFT, APPROVED") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("watermark_text_input")
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Text("Quick Presets:", style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                                Spacer(modifier = Modifier.height(6.dp))

                                val textPresets = listOf("CONFIDENTIAL", "DRAFT", "APPROVED", "SAMPLE", "DO NOT COPY", "INTERNAL USE")
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(textPresets) { preset ->
                                        FilterChip(
                                            selected = watermarkText.equals(preset, ignoreCase = true),
                                            onClick = { viewModel.setWatermarkText(preset) },
                                            label = { Text(preset, fontSize = 12.sp) }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text("Text Size: ${watermarkTextSize.toInt()} pt", style = MaterialTheme.typography.labelMedium)
                                Slider(
                                    value = watermarkTextSize,
                                    onValueChange = { viewModel.setWatermarkTextSize(it) },
                                    valueRange = 18f..72f,
                                    steps = 8,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Text("Text Color:", style = MaterialTheme.typography.labelMedium)
                                Spacer(modifier = Modifier.height(8.dp))

                                val colorPalette = listOf(
                                    Pair("Gray", intArrayOf(180, 180, 180)),
                                    Pair("Dark", intArrayOf(60, 60, 60)),
                                    Pair("Red", intArrayOf(220, 38, 38)),
                                    Pair("Blue", intArrayOf(37, 99, 235)),
                                    Pair("Green", intArrayOf(22, 163, 74)),
                                    Pair("Amber", intArrayOf(217, 119, 6))
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    colorPalette.forEach { (name, rgb) ->
                                        val isSelected = watermarkTextColorRgb.contentEquals(rgb)
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color(rgb[0], rgb[1], rgb[2]))
                                                .border(
                                                    width = if (isSelected) 3.dp else 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                    shape = CircleShape
                                                )
                                                .clickable { viewModel.setWatermarkTextColorRgb(rgb) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // --- IMAGE WATERMARK OPTIONS ---
                if (watermarkType == WatermarkType.IMAGE) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Watermark Logo Image", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = { imagePickerLauncher.launch(arrayOf("image/*")) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().height(46.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (watermarkImageUri == null) "Select Watermark Logo Image" else "Change Logo Image", fontWeight = FontWeight.Bold)
                                }

                                if (watermarkImageUri != null) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(8.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Logo selected", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                        TextButton(onClick = { viewModel.setWatermarkImageUri(null) }) {
                                            Text("Remove", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text("Logo Scale: ${(watermarkImageScale * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                                Slider(
                                    value = watermarkImageScale,
                                    onValueChange = { viewModel.setWatermarkImageScale(it) },
                                    valueRange = 0.1f..1.0f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // --- STYLING & POSITIONING OPTIONS ---
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Watermark Position & Transparency", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.height(14.dp))

                            Text("Opacity / Transparency: ${(watermarkOpacity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                            Slider(
                                value = watermarkOpacity,
                                onValueChange = { viewModel.setWatermarkOpacity(it) },
                                valueRange = 0.1f..0.9f,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text("Rotation Angle:", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(6.dp))

                            val angles = listOf(
                                Pair("0° Horizontal", 0f),
                                Pair("30° Diagonal", 30f),
                                Pair("45° Diagonal", 45f),
                                Pair("90° Vertical", 90f),
                                Pair("-45° Reverse", -45f)
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(angles) { (label, angle) ->
                                    FilterChip(
                                        selected = watermarkRotation == angle,
                                        onClick = { viewModel.setWatermarkRotation(angle) },
                                        label = { Text(label, fontSize = 12.sp) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text("Placement / Alignment:", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(8.dp))

                            val positions = listOf(
                                Pair("Center", WatermarkPosition.CENTER),
                                Pair("Top Left", WatermarkPosition.TOP_LEFT),
                                Pair("Top Right", WatermarkPosition.TOP_RIGHT),
                                Pair("Bottom Left", WatermarkPosition.BOTTOM_LEFT),
                                Pair("Bottom Right", WatermarkPosition.BOTTOM_RIGHT),
                                Pair("Tile Pattern", WatermarkPosition.TILE)
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                    FilterChip(
                                        selected = watermarkPosition == WatermarkPosition.CENTER,
                                        onClick = { viewModel.setWatermarkPosition(WatermarkPosition.CENTER) },
                                        label = { Text("Center") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = watermarkPosition == WatermarkPosition.TILE,
                                        onClick = { viewModel.setWatermarkPosition(WatermarkPosition.TILE) },
                                        label = { Text("Tile Pattern") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                    FilterChip(
                                        selected = watermarkPosition == WatermarkPosition.TOP_LEFT,
                                        onClick = { viewModel.setWatermarkPosition(WatermarkPosition.TOP_LEFT) },
                                        label = { Text("Top Left") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = watermarkPosition == WatermarkPosition.TOP_RIGHT,
                                        onClick = { viewModel.setWatermarkPosition(WatermarkPosition.TOP_RIGHT) },
                                        label = { Text("Top Right") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                    FilterChip(
                                        selected = watermarkPosition == WatermarkPosition.BOTTOM_LEFT,
                                        onClick = { viewModel.setWatermarkPosition(WatermarkPosition.BOTTOM_LEFT) },
                                        label = { Text("Bottom Left") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = watermarkPosition == WatermarkPosition.BOTTOM_RIGHT,
                                        onClick = { viewModel.setWatermarkPosition(WatermarkPosition.BOTTOM_RIGHT) },
                                        label = { Text("Bottom Right") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text("Apply to Pages:", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                FilterChip(
                                    selected = watermarkPageRange == WatermarkPageRange.ALL_PAGES,
                                    onClick = { viewModel.setWatermarkPageRange(WatermarkPageRange.ALL_PAGES) },
                                    label = { Text("All Pages") },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = watermarkPageRange == WatermarkPageRange.FIRST_PAGE_ONLY,
                                    onClick = { viewModel.setWatermarkPageRange(WatermarkPageRange.FIRST_PAGE_ONLY) },
                                    label = { Text("First Page Only") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // --- OUTPUT FILE NAME ---
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Export Configuration", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = outputName,
                                onValueChange = { viewModel.setWatermarkOutputName(it) },
                                label = { Text("Output PDF File Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("watermark_output_name_input")
                            )
                        }
                    }
                }
            }

            // --- BOTTOM EXPORT ACTION BAR ---
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    Button(
                        onClick = { viewModel.executeWatermark() },
                        enabled = !isProcessing && sourceFile != null && (watermarkType == WatermarkType.TEXT || watermarkImageUri != null),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("execute_watermark_button")
                    ) {
                        Icon(imageVector = Icons.Default.BrandingWatermark, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Apply Watermark & Export PDF", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
}
