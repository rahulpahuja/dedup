package com.rp.dedup.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.rp.dedup.core.model.SocialApp
import com.rp.dedup.core.model.SocialMediaFile
import com.rp.dedup.core.model.SocialMediaType
import com.rp.dedup.core.model.SocialMediaCleanerState
import com.rp.dedup.core.viewmodels.SocialMediaCleanerViewModel
import com.rp.dedup.ui.theme.DeDupTheme
import com.rp.dedup.core.ui.DeDupTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialMediaCleanerScreen(navController: NavHostController) {
    val context = LocalContext.current
    val viewModel: SocialMediaCleanerViewModel = viewModel(
        factory = SocialMediaCleanerViewModel.factory(context)
    )
    val state by viewModel.state.collectAsState()
    val selectedUris = remember { mutableStateSetOf<android.net.Uri>() }

    Scaffold(
        topBar = {
            DeDupTopBar(
                title = "Social Media Cleaner",
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                            "${selectedUris.size} selected · ${selectedUris
                                .mapNotNull { uri -> results.duplicateGroups.flatten().find { it.uri == uri } }
                                .sumOf { it.size }.toReadableSize()} reclaimable",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = {
                                viewModel.deleteFiles(selectedUris.toList())
                                selectedUris.clear()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Delete Selected")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                is SocialMediaCleanerState.Idle -> IdleView(onScan = { viewModel.startScan() })
                is SocialMediaCleanerState.ScanningFiles -> ScanProgressView(
                    label = "Scanning media files...",
                    progress = null,
                    detail = "${s.found} files found"
                )
                is SocialMediaCleanerState.ComputingChecksums -> ScanProgressView(
                    label = "Computing checksums...",
                    progress = s.progress,
                    detail = "${(s.progress * 100).toInt()}% complete"
                )
                is SocialMediaCleanerState.Results -> ResultsContent(
                    groups = s.duplicateGroups,
                    reclaimableBytes = s.reclaimableBytes,
                    selectedUris = selectedUris,
                    onToggle = { uri ->
                        if (uri in selectedUris) selectedUris.remove(uri) else selectedUris.add(uri)
                    },
                    onRescan = { viewModel.startScan() }
                )
                is SocialMediaCleanerState.Error -> ErrorView(
                    message = s.message,
                    onRetry = { viewModel.startScan() }
                )
            }
        }
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
        Text("Scan WhatsApp & Telegram", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Find duplicate media files inside social app folders.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onScan, shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Start Scan")
        }
    }
}

@Composable
private fun ScanProgressView(label: String, progress: Float?, detail: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(24.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(0.6f).clip(CircleShape)
            )
            Spacer(Modifier.height(8.dp))
        }
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ResultsContent(
    groups: List<List<SocialMediaFile>>,
    reclaimableBytes: Long,
    selectedUris: Set<android.net.Uri>,
    onToggle: (android.net.Uri) -> Unit,
    onRescan: () -> Unit
) {
    if (groups.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = Color(0xFF34A853)
            )
            Spacer(Modifier.height(16.dp))
            Text("No duplicates found!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Your WhatsApp and Telegram folders are clean.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SummaryBanner(
                groupCount = groups.size,
                reclaimableBytes = reclaimableBytes,
                onRescan = onRescan
            )
        }
        itemsIndexed(groups) { _, group ->
            DuplicateGroupCard(group = group, selectedUris = selectedUris, onToggle = onToggle)
        }
    }
}

@Composable
private fun SummaryBanner(groupCount: Int, reclaimableBytes: Long, onRescan: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "$groupCount duplicate group${if (groupCount != 1) "s" else ""} found",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${reclaimableBytes.toReadableSize()} reclaimable",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            TextButton(onClick = onRescan) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Rescan")
            }
        }
    }
}

@Composable
private fun DuplicateGroupCard(
    group: List<SocialMediaFile>,
    selectedUris: Set<android.net.Uri>,
    onToggle: (android.net.Uri) -> Unit
) {
    val appColor = if (group.first().app == SocialApp.WHATSAPP) Color(0xFF25D366) else Color(0xFF2CA5E0)

    Card(shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(appColor, CircleShape)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    group.first().app.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = appColor,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "· ${group.first().mediaType.displayName} · ${group.first().size.toReadableSize()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(group) { index, file ->
                    DuplicateFileItem(
                        file = file,
                        isFirst = index == 0,
                        isSelected = file.uri in selectedUris,
                        onToggle = { onToggle(file.uri) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DuplicateFileItem(
    file: SocialMediaFile,
    isFirst: Boolean,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(90.dp)
            .clickable(enabled = !isFirst) { onToggle() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            if (file.mediaType == SocialMediaType.IMAGE || file.mediaType == SocialMediaType.VIDEO) {
                AsyncImage(
                    model = file.uri,
                    contentDescription = null,
                    modifier = Modifier
                        .size(90.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            width = if (isSelected) 2.dp else 0.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.error else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.InsertDriveFile, contentDescription = null, modifier = Modifier.size(32.dp))
                }
            }

            // KEEP / SELECT badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .background(
                        if (isFirst) Color(0xFF34A853) else if (isSelected) MaterialTheme.colorScheme.error else Color.Black.copy(alpha = 0.5f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    if (isFirst) "KEEP" else if (isSelected) "✓" else "DEL",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            file.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.ErrorOutline, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

private fun Long.toReadableSize(): String = when {
    this < 1_024 -> "$this B"
    this < 1_048_576 -> "${this / 1_024} KB"
    this < 1_073_741_824 -> "${this / 1_048_576} MB"
    else -> "${"%.1f".format(this.toDouble() / 1_073_741_824)} GB"
}

@Preview(showBackground = true)
@Composable
private fun SocialMediaCleanerScreenPreview() {
    DeDupTheme {
        val navController = rememberNavController()
        SocialMediaCleanerScreen(navController = navController)
    }
}
