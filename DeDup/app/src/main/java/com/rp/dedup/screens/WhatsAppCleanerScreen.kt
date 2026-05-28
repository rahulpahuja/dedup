package com.rp.dedup.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.rp.dedup.core.data.*
import com.rp.dedup.core.viewmodels.WhatsAppCleanerState
import com.rp.dedup.core.viewmodels.WhatsAppCleanerViewModel
import com.rp.dedup.ui.theme.DeDupTheme
import com.rp.dedup.core.ui.DeDupTopBar

private val WaGreen = Color(0xFF25D366)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAppCleanerScreen(navController: NavHostController) {
    val context = LocalContext.current
    val viewModel: WhatsAppCleanerViewModel =
        viewModel(factory = WhatsAppCleanerViewModel.factory(context))
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            DeDupTopBar(
                title = {
                    Text(
                        "WhatsApp Cleaner",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when (val s = state) {
            is WhatsAppCleanerState.Idle ->
                WaIdleView(modifier = Modifier.padding(padding), onScan = viewModel::startScan)
            is WhatsAppCleanerState.Scanning ->
                WaScanningView(modifier = Modifier.padding(padding), phase = s.phase)
            is WhatsAppCleanerState.Results ->
                WaResultsView(
                    modifier = Modifier.padding(padding),
                    data     = s.data,
                    onDelete = viewModel::deleteFiles
                )
            is WhatsAppCleanerState.Error ->
                WaErrorView(
                    modifier = Modifier.padding(padding),
                    message  = s.message,
                    onRetry  = viewModel::startScan
                )
        }
    }
}

// ─── Idle ─────────────────────────────────────────────────────────────────────

@Composable
private fun WaIdleView(modifier: Modifier, onScan: () -> Unit) {
    Column(
        modifier          = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = WaGreen
        )
        Spacer(Modifier.height(24.dp))
        Text("Scan WhatsApp Storage", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Find duplicate media, stale statuses, large files, and forwarded content sent multiple times.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onScan,
            colors = ButtonDefaults.buttonColors(containerColor = WaGreen),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(0.6f).height(48.dp)
        ) {
            Text("Start Scan", style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ─── Scanning ─────────────────────────────────────────────────────────────────

@Composable
private fun WaScanningView(modifier: Modifier, phase: String) {
    Column(
        modifier          = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = WaGreen, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(20.dp))
        Text(phase, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ─── Error ────────────────────────────────────────────────────────────────────

@Composable
private fun WaErrorView(modifier: Modifier, message: String, onRetry: () -> Unit) {
    Column(
        modifier          = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Scan failed", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

// ─── Results ──────────────────────────────────────────────────────────────────

@Composable
private fun WaResultsView(
    modifier: Modifier,
    data: WhatsAppScanResult,
    onDelete: (List<android.net.Uri>) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var selected    by remember { mutableStateOf(setOf<android.net.Uri>()) }

    // Clear selection when switching tabs
    LaunchedEffect(selectedTab) { selected = emptySet() }

    val tabs = listOf(
        WhatsAppCleanerTab.DUPLICATE_MEDIA    to data.duplicateMedia.size,
        WhatsAppCleanerTab.DUPLICATE_STATUSES to data.duplicateStatuses.size,
        WhatsAppCleanerTab.DUPLICATE_DOCS     to data.duplicateDocs.size,
        WhatsAppCleanerTab.LARGE_FILES        to data.largeFiles.size,
        WhatsAppCleanerTab.SENT_RECEIVED      to data.sentReceivedMatches.size
    )

    Column(modifier = modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor   = MaterialTheme.colorScheme.surface,
            edgePadding      = 0.dp
        ) {
            tabs.forEachIndexed { index, (tab, count) ->
                Tab(
                    selected = selectedTab == index,
                    onClick  = { selectedTab = index },
                    text     = {
                        Text(
                            "${tab.label} ($count)",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> DuplicateGroupList(
                    groups   = data.duplicateMedia,
                    selected = selected,
                    onToggle = { uri -> selected = if (uri in selected) selected - uri else selected + uri }
                )
                1 -> DuplicateGroupList(
                    groups   = data.duplicateStatuses,
                    selected = selected,
                    onToggle = { uri -> selected = if (uri in selected) selected - uri else selected + uri }
                )
                2 -> DuplicateGroupList(
                    groups   = data.duplicateDocs,
                    selected = selected,
                    onToggle = { uri -> selected = if (uri in selected) selected - uri else selected + uri }
                )
                3 -> LargeFileList(
                    files    = data.largeFiles,
                    selected = selected,
                    onToggle = { uri -> selected = if (uri in selected) selected - uri else selected + uri }
                )
                4 -> SentReceivedList(
                    matches  = data.sentReceivedMatches,
                    selected = selected,
                    onToggle = { uri -> selected = if (uri in selected) selected - uri else selected + uri }
                )
            }
        }

        if (selected.isNotEmpty()) {
            Surface(shadowElevation = 8.dp) {
                Button(
                    onClick = {
                        onDelete(selected.toList())
                        selected = emptySet()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape  = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Delete Selected (${selected.size})", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

// ─── Duplicate group list ─────────────────────────────────────────────────────

@Composable
private fun DuplicateGroupList(
    groups: List<List<WhatsAppFile>>,
    selected: Set<android.net.Uri>,
    onToggle: (android.net.Uri) -> Unit
) {
    if (groups.isEmpty()) {
        EmptyTabMessage("No duplicates found")
        return
    }
    LazyColumn(
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(groups) { group ->
            DuplicateGroupCard(group, selected, onToggle)
        }
    }
}

@Composable
private fun DuplicateGroupCard(
    group: List<WhatsAppFile>,
    selected: Set<android.net.Uri>,
    onToggle: (android.net.Uri) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "${group.size} copies  •  ${formatSize(group.sumOf { it.size })} total",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            group.forEachIndexed { idx, file ->
                WaFileRow(
                    file     = file,
                    keepBadge = idx == 0,
                    checked  = if (idx == 0) false else file.uri in selected,
                    onToggle = if (idx == 0) null else { { onToggle(file.uri) } }
                )
                if (idx < group.lastIndex) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

// ─── Large file list ──────────────────────────────────────────────────────────

@Composable
private fun LargeFileList(
    files: List<WhatsAppFile>,
    selected: Set<android.net.Uri>,
    onToggle: (android.net.Uri) -> Unit
) {
    if (files.isEmpty()) {
        EmptyTabMessage("No large files found")
        return
    }
    LazyColumn(
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(files) { file ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                WaFileRow(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    file     = file,
                    keepBadge = false,
                    checked  = file.uri in selected,
                    onToggle = { onToggle(file.uri) }
                )
            }
        }
    }
}

// ─── Sent & Received list ─────────────────────────────────────────────────────

@Composable
private fun SentReceivedList(
    matches: List<SentReceivedMatch>,
    selected: Set<android.net.Uri>,
    onToggle: (android.net.Uri) -> Unit
) {
    if (matches.isEmpty()) {
        EmptyTabMessage("No sent/received duplicates found")
        return
    }
    LazyColumn(
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(matches) { match ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Identical file sent & received",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Badge(containerColor = Color(0xFF1A73E8)) { Text("KEEP", style = MaterialTheme.typography.labelSmall) }
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(match.received.name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            Text("Received  •  ${formatSize(match.received.size)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    WaFileRow(
                        file      = match.sent,
                        keepBadge = false,
                        checked   = match.sent.uri in selected,
                        onToggle  = { onToggle(match.sent.uri) },
                        labelOverride = "Sent"
                    )
                }
            }
        }
    }
}

// ─── Shared composables ───────────────────────────────────────────────────────

@Composable
private fun WaFileRow(
    modifier: Modifier = Modifier,
    file: WhatsAppFile,
    keepBadge: Boolean,
    checked: Boolean,
    onToggle: (() -> Unit)?,
    labelOverride: String? = null
) {
    Row(
        modifier          = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onToggle != null) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
        } else if (keepBadge) {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Badge(containerColor = WaGreen) { Text("KEEP", style = MaterialTheme.typography.labelSmall) }
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(file.name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            Text(
                "${labelOverride ?: file.folder.label}  •  ${formatSize(file.size)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyTabMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L     -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024L         -> "%.1f KB".format(bytes / 1_024.0)
    else                    -> "$bytes B"
}

@Preview(showBackground = true)
@Composable
private fun WhatsAppCleanerScreenPreview() {
    DeDupTheme {
        val navController = rememberNavController()
        WhatsAppCleanerScreen(navController = navController)
    }
}
