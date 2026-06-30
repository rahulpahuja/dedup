package com.rp.dedup.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.rp.dedup.R
import com.rp.dedup.LocalUserProfileViewModel
import com.rp.dedup.Screen
import com.rp.dedup.core.common.Constants
import com.rp.dedup.core.model.SocialMediaFile
import com.rp.dedup.core.model.state.SocialMediaCleanerState
import com.rp.dedup.core.ui.DeDupTopBar
import com.rp.dedup.core.viewmodels.SocialMediaCleanerViewModel
import com.rp.dedup.ui.theme.DeDupTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialMediaCleanerScreen(navController: NavHostController) {
    val context = LocalContext.current
    val viewModel: SocialMediaCleanerViewModel = viewModel(
        factory = SocialMediaCleanerViewModel.factory(context)
    )
    val profileViewModel = LocalUserProfileViewModel.current
    val state by viewModel.state.collectAsState()
    val selectedUris = remember { mutableStateSetOf<Uri>() }
    var showGuestSignInDialog by remember { mutableStateOf(false) }
    val analytics = remember { com.rp.dedup.core.analytics.AnalyticsManager.getInstance(context) }
    LaunchedEffect(Unit) { analytics.logScreenView("SocialMediaCleaner") }

    Scaffold(
        topBar = {
            DeDupTopBar(
                title = stringResource(R.string.screen_social_media_cleaner),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        bottomBar = {
            val results = state as? SocialMediaCleanerState.Results
            if (results != null && selectedUris.isNotEmpty()) {
                Surface(shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.delete_selected_btn, selectedUris.size, ""),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = {
                                if (profileViewModel.isGuest) showGuestSignInDialog = true
                                else {
                                    viewModel.deleteFiles(selectedUris.toList())
                                    selectedUris.clear()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.delete_selected_btn, selectedUris.size, Constants.EMPTY_STRING).split(" (")[0]) // Temporary hack to reuse string
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                is SocialMediaCleanerState.Idle -> IdleView(onScan = viewModel::startScan)
                is SocialMediaCleanerState.ScanningFiles -> ScanProgressView(
                    phase = stringResource(R.string.scanning),
                    progress = null,
                    detail = "Found ${s.found} files"
                )
                is SocialMediaCleanerState.ComputingChecksums -> ScanProgressView(
                    phase = "Computing Checksums",
                    progress = s.progress,
                    detail = "${(s.progress * 100).toInt()}%"
                )
                is SocialMediaCleanerState.Results -> ResultsContent(
                    groups = s.duplicateGroups,
                    totalReclaimable = s.reclaimableBytes,
                    selectedUris = selectedUris,
                    onToggle = { uri ->
                        if (selectedUris.contains(uri)) selectedUris.remove(uri)
                        else selectedUris.add(uri)
                    },
                    onAutoSelect = {
                        val toSelect = s.duplicateGroups.flatMap { it.drop(1) }.map { it.uri }
                        selectedUris.addAll(toSelect)
                    }
                )
                is SocialMediaCleanerState.Error -> ErrorView(
                    message = s.message,
                    onRetry = viewModel::startScan
                )
            }
        }
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
}

@Composable
private fun IdleView(onScan: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Chat,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Color(0xFF25D366)
        )
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.scan_social_media), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.social_media_cleaner_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onScan, shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.start_scan))
        }
    }
}

@Composable
private fun ScanProgressView(phase: String, progress: Float?, detail: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (progress != null) {
            CircularProgressIndicator(progress = { progress })
        } else {
            CircularProgressIndicator()
        }
        Spacer(Modifier.height(24.dp))
        Text(phase, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ResultsContent(
    groups: List<List<SocialMediaFile>>,
    totalReclaimable: Long,
    selectedUris: Set<Uri>,
    onToggle: (Uri) -> Unit,
    onAutoSelect: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SummaryBanner(
            groupCount = groups.size,
            reclaimableBytes = totalReclaimable,
            onAutoSelect = onAutoSelect
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(groups) { group ->
                DuplicateGroupCard(group, selectedUris, onToggle)
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun SummaryBanner(groupCount: Int, reclaimableBytes: Long, onAutoSelect: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.duplicate_groups_found, groupCount),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.storage_summary_reclaimable, reclaimableBytes.toReadableSize()),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(onClick = onAutoSelect, shape = RoundedCornerShape(8.dp)) {
                Text(stringResource(R.string.auto_select))
            }
        }
    }
}

@Composable
private fun DuplicateGroupCard(
    files: List<SocialMediaFile>,
    selectedUris: Set<Uri>,
    onToggle: (Uri) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.group_files_count, files.size),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            files.forEachIndexed { index, file ->
                DuplicateFileItem(
                    file = file,
                    isKeep = index == 0,
                    isSelected = selectedUris.contains(file.uri),
                    onToggle = { onToggle(file.uri) }
                )
                if (index < files.size - 1) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun DuplicateFileItem(
    file: SocialMediaFile,
    isKeep: Boolean,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = file.uri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                file.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(file.size.toReadableSize(), style = MaterialTheme.typography.bodySmall)
                if (isKeep) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = Color(0xFF2E7D32).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            stringResource(R.string.keep),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.ErrorOutline, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Text(message, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

private fun Long.toReadableSize(): String {
    val kb = this / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> "%.2f GB".format(gb)
        mb >= 1.0 -> "%.2f MB".format(mb)
        kb >= 1.0 -> "%.2f KB".format(kb)
        else -> "$this B"
    }
}

@Preview(showBackground = true)
@Composable
private fun SocialMediaCleanerScreenPreview() {
    DeDupTheme {
        SocialMediaCleanerScreen(navController = NavHostController(LocalContext.current))
    }
}
