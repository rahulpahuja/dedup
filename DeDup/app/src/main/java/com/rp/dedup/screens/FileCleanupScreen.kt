package com.rp.dedup.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.rp.dedup.LocalDrawerState
import com.rp.dedup.LocalUserProfileViewModel
import com.rp.dedup.R
import com.rp.dedup.Screen
import com.rp.dedup.core.model.CleanupCategoryStats
import com.rp.dedup.core.model.ScannedFile
import com.rp.dedup.core.repository.FileScannerRepository
import com.rp.dedup.core.viewmodels.CleanupViewModel
import com.rp.dedup.ui.theme.DeDupTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.rp.dedup.core.ui.DeDupTopBar

// ── Domain model for the Large File Finder section ────────────────────────────

private enum class LargeFileCategory { VIDEO, ARCHIVE, APP_DOWNLOAD, OLD_DOWNLOAD }
private enum class FileListViewMode  { LIST, GRID }

private data class LargeFileItemLocal(
    val title: String,
    val icon: ImageVector,
    val iconBg: Color,
    val iconTint: Color,
    val category: LargeFileCategory
)

private val MB = 1024L * 1024L

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileCleanupScreen(navController: NavHostController) {
    val context = LocalContext.current
    val analytics = remember { com.rp.dedup.core.analytics.AnalyticsManager.getInstance(context) }
    LaunchedEffect(Unit) { analytics.logScreenView("FileCleanup") }

    val cleanupViewModel: CleanupViewModel = viewModel(
        factory = CleanupViewModel.Factory(FileScannerRepository(context))
    )
    val cleanupState by cleanupViewModel.uiState.collectAsState()
    val profileViewModel = LocalUserProfileViewModel.current
    val drawerState = LocalDrawerState.current
    val scope = rememberCoroutineScope()

    // ── UI state ──────────────────────────────────────────────────────────────
    var selectedFilter   by remember { mutableStateOf(">100MB") }
    var selectedCategory by remember { mutableStateOf<LargeFileCategory?>(null) }
    var selectedUris     by remember { mutableStateOf(emptySet<Uri>()) }
    var pendingDeleteUris  by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var pendingDeleteBytes by remember { mutableStateOf(0L) }
    var showGuestDialog    by remember { mutableStateOf(false) }

    val sizeFilters = remember {
        listOf(">50MB" to 50L * MB, ">100MB" to 100L * MB, ">200MB" to 200L * MB)
    }
    val minBytes = sizeFilters.first { it.first == selectedFilter }.second

    // ── Delete launcher ───────────────────────────────────────────────────────
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            analytics.logFilesDeleted(
                selectedCategory?.toScanType() ?: "LARGE_FILE",
                pendingDeleteUris.size,
                pendingDeleteBytes
            )
            cleanupViewModel.onFilesDeleted(pendingDeleteUris.toSet())
            selectedUris       = emptySet()
            pendingDeleteUris  = emptyList()
            pendingDeleteBytes = 0L
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun statsFor(cat: LargeFileCategory): CleanupCategoryStats = when (cat) {
        LargeFileCategory.VIDEO        -> cleanupState.videoStats
        LargeFileCategory.ARCHIVE      -> cleanupState.archiveStats
        LargeFileCategory.APP_DOWNLOAD -> cleanupState.appDownloadStats
        LargeFileCategory.OLD_DOWNLOAD -> cleanupState.oldDownloadStats
    }

    fun triggerDelete(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val freedBytes = uris.sumOf { uri ->
            selectedCategory?.let { cat -> statsFor(cat).files.find { it.uri == uri }?.size } ?: 0L
        }
        pendingDeleteUris  = uris
        pendingDeleteBytes = freedBytes
        val scanType = selectedCategory?.toScanType() ?: "LARGE_FILE"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            selectedCategory == LargeFileCategory.VIDEO) {
            runCatching {
                val pi = MediaStore.createDeleteRequest(context.contentResolver, uris)
                deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
            }.onFailure {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        uris.forEach { runCatching { context.contentResolver.delete(it, null, null) } }
                    }
                    analytics.logFilesDeleted(scanType, uris.size, freedBytes)
                    cleanupViewModel.onFilesDeleted(uris.toSet())
                    selectedUris       = emptySet()
                    pendingDeleteUris   = emptyList()
                    pendingDeleteBytes  = 0L
                }
            }
        } else {
            scope.launch {
                withContext(Dispatchers.IO) {
                    uris.forEach { runCatching { context.contentResolver.delete(it, null, null) } }
                }
                analytics.logFilesDeleted(scanType, uris.size, freedBytes)
                cleanupViewModel.onFilesDeleted(uris.toSet())
                selectedUris       = emptySet()
                pendingDeleteUris   = emptyList()
                pendingDeleteBytes  = 0L
            }
        }
    }

    // ── Category definitions ──────────────────────────────────────────────────

    val largeFileItems = listOf(
        LargeFileItemLocal(
            title    = stringResource(R.string.unused_video_assets),
            icon     = Icons.Default.VideoLibrary,
            iconBg   = Color(0xFFB2EBF2),
            iconTint = Color(0xFF006064),
            category = LargeFileCategory.VIDEO
        ),
        LargeFileItemLocal(
            title    = stringResource(R.string.obsolete_archives),
            icon     = Icons.Default.Archive,
            iconBg   = Color(0xFFEDE7F6),
            iconTint = Color(0xFF512DA8),
            category = LargeFileCategory.ARCHIVE
        ),
        LargeFileItemLocal(
            title    = stringResource(R.string.large_app_downloads),
            icon     = Icons.Default.Android,
            iconBg   = Color(0xFFE8F5E9),
            iconTint = Color(0xFF2E7D32),
            category = LargeFileCategory.APP_DOWNLOAD
        ),
        LargeFileItemLocal(
            title    = stringResource(R.string.old_downloads),
            icon     = Icons.Default.DownloadDone,
            iconBg   = Color(0xFFFFF9C4),
            iconTint = Color(0xFFFBC02D),
            category = LargeFileCategory.OLD_DOWNLOAD
        )
    )

    // Only show a category if it's loading or has at least one file above the threshold.
    val filteredItems = largeFileItems.filter { item ->
        val stats = statsFor(item.category)
        stats.isLoading || stats.files.any { it.size >= minBytes }
    }

    // ── Total size of currently selected files (for bottom bar label) ─────────
    val selectionBytes = selectedCategory?.let { cat ->
        statsFor(cat).files.filter { it.uri in selectedUris }.sumOf { it.size }
    } ?: 0L

    // ── Scaffold ──────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            DeDupTopBar(
                title = stringResource(R.string.app_name),
                navigationIcon = {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.menu))
                    }
                },
                actions = {
                    IconButton(onClick = { }) {
                        Surface(
                            shape = CircleShape,
                            modifier = Modifier.size(32.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = stringResource(R.string.profile),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (selectedUris.isNotEmpty()) {
                Surface(shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "${selectedUris.size} files selected",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                Formatter.formatShortFileSize(context, selectionBytes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                if (profileViewModel.isGuest) showGuestDialog = true
                                else triggerDelete(selectedUris.toList())
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Delete")
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp)
        ) {
            // ── Header ────────────────────────────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.screen_file_cleanup),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondary,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.scan_redundant_files_btn),
                            color = MaterialTheme.colorScheme.onSecondary,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.manage_docs_apks),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            // ── Scanner category shortcuts ────────────────────────────────────
            item {
                Text(
                    stringResource(R.string.scanner_categories),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CategoryCard(
                        title = stringResource(R.string.pdfs_label),
                        icon = Icons.Default.Description,
                        color = Color(0xFFE53935),
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate(Screen.FileScanner.createRoute("pdf")) }
                    )
                    CategoryCard(
                        title = stringResource(R.string.apks_label),
                        icon = Icons.Default.Android,
                        color = Color(0xFF43A047),
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate(Screen.FileScanner.createRoute("apk")) }
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            // ── Large File Finder section header + filter chips ───────────────
            item {
                Text(
                    stringResource(R.string.large_file_finder),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
                Text(
                    stringResource(R.string.large_file_finder_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sizeFilters.forEach { (label, _) ->
                        SizeFilterChip(
                            text = label,
                            isSelected = selectedFilter == label,
                            onClick = {
                                selectedFilter   = label
                                selectedCategory = null
                                selectedUris     = emptySet()
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // ── Category cards with inline file list ──────────────────────────
            items(filteredItems, key = { it.category }) { item ->
                val stats = statsFor(item.category)
                val filteredFiles = remember(stats.files, minBytes) {
                    stats.files.filter { it.size >= minBytes }.sortedByDescending { it.size }
                }
                val filteredSize = filteredFiles.sumOf { it.size }
                val isExpanded   = selectedCategory == item.category

                val subtitle = when {
                    stats.isLoading      -> stringResource(R.string.scanning)
                    filteredFiles.isEmpty() -> "No files above $selectedFilter"
                    else -> "${filteredFiles.size} files · ${Formatter.formatShortFileSize(context, filteredSize)}"
                }
                val sizeLabel = when {
                    stats.isLoading -> "…"
                    else -> Formatter.formatShortFileSize(context, filteredSize)
                }

                LargeFileCard(
                    title      = item.title,
                    subtitle   = subtitle,
                    size       = sizeLabel,
                    icon       = item.icon,
                    iconBg     = item.iconBg,
                    iconTint   = item.iconTint,
                    isExpanded = isExpanded,
                    onClick    = {
                        if (isExpanded) {
                            selectedCategory = null
                            selectedUris     = emptySet()
                        } else {
                            selectedCategory = item.category
                            selectedUris     = emptySet()
                        }
                    }
                )

                AnimatedVisibility(visible = isExpanded) {
                    LargeFileListSection(
                        files        = filteredFiles,
                        selectedUris = selectedUris,
                        onToggle     = { uri ->
                            selectedUris = if (uri in selectedUris)
                                selectedUris - uri else selectedUris + uri
                        },
                        onSelectAll  = { selectedUris = filteredFiles.map { it.uri }.toSet() },
                        onClearAll   = { selectedUris = emptySet() }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }

    if (showGuestDialog) {
        GuestSignInDialog(
            onDismiss = { showGuestDialog = false },
            onSignIn  = {
                showGuestDialog = false
                navController.navigate(Screen.Login.route) {
                    popUpTo(Screen.Dashboard.route) { inclusive = false }
                }
            }
        )
    }
}

// ── Category shortcut card (PDF / APK) ────────────────────────────────────────

@Composable
fun CategoryCard(
    title: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ── Size filter chip ──────────────────────────────────────────────────────────

@Composable
fun SizeFilterChip(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .height(32.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

// ── Large file category card ──────────────────────────────────────────────────

@Composable
fun LargeFileCard(
    title: String,
    subtitle: String,
    size: String,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    isCountType: Boolean = false,
    isExpanded: Boolean = false,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            // Icon + size row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = iconBg,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                if (!isCountType) {
                    Text(
                        size,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (isCountType) {
                Text(
                    size,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }

            // Title + subtitle + expand chevron
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess
                                  else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Inline file list shown when a category card is expanded ───────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LargeFileListSection(
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
                // ── Toolbar: count · select-all · list/grid toggle ────────────
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

                // ── Content ───────────────────────────────────────────────────
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
                            verticalArrangement   = Arrangement.spacedBy(8.dp),
                            maxItemsInEachRow = 3
                        ) {
                            files.forEach { file ->
                                LargeFileGridItem(
                                    file       = file,
                                    isSelected = file.uri in selectedUris,
                                    onToggle   = { onToggle(file.uri) },
                                    modifier   = Modifier
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

        // Thumbnail — image loads from URI; video shows icon placeholder with play badge
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
                    // Play badge
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

        // Size + explicit preview / play button
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
                        imageVector = if (isImage) Icons.Default.Visibility
                                      else Icons.Default.PlayArrow,
                        contentDescription = if (isImage) "Preview" else "Play",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    if (showImagePreview) {
        ImagePreviewDialog(
            uri      = file.uri,
            name     = file.name,
            onDismiss = { showImagePreview = false }
        )
    }
}

// ── Full-screen image preview dialog ─────────────────────────────────────────

@Composable
private fun ImagePreviewDialog(uri: Uri, name: String, onDismiss: () -> Unit) {
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
            // Top bar: file name + close button
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

private fun String.isImageFile() = lowercase() in setOf(
    "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "avif", "tiff"
)

private fun String.isVideoFile() = lowercase() in setOf(
    "mp4", "mkv", "mov", "avi", "3gp", "webm", "m4v", "ts", "flv"
)

private fun launchVideoPlayer(context: android.content.Context, uri: Uri, ext: String) {
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

// ── Grid item ─────────────────────────────────────────────────────────────────

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
        // ── Thumbnail / icon ──────────────────────────────────────────────────
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

        // ── Selection overlay ─────────────────────────────────────────────────
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            )
        }

        // ── Checkmark badge (top-right) ───────────────────────────────────────
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

        // ── Preview / play badge (top-left, only for image & video) ──────────
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

        // ── Filename + size overlay (bottom) ──────────────────────────────────
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
        ImagePreviewDialog(uri = file.uri, name = file.name, onDismiss = { showImagePreview = false })
    }
}

private fun LargeFileCategory.toScanType() = when (this) {
    LargeFileCategory.VIDEO        -> "LARGE_FILE_VIDEO"
    LargeFileCategory.ARCHIVE      -> "LARGE_FILE_ARCHIVE"
    LargeFileCategory.APP_DOWNLOAD -> "LARGE_FILE_APP"
    LargeFileCategory.OLD_DOWNLOAD -> "LARGE_FILE_DOWNLOAD"
}

private fun fileTypeIcon(ext: String): ImageVector = when (ext.lowercase()) {
    "pdf"                         -> Icons.Default.PictureAsPdf
    "apk", "obb"                  -> Icons.Default.Android
    "zip", "rar", "7z", "tar", "gz" -> Icons.Default.Archive
    else                          -> Icons.Default.InsertDriveFile
}

private fun fileTypeColor(ext: String): Color = when (ext.lowercase()) {
    "pdf"                         -> Color(0xFFE53935)
    "apk", "obb"                  -> Color(0xFF43A047)
    "zip", "rar", "7z", "tar", "gz" -> Color(0xFF7B1FA2)
    else                          -> Color(0xFF546E7A)
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Light Mode")
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    name = "Dark Mode"
)
@Composable
fun FileCleanupScreenPreview() {
    DeDupTheme {
        FileCleanupScreen(rememberNavController())
    }
}
