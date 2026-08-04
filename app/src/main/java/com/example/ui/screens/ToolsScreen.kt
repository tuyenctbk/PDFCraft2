package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.PdfOperationType
import com.example.ui.MainViewModel

data class ToolItemData(
    val titleRes: Int,
    val descRes: Int,
    val icon: ImageVector,
    val operationType: PdfOperationType
)

val ALL_PDF_TOOLS = listOf(
    ToolItemData(R.string.tool_merge_title, R.string.tool_merge_desc, Icons.Default.Merge, PdfOperationType.MERGE),
    ToolItemData(R.string.tool_split_title, R.string.tool_split_desc, Icons.Default.ContentCut, PdfOperationType.SPLIT),
    ToolItemData(R.string.tool_compress_title, R.string.tool_compress_desc, Icons.Default.Compress, PdfOperationType.COMPRESS),
    ToolItemData(R.string.tool_metadata_title, R.string.tool_metadata_desc, Icons.Default.Edit, PdfOperationType.METADATA),
    ToolItemData(R.string.tool_encrypt_title, R.string.tool_encrypt_desc, Icons.Default.Lock, PdfOperationType.ENCRYPT),
    ToolItemData(R.string.tool_watermark_title, R.string.tool_watermark_desc, Icons.Default.TextFields, PdfOperationType.WATERMARK),
    ToolItemData(R.string.tool_sign_title, R.string.tool_sign_desc, Icons.Default.Draw, PdfOperationType.SIGN),
    ToolItemData(R.string.tool_rotate_title, R.string.tool_rotate_desc, Icons.Default.RotateRight, PdfOperationType.ROTATE)
)

@Composable
fun ToolsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .align(Alignment.TopCenter)
                .padding(16.dp)
        ) {
            Text(
                text = "PDF Tool Catalog",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Select a professional utility to process your documents on-device",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(ALL_PDF_TOOLS) { tool ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clickable { viewModel.setActiveTool(tool.operationType) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = tool.icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Column {
                                Text(
                                    text = stringResource(tool.titleRes),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(tool.descRes),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
