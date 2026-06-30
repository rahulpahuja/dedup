package com.rp.dedup.screens.cleanup.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.rp.dedup.core.model.ScannedFile

enum class FileListViewMode { LIST, GRID }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LargeFileListSection(
    files: List<ScannedFile>,
    selectedUris: Set<Uri>,
    onToggle: (Uri) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit
) {
    var viewMode by remember { mutableStateOf(FileListViewMode.LIST) }
    val allSelected = files.isNotEmpty() && selectedUris.size == files.size

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (files.isEmpty()) {
                Text(
                    "No files above this size threshold",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${files.size} files · ${selectedUris.size} selected",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = if (allSelected) onClearAll else onSelectAll) {
                            Text(if (allSelected) "Deselect All" else "Select All")
                        }
                        IconButton(onClick = { viewMode = FileListViewMode.LIST }) {
                            Icon(
                                Icons.Default.ViewList,
                                contentDescription = "List view",
                                tint = if (viewMode == FileListViewMode.LIST)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = { viewMode = FileListViewMode.GRID }) {
                            Icon(
                                Icons.Default.GridView,
                                contentDescription = "Grid view",
                                tint = if (viewMode == FileListViewMode.GRID)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                HorizontalDivider()

                when (viewMode) {
                    FileListViewMode.LIST -> {
                        files.forEach { file ->
                            LargeFileRow(
                                file = file,
                                isSelected = file.uri in selectedUris,
                                onToggle = { onToggle(file.uri) }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                    FileListViewMode.GRID -> {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            maxItemsInEachRow = 3
                        ) {
                            files.forEach { file ->
                                LargeFileGridItem(
                                    file = file,
                                    isSelected = file.uri in selectedUris,
                                    onToggle = { onToggle(file.uri) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun LargeFileRow(
    file: ScannedFile,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val context = LocalContext.current
    val isImage = file.extension.isImageFile()
    val isVideo = file.extension.isVideoFile()
    var showImagePreview by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(8.dp))

        if (isImage || isVideo) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        if (isImage) showImagePreview = true
                        else launchVideoPlayer(context, file.uri, file.extension)
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isImage) {
                    AsyncImage(
                        model = file.uri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.VideoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.size(28.dp)
                    )
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(18.dp)
                            .align(Alignment.BottomEnd)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = file.path.substringBeforeLast('/'),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(4.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = Formatter.formatShortFileSize(context, file.size),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isImage || isVideo) {
                IconButton(
                    onClick = {
                        if (isImage) showImagePreview = true
                        else launchVideoPlayer(context, file.uri, file.extension)
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isImage) Icons.Default.Visibility else Icons.Default.PlayArrow,
                        contentDescription = if (isImage) "Preview" else "Play",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    if (showImagePreview) {
        LargeFileImagePreviewDialog(
            uri = file.uri,
            name = file.name,
            onDismiss = { showImagePreview = false }
        )
    }
}

@Composable
private fun LargeFileGridItem(
    file: ScannedFile,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isImage = file.extension.isImageFile()
    val isVideo = file.extension.isVideoFile()
    var showImagePreview by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onToggle)
    ) {
        when {
            isImage -> AsyncImage(
                model = file.uri,
                contentDescription = file.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            isVideo -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.VideoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.size(40.dp)
                )
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            else -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = fileTypeIcon(file.extension),
                    contentDescription = null,
                    tint = fileTypeColor(file.extension),
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            )
        }

        Box(
            modifier = Modifier
                .padding(5.dp)
                .size(20.dp)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary
                    else Color.Black.copy(alpha = 0.38f),
                    CircleShape
                )
                .align(Alignment.TopEnd),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        if (isImage || isVideo) {
            Box(
                modifier = Modifier
                    .padding(5.dp)
                    .size(24.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    .align(Alignment.TopStart)
                    .clickable {
                        if (isImage) showImagePreview = true
                        else launchVideoPlayer(context, file.uri, file.extension)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isImage) Icons.Default.Visibility else Icons.Default.PlayArrow,
                    contentDescription = if (isImage) "Preview" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = Formatter.formatShortFileSize(context, file.size),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = Color.White.copy(alpha = 0.75f)
            )
        }
    }

    if (showImagePreview) {
        LargeFileImagePreviewDialog(uri = file.uri, name = file.name, onDismiss = { showImagePreview = false })
    }
}

@Composable
private fun LargeFileImagePreviewDialog(uri: Uri, name: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .clickable(onClick = onDismiss)
        ) {
            AsyncImage(
                model = uri,
                contentDescription = name,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 56.dp, bottom = 48.dp),
                contentScale = ContentScale.Fit
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}

// ── File-type helpers ─────────────────────────────────────────────────────────

fun String.isImageFile() = lowercase() in setOf(
    "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "avif", "tiff"
)

fun String.isVideoFile() = lowercase() in setOf(
    "mp4", "mkv", "mov", "avi", "3gp", "webm", "m4v", "ts", "flv"
)

fun launchVideoPlayer(context: Context, uri: Uri, ext: String) {
    val mime = when (ext.lowercase()) {
        "mp4"  -> "video/mp4"
        "mkv"  -> "video/x-matroska"
        "mov"  -> "video/quicktime"
        "avi"  -> "video/avi"
        "3gp"  -> "video/3gpp"
        "webm" -> "video/webm"
        else   -> "video/*"
    }
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )
    }
}

fun fileTypeIcon(ext: String): ImageVector = when (ext.lowercase()) {
    "pdf"                             -> Icons.Default.PictureAsPdf
    "apk", "obb"                      -> Icons.Default.Android
    "zip", "rar", "7z", "tar", "gz"   -> Icons.Default.Archive
    else                              -> Icons.Default.InsertDriveFile
}

fun fileTypeColor(ext: String): Color = when (ext.lowercase()) {
    "pdf"                             -> Color(0xFFE53935)
    "apk", "obb"                      -> Color(0xFF43A047)
    "zip", "rar", "7z", "tar", "gz"   -> Color(0xFF7B1FA2)
    else                              -> Color(0xFF546E7A)
}
