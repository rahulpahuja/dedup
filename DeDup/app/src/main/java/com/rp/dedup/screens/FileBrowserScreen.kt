package com.rp.dedup.screens

import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.rp.dedup.UIConstants
import com.rp.dedup.core.browser.FileItem
import com.rp.dedup.core.viewmodels.FileBrowserViewModel
import com.rp.dedup.core.viewmodels.SortMode
import com.rp.dedup.ui.theme.DeDupTheme
import java.io.File
import java.util.Locale

// ─── Entry point ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(navController: NavHostController) {
    val vm: FileBrowserViewModel = viewModel()
    val context = LocalContext.current

    val items by vm.items.collectAsState()
    val currentDir by vm.currentDir.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val sortMode by vm.sortMode.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val breadcrumbs by vm.breadcrumbs.collectAsState()
    val errorMessage by vm.errorMessage.collectAsState()

    var searchActive by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }

    // Intercept system back button
    BackHandler(enabled = vm.canNavigateUp) {
        vm.navigateUp()
    }

    FileBrowserContent(
        navController = navController,
        items = items,
        currentDir = currentDir,
        isLoading = isLoading,
        sortMode = sortMode,
        searchQuery = searchQuery,
        breadcrumbs = breadcrumbs,
        errorMessage = errorMessage,
        canNavigateUp = vm.canNavigateUp,
        searchActive = searchActive,
        onSearchActiveChange = { searchActive = it },
        onSearchQueryChange = vm::setSearchQuery,
        onNavigateUp = vm::navigateUp,
        onNavigateToDir = { vm.navigateTo(File(it)) },
        onRefresh = vm::refresh,
        onSortClick = { showSortSheet = true },
        onOpenFile = { openFile(context, File(it)) }
    )

    // Sort bottom sheet
    if (showSortSheet) {
        SortBottomSheet(
            currentSort = sortMode,
            onSelect = { vm.setSortMode(it); showSortSheet = false },
            onDismiss = { showSortSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserContent(
    navController: NavHostController,
    items: List<FileItem>,
    currentDir: File,
    isLoading: Boolean,
    sortMode: SortMode,
    searchQuery: String,
    breadcrumbs: List<String>,
    errorMessage: String?,
    canNavigateUp: Boolean,
    searchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateToDir: (String) -> Unit,
    onRefresh: () -> Unit,
    onSortClick: () -> Unit,
    onOpenFile: (String) -> Unit
) {
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (searchActive) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                placeholder = {
                                    Text(
                                        "Search in ${currentDir.name}…",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent
                                )
                            )
                        } else {
                            Text(
                                currentDir.name.let { name ->
                                    if (currentDir.absolutePath == "/storage/emulated/0"
                                    ) UIConstants.FILE_BROWSER_ROOT_LABEL else name
                                },
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        if (canNavigateUp) {
                            IconButton(onClick = onNavigateUp) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                            }
                        } else {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close")
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            onSearchActiveChange(!searchActive)
                            if (!searchActive) onSearchQueryChange("")
                        }) {
                            Icon(
                                if (searchActive) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = if (searchActive) "Close search" else "Search"
                            )
                        }
                        IconButton(onClick = onSortClick) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort")
                        }
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Breadcrumb path bar
                BreadcrumbBar(breadcrumbs)

                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    ) { paddingValues ->

        val dirCount = items.count { it.isDirectory }
        val fileCount = items.count { !it.isDirectory }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Stats row
            item {
                if (!isLoading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            buildString {
                                if (dirCount > 0) append("$dirCount folder${if (dirCount > 1) "s" else ""}")
                                if (dirCount > 0 && fileCount > 0) append("  •  ")
                                if (fileCount > 0) append("$fileCount file${if (fileCount > 1) "s" else ""}")
                                if (dirCount == 0 && fileCount == 0) append("Empty folder")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider()
                }
            }

            // Error state
            errorMessage?.let { msg ->
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                msg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Empty state (no error, just empty)
            if (items.isEmpty() && !isLoading && errorMessage == null) {
                item { EmptyFolderState() }
            }

            // File/folder items
            items(items, key = { it.path }) { item ->
                FileItemRow(
                    item = item,
                    onClick = {
                        if (item.isDirectory) {
                            onNavigateToDir(item.path)
                        } else {
                            onOpenFile(item.path)
                        }
                    }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ─── Breadcrumb bar ───────────────────────────────────────────────────────────

@Composable
private fun BreadcrumbBar(segments: List<String>) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        segments.forEachIndexed { index, segment ->
            if (index > 0) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    modifier = Modifier
                        .size(10.dp)
                        .padding(horizontal = 2.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
            Text(
                text = segment,
                style = MaterialTheme.typography.bodySmall,
                color = if (index == segments.lastIndex)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (index == segments.lastIndex) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

// ─── List items ──────────────────────────────────────────────────────────────

@Composable
private fun FileItemRow(item: FileItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (item.isDirectory)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (item.isDirectory) Icons.Default.Folder else iconForExtension(item.extension),
                contentDescription = null,
                tint = if (item.isDirectory)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(Modifier.width(16.dp))

        // Text details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (item.isDirectory) {
                        "${item.childCount} items"
                    } else {
                        formatFileSize(item.size)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "  •  ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Text(
                    text = DateUtils.getRelativeTimeSpanString(
                        item.lastModified,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS
                    ).toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (item.isDirectory) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun EmptyFolderState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 100.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "No files found",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

// ─── Bottom Sheet ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortBottomSheet(
    currentSort: SortMode,
    onSelect: (SortMode) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                "Sort by",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )
            SortOption(
                label = "Name",
                icon = Icons.Default.SortByAlpha,
                selected = currentSort == SortMode.NAME,
                onClick = { onSelect(SortMode.NAME) }
            )
            SortOption(
                label = "Date",
                icon = Icons.Default.Event,
                selected = currentSort == SortMode.DATE,
                onClick = { onSelect(SortMode.DATE) }
            )
            SortOption(
                label = "Size",
                icon = Icons.Default.VerticalAlignBottom,
                selected = currentSort == SortMode.SIZE,
                onClick = { onSelect(SortMode.SIZE) }
            )
        }
    }
}

@Composable
private fun SortOption(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
        Spacer(Modifier.weight(1f))
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun iconForExtension(ext: String): ImageVector {
    return when (ext.lowercase()) {
        "jpg", "jpeg", "png", "webp", "gif" -> Icons.Default.Image
        "mp4", "mkv", "mov", "avi" -> Icons.Default.Movie
        "mp3", "wav", "flac", "ogg", "aac" -> Icons.Default.AudioFile
        "pdf" -> Icons.Default.PictureAsPdf
        "zip", "rar", "7z", "tar" -> Icons.Default.FolderZip
        "txt", "doc", "docx", "pdf" -> Icons.AutoMirrored.Filled.InsertDriveFile
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
}

private fun mimeTypeFor(extension: String): String = when (extension.lowercase()) {
    "jpg", "jpeg"      -> "image/jpeg"
    "png"              -> "image/png"
    "webp"             -> "image/webp"
    "gif"              -> "image/gif"
    "mp4"              -> "video/mp4"
    "mkv"              -> "video/x-matroska"
    "mov"              -> "video/quicktime"
    "mp3"              -> "audio/mpeg"
    "aac"              -> "audio/aac"
    "ogg"              -> "audio/ogg"
    "flac"             -> "audio/flac"
    "wav"              -> "audio/wav"
    "pdf"              -> "application/pdf"
    "apk"              -> "application/vnd.android.package-archive"
    "zip"              -> "application/zip"
    "rar"              -> "application/x-rar-compressed"
    "doc", "docx"      -> "application/msword"
    "xls", "xlsx"      -> "application/vnd.ms-excel"
    "ppt", "pptx"      -> "application/vnd.ms-powerpoint"
    "txt"              -> "text/plain"
    else               -> "*/*"
}

private fun openFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeTypeFor(file.extension))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Open with…"))
    } catch (_: Exception) {
        // No app available to handle this file type — silently ignore
    }
}

@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
private fun FileBrowserPreview() {
    val mockItems = listOf(
        FileItem("Documents", "/storage/emulated/0/Documents", true, 0L, System.currentTimeMillis(), "", 5),
        FileItem("image.jpg", "/storage/emulated/0/image.jpg", false, 1024L * 500, System.currentTimeMillis() - 100000, "jpg", 0),
        FileItem("report.pdf", "/storage/emulated/0/report.pdf", false, 1024L * 1024 * 2, System.currentTimeMillis() - 500000, "pdf", 0)
    )
    DeDupTheme {
        FileBrowserContent(
            navController = rememberNavController(),
            items = mockItems,
            currentDir = File("/storage/emulated/0"),
            isLoading = false,
            sortMode = SortMode.NAME,
            searchQuery = "",
            breadcrumbs = listOf("Internal Storage"),
            errorMessage = null,
            canNavigateUp = false,
            searchActive = false,
            onSearchActiveChange = {},
            onSearchQueryChange = {},
            onNavigateUp = {},
            onNavigateToDir = {},
            onRefresh = {},
            onSortClick = {},
            onOpenFile = {}
        )
    }
}
