package com.rp.dedup.screens

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import com.rp.dedup.screens.cleanup.components.*
import com.rp.dedup.ui.theme.DeDupTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.rp.dedup.core.ui.DeDupTopBar

// ── Domain model for the Large File Finder section ────────────────────────────

private enum class LargeFileCategory { VIDEO, ARCHIVE, APP_DOWNLOAD, OLD_DOWNLOAD }

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

    val filteredItems = largeFileItems.filter { item ->
        val stats = statsFor(item.category)
        stats.isLoading || stats.files.any { it.size >= minBytes }
    }

    val selectionBytes = selectedCategory?.let { cat ->
        statsFor(cat).files.filter { it.uri in selectedUris }.sumOf { it.size }
    } ?: 0L

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
                            Text(stringResource(R.string.delete))
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

            items(filteredItems, key = { it.category }) { item ->
                val stats = statsFor(item.category)
                val filteredFiles = remember(stats.files, minBytes) {
                    stats.files.filter { it.size >= minBytes }.sortedByDescending { it.size }
                }
                val filteredSize = filteredFiles.sumOf { it.size }
                val isExpanded   = selectedCategory == item.category

                val subtitle = when {
                    stats.isLoading         -> stringResource(R.string.scanning)
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

private fun LargeFileCategory.toScanType() = when (this) {
    LargeFileCategory.VIDEO        -> "LARGE_FILE_VIDEO"
    LargeFileCategory.ARCHIVE      -> "LARGE_FILE_ARCHIVE"
    LargeFileCategory.APP_DOWNLOAD -> "LARGE_FILE_APP"
    LargeFileCategory.OLD_DOWNLOAD -> "LARGE_FILE_DOWNLOAD"
}

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
