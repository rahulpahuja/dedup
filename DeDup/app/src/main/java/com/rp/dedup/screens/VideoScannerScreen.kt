package com.rp.dedup.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.format.Formatter.formatFileSize
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.core.content.ContextCompat
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.rp.dedup.core.PaginationBar
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.rp.dedup.LocalDrawerState
import com.rp.dedup.R
import com.rp.dedup.LocalUserProfileViewModel
import com.rp.dedup.Screen
import com.rp.dedup.VideoScannerViewModelFactory
import com.rp.dedup.core.model.ScannedVideo
import com.rp.dedup.core.viewmodels.UserProfileViewModel
import com.rp.dedup.core.viewmodels.VideoScannerViewModel
import com.rp.dedup.ui.theme.DeDupTheme
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import androidx.core.content.FileProvider
import java.io.File
import com.rp.dedup.core.ui.DeDupTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoScannerScreen(navController: NavHostController) {
    val drawerState = LocalDrawerState.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val viewModel: VideoScannerViewModel = viewModel(
        factory = VideoScannerViewModelFactory(context)
    )

    val duplicateGroups by viewModel.duplicateGroups.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val scannedCount by viewModel.scannedCount.collectAsState()
    val cacheLoaded by viewModel.cacheLoaded.collectAsState()
    val resumedCount by viewModel.resumedCount.collectAsState()
    val analyticsManager = remember { com.rp.dedup.core.analytics.AnalyticsManager(context) }

    // True when the DB has results from a previous interrupted scan ready to show
    val hasCachedResults = cacheLoaded && duplicateGroups.isNotEmpty() && !isScanning
    val wasInterrupted = cacheLoaded && scannedCount > 0 && !isScanning && resumedCount == 0

    LaunchedEffect(Unit) {
        analyticsManager.logScreenView("VideoScanner")
    }

    LaunchedEffect(isScanning) {
        if (isScanning) analyticsManager.logScanStarted("VIDEO")
        else if (cacheLoaded) analyticsManager.logScanCompleted("VIDEO", scannedCount, duplicateGroups.size, 0L)
    }

    val profileViewModel = LocalUserProfileViewModel.current
    val isGuest = profileViewModel.isGuest

    val selectedUris = remember { mutableStateListOf<Uri>() }
    var pendingDeleteUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showBubblePermRationale by remember { mutableStateOf(false) }
    var showGuestSignInDialog by remember { mutableStateOf(false) }
    val bubblePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) com.rp.dedup.core.bubble.BubbleLauncher.launch(context)
        else showBubblePermRationale = true
    }
    val onBubbleClick: () -> Unit = {
        val hasPermission = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasPermission) com.rp.dedup.core.bubble.BubbleLauncher.launch(context)
        else bubblePermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            analyticsManager.logFilesDeleted("VIDEO", pendingDeleteUris.size, 0L)
            viewModel.removeDeletedVideosFromUI(pendingDeleteUris)
            selectedUris.removeAll(pendingDeleteUris)
            pendingDeleteUris = emptyList()
        }
    }

    fun triggerDelete(uris: List<Uri>) {
        if (uris.isEmpty()) return
        pendingDeleteUris = uris
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pi = MediaStore.createDeleteRequest(context.contentResolver, uris)
            deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        } else {
            uris.forEach { context.contentResolver.delete(it, null, null) }
            analyticsManager.logFilesDeleted("VIDEO", uris.size, 0L)
            viewModel.removeDeletedVideosFromUI(uris)
            selectedUris.removeAll(uris)
        }
    }

    if (showBubblePermRationale) {
        AlertDialog(
            onDismissRequest = { showBubblePermRationale = false },
            title = { Text("Notifications Required") },
            text = { Text("Allow notifications to show the floating scanner bubble. Enable them in Settings.") },
            confirmButton = {
                TextButton(onClick = {
                    showBubblePermRationale = false
                    context.startActivity(
                        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", context.packageName, null)
                        }
                    )
                }) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = { showBubblePermRationale = false }) { Text("Not now") }
            }
        )
    }

    if (showGuestSignInDialog) {
        GuestSignInDialog(
            onDismiss = { showGuestSignInDialog = false },
            onSignIn = {
                showGuestSignInDialog = false
                navController.navigate(Screen.Login.route) {
                    popUpTo(Screen.Dashboard.route) { inclusive = false }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            DeDupTopBar(
                title = "DeDup",
                navigationIcon = {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.menu))
                    }
                },
                actions = {
                    if (selectedUris.isNotEmpty()) {
                        IconButton(onClick = {
                            if (isGuest) showGuestSignInDialog = true
                            else triggerDelete(selectedUris.toList())
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_selected_btn, selectedUris.size, ""), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Text(
                text = stringResource(R.string.video_scanner_title),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                modifier = androidx.compose.ui.Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                color = MaterialTheme.colorScheme.onBackground,
            )
            // ── Resume banner ──────────────────────────────────────────────
            if (hasCachedResults && resumedCount == 0) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.RestoreFromTrash,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Previous scan saved",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "Tap Scan to resume where you left off, or Rescan to start fresh.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                selectedUris.clear()
                                viewModel.clearCache()
                            }
                        ) {
                            Text(
                                "Clear",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // ── Resuming indicator ─────────────────────────────────────────
            if (isScanning && resumedCount > 0) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Replay,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Resuming — skipped $resumedCount already-scanned videos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Header + scan button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.scanner_header),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    when {
                        isScanning -> Text(
                            stringResource(R.string.scanned_videos_summary, scannedCount, duplicateGroups.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        duplicateGroups.isNotEmpty() -> Text(
                            stringResource(R.string.duplicate_groups_found, duplicateGroups.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // App Bubble Action (Android 17)
                if (android.os.Build.VERSION.SDK_INT >= 37 && isScanning) {
                    IconButton(
                        onClick = onBubbleClick,
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                    ) {
                        Icon(
                            Icons.Default.OpenInFull,
                            contentDescription = "Bubble Scan",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }

                // Show "Rescan" (force-fresh) alongside "Stop"/"Scan" when cached results exist
                if (hasCachedResults && !isScanning) {
                    OutlinedButton(
                        onClick = {
                            selectedUris.clear()
                            viewModel.startScanning(deepScan = true, forceRescan = true)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Rescan")
                    }
                }

                Button(
                    onClick = {
                        if (isScanning) viewModel.cancelScanning()
                        else {
                            selectedUris.clear()
                            // Resume by default (skips already-scanned URIs from cache)
                            viewModel.startScanning(deepScan = true, forceRescan = false)
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (isScanning) Icons.Default.Stop else Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            isScanning -> stringResource(R.string.stop_btn)
                            hasCachedResults -> "Resume"
                            else -> stringResource(R.string.scan_btn)
                        }
                    )
                }
            }

            if (isScanning && duplicateGroups.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.analyzing_videos),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (!isScanning && duplicateGroups.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Videocam,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.no_video_duplicates),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                if (isScanning) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                val videoPageSize = 5
                var currentPage by remember { mutableStateOf(1) }
                val totalPages = remember(duplicateGroups.size) {
                    maxOf(1, (duplicateGroups.size + videoPageSize - 1) / videoPageSize)
                }
                LaunchedEffect(isScanning) { if (isScanning) currentPage = 1 }

                val listState = rememberLazyListState()
                LaunchedEffect(currentPage) { listState.scrollToItem(0) }

                val pageStart = (currentPage - 1) * videoPageSize
                val pageGroups = remember(currentPage, duplicateGroups) {
                    duplicateGroups.subList(
                        pageStart.coerceAtMost(duplicateGroups.size),
                        (pageStart + videoPageSize).coerceAtMost(duplicateGroups.size)
                    )
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        items(pageGroups) { group ->
                            DuplicateVideoGroup(
                                group = group,
                                selectedUris = selectedUris,
                                onToggleSelect = { uri ->
                                    if (selectedUris.contains(uri)) selectedUris.remove(uri)
                                    else selectedUris.add(uri)
                                }
                            )
                        }
                    }

                    if (totalPages > 1) {
                        PaginationBar(
                            currentPage = currentPage,
                            totalPages = totalPages,
                            onPageChange = { currentPage = it }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DuplicateVideoGroup(
    group: List<ScannedVideo>,
    selectedUris: List<Uri>,
    onToggleSelect: (Uri) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.duplicate_group_items, group.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        // Fixed height for simplicity within LazyColumn, or use a non-nesting approach
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Display duplicates in a grid-like fashion using Rows
            val chunks = group.chunked(2)
            chunks.forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowItems.forEachIndexed { indexInRow, video ->
                        Box(modifier = Modifier.weight(1f)) {
                            VideoGridItem(
                                video = video,
                                isSelected = selectedUris.contains(video.uri),
                                isKeep = indexInRow == 0 && group.indexOf(video) == 0,
                                onToggleSelect = { onToggleSelect(video.uri) }
                            )
                        }
                    }
                    if (rowItems.size < 2) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun VideoGridItem(
    video: ScannedVideo,
    isSelected: Boolean,
    isKeep: Boolean,
    onToggleSelect: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.8f)
            .border(
                width = if (isSelected) 3.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.error else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onToggleSelect() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = video.uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                // Selection Checkbox
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    modifier = Modifier.align(Alignment.TopEnd),
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.error,
                        checkmarkColor = Color.White
                    )
                )

                if (isKeep) {
                    Surface(
                        color = Color(0xFF2E7D32),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.keep),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                // Play Button Overlay
                IconButton(
                    onClick = {
                        val videoFile = video.path?.let { File(it) }
                        val uriToShare = if (videoFile != null && videoFile.exists()) {
                            FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                videoFile
                            )
                        } else {
                            video.uri
                        }

                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uriToShare, "video/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Play Video"))
                    },
                    modifier = Modifier.size(40.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.play),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Duration Badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = formatDuration(video.durationMs),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
            
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = formatFileSize(context, video.sizeInBytes),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = video.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "0:00"
    val hours = TimeUnit.MILLISECONDS.toHours(durationMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

@Preview(showBackground = true)
@Composable
private fun VideoScannerScreenPreview() {
    DeDupTheme {
        val navController = rememberNavController()
        VideoScannerScreen(navController = navController)
    }
}
