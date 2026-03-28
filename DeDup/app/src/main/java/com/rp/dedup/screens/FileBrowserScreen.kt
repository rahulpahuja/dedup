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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Spacer(Modifier.width(2.dp))
            }
            val isLast = index == segments.lastIndex
            Text(
                text = segment,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isLast) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

// ─── File / folder row ────────────────────────────────────────────────────────

@Composable
private fun FileItemRow(item: FileItem, onClick: () -> Unit) {
    val (icon, iconColor) = fileIconAndColor(item)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon container
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(Modifier.width(14.dp))

        // Name + subtitle
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (item.isDirectory) {
                    "${item.childCount} item${if (item.childCount != 1) "s" else ""}  •  " +
                            relativeDate(item.lastModified)
                } else {
                    formatSize(item.size) + "  •  " + relativeDate(item.lastModified)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        Spacer(Modifier.width(8.dp))

        if (item.isDirectory) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.size(14.dp)
            )
        } else {
            Text(
                item.extension.uppercase().take(4),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    color = iconColor
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(iconColor.copy(alpha = 0.1f))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            )
        }
    }
}

// ─── Sort bottom sheet ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortBottomSheet(
    currentSort: SortMode,
    onSelect: (SortMode) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                "Sort by",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
            HorizontalDivider()
            SortMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(mode) }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        sortModeIcon(mode),
                        contentDescription = null,
                        tint = if (currentSort == mode) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        mode.label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = if (currentSort == mode) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    )
                    if (currentSort == mode) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─── Empty state ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyFolderState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "This folder is empty",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private data class IconSpec(val icon: ImageVector, val color: Color)

private fun fileIconAndColor(item: FileItem): IconSpec {
    if (item.isDirectory) return IconSpec(Icons.Default.Folder, UIConstants.ColorFileFolder)
    return when (item.extension) {
        "jpg", "jpeg", "png", "gif", "webp", "heic", "bmp" ->
            IconSpec(Icons.Default.Image, UIConstants.ColorFileImage)
        "mp4", "mkv", "avi", "mov", "webm", "ts", "3gp" ->
            IconSpec(Icons.Default.Videocam, UIConstants.ColorFileVideo)
        "mp3", "aac", "ogg", "flac", "wav", "m4a", "opus" ->
            IconSpec(Icons.Default.MusicNote, UIConstants.ColorFileAudio)
        "pdf" ->
            IconSpec(Icons.Default.PictureAsPdf, UIConstants.ColorFilePdf)
        "apk" ->
            IconSpec(Icons.Default.Android, UIConstants.ColorFileApk)
        "zip", "rar", "7z", "tar", "gz" ->
            IconSpec(Icons.Default.FolderZip, UIConstants.ColorFileArchive)
        "doc", "docx" ->
            IconSpec(Icons.Default.Description, UIConstants.ColorFileWordDoc)
        "xls", "xlsx" ->
            IconSpec(Icons.Default.TableChart, UIConstants.ColorFileSpreadsheet)
        "ppt", "pptx" ->
            IconSpec(Icons.Default.Slideshow, UIConstants.ColorFilePresentation)
        "txt", "md", "log" ->
            IconSpec(Icons.Default.Article, UIConstants.ColorFileText)
        else ->
            IconSpec(Icons.AutoMirrored.Filled.InsertDriveFile, UIConstants.ColorFileGeneric)
    }
}

private fun sortModeIcon(mode: SortMode): ImageVector = when (mode) {
    SortMode.NAME -> Icons.Default.SortByAlpha
    SortMode.SIZE -> Icons.Default.DataUsage
    SortMode.DATE -> Icons.Default.AccessTime
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0L       -> "0 B"
    bytes < 1024L     -> "$bytes B"
    bytes < 1_048_576 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1_073_741_824 -> "%.1f MB".format(bytes / 1_048_576.0)
    else              -> "%.2f GB".format(bytes / 1_073_741_824.0)
}

private fun relativeDate(epochMillis: Long): String {
    return DateUtils.getRelativeTimeSpanString(
        epochMillis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()
}

private fun mimeTypeFor(extension: String): String = when (extension.lowercase()) {
    "jpg", "jpeg"      -> "image/jpeg"
    "png"              -> "image/png"
    "gif"              -> "image/gif"
    "webp"             -> "image/webp"
    "heic"             -> "image/heic"
    "mp4"              -> "video/mp4"
    "mkv"              -> "video/x-matroska"
    "avi"              -> "video/x-msvideo"
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
        FileItem("Documents", "/storage/emulated/0/Documents", true, 0, System.currentTimeMillis(), 5, ""),
        FileItem("image.jpg", "/storage/emulated/0/image.jpg", false, 1024 * 500, System.currentTimeMillis() - 100000, 0, "jpg"),
        FileItem("report.pdf", "/storage/emulated/0/report.pdf", false, 1024 * 1024 * 2, System.currentTimeMillis() - 500000, 0, "pdf")
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
