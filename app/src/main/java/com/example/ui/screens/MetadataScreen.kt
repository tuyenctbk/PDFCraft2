package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.pdf.PdfEngine
import com.example.ui.MainViewModel
import com.example.ui.components.PdfDocumentPreviewCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val sourceUri by viewModel.metadataSourceUri.collectAsState()
    val sourceFile by viewModel.metadataSourceFile.collectAsState()

    val title by viewModel.metadataTitle.collectAsState()
    val author by viewModel.metadataAuthor.collectAsState()
    val subject by viewModel.metadataSubject.collectAsState()
    val keywords by viewModel.metadataKeywords.collectAsState()
    val creator by viewModel.metadataCreator.collectAsState()
    val producer by viewModel.metadataProducer.collectAsState()
    val creationDate by viewModel.metadataCreationDate.collectAsState()
    val modificationDate by viewModel.metadataModificationDate.collectAsState()
    val pageCount by viewModel.metadataPageCount.collectAsState()
    val fileSizeFormatted by viewModel.metadataFileSizeFormatted.collectAsState()
    val outputName by viewModel.metadataOutputName.collectAsState()

    val isProcessing by viewModel.isProcessing.collectAsState()
    val processingMessage by viewModel.processingMessage.collectAsState()

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadPdfForMetadata(it) }
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
                            text = "PDF Metadata Editor",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = sourceFile?.let { "${it.name} (${pageCount} pages • ${fileSizeFormatted})" }
                                ?: "View & edit document title, author, subject, and keywords",
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
                        modifier = Modifier.testTag("select_metadata_pdf_button")
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
                            Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = "Edit PDF Document Metadata",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Update properties like Document Title, Author, Subject description, and searchable Keywords tags directly inside the PDF structure.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf", "image/*", "text/plain", "*/*")) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("metadata_empty_import_button")
                        ) {
                            Icon(imageVector = Icons.Default.FileUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Document File", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // --- METADATA EDITOR FORM ---
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    PdfDocumentPreviewCard(
                        file = sourceFile,
                        uri = sourceUri,
                        title = "Document Preview"
                    )
                }

                // --- DOCUMENT OVERVIEW CARD ---
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Document Info", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("File Name", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(sourceFile?.name ?: "", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Pages & Size", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("$pageCount pages • $fileSizeFormatted", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            if (creator.isNotBlank() || producer.isNotBlank()) {
                                Divider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    if (creator.isNotBlank()) {
                                        Column {
                                            Text("Creator", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(creator, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    if (producer.isNotBlank()) {
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("Producer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(producer, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }

                            if (creationDate.isNotBlank() && creationDate != "Unknown") {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Created: $creationDate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Modified: $modificationDate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                // --- EDITABLE METADATA FIELDS ---
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Edit Metadata Fields", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.height(14.dp))

                            // 1. Title
                            OutlinedTextField(
                                value = title,
                                onValueChange = { viewModel.setMetadataTitle(it) },
                                label = { Text("Document Title") },
                                placeholder = { Text("e.g. Q3 Financial Report 2026") },
                                leadingIcon = { Icon(Icons.Default.Title, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("metadata_title_input")
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // 2. Author
                            OutlinedTextField(
                                value = author,
                                onValueChange = { viewModel.setMetadataAuthor(it) },
                                label = { Text("Author / Organization") },
                                placeholder = { Text("e.g. John Doe, ACME Corp") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("metadata_author_input")
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // 3. Subject
                            OutlinedTextField(
                                value = subject,
                                onValueChange = { viewModel.setMetadataSubject(it) },
                                label = { Text("Subject / Summary") },
                                placeholder = { Text("e.g. Quarterly earnings analysis and projections") },
                                leadingIcon = { Icon(Icons.Default.Topic, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth().testTag("metadata_subject_input")
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // 4. Keywords
                            OutlinedTextField(
                                value = keywords,
                                onValueChange = { viewModel.setMetadataKeywords(it) },
                                label = { Text("Keywords / Search Tags") },
                                placeholder = { Text("e.g. report, finance, quarterly, acme") },
                                leadingIcon = { Icon(Icons.Default.Label, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth().testTag("metadata_keywords_input")
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Quick keyword chips
                            Text("Add Quick Tags:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(6.dp))

                            val tagPresets = listOf("Confidential", "Draft", "Final", "Financial", "Legal", "Marketing", "Report")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(tagPresets) { tag ->
                                    AssistChip(
                                        onClick = {
                                            if (keywords.isBlank()) {
                                                viewModel.setMetadataKeywords(tag)
                                            } else if (!keywords.contains(tag, ignoreCase = true)) {
                                                viewModel.setMetadataKeywords("$keywords, $tag")
                                            }
                                        },
                                        label = { Text("+ $tag", fontSize = 12.sp) }
                                    )
                                }
                            }
                        }
                    }
                }

                // --- QUICK PRESETS CARD ---
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Quick Presets", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.height(10.dp))

                            Text("Subject Presets:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(6.dp))

                            val subjectPresets = listOf("Annual Report", "Business Proposal", "Contract Agreement", "Project Spec", "Meeting Agenda")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(subjectPresets) { preset ->
                                    FilterChip(
                                        selected = subject.equals(preset, ignoreCase = true),
                                        onClick = { viewModel.setMetadataSubject(preset) },
                                        label = { Text(preset, fontSize = 12.sp) }
                                    )
                                }
                            }
                        }
                    }
                }

                // --- EXPORT CONFIGURATION ---
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
                                onValueChange = { viewModel.setMetadataOutputName(it) },
                                label = { Text("Output PDF File Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("metadata_output_name_input")
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
                        onClick = { viewModel.executeMetadataUpdate() },
                        enabled = !isProcessing && sourceFile != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("execute_metadata_button")
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Metadata & Export PDF", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
}
