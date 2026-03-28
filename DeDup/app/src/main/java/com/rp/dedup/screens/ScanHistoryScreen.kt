package com.rp.dedup.screens

import android.text.format.Formatter
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.rp.dedup.core.db.AppDatabase
import com.rp.dedup.core.data.ScanHistory
import com.rp.dedup.core.repository.ScanHistoryRepository
import com.rp.dedup.core.viewmodels.ScanHistoryViewModel
import com.rp.dedup.ui.theme.PrimaryBlue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanHistoryScreen(navController: NavHostController) {
    val context = LocalContext.current
    val viewModel: ScanHistoryViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ScanHistoryViewModel(
                    ScanHistoryRepository(AppDatabase.getDatabase(context).scanHistoryDao())
                ) as T
            }
        }
    )

    val history by viewModel.history.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(Color(0xFF060D1F), Color(0xFF0D2347))))
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Scan History",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        if (history.isNotEmpty()) {
                            IconButton(onClick = { showClearDialog = true }) {
                                Icon(
                                    Icons.Default.DeleteSweep,
                                    contentDescription = "Clear history",
                                    tint = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { paddingValues ->
            if (history.isEmpty()) {
                EmptyHistoryState(modifier = Modifier.padding(paddingValues))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(Modifier.height(4.dp)) }

                    item { SummaryCard(history) }

                    item {
                        Text(
                            text = "RECENT SCANS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White.copy(alpha = 0.35f),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            ),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                        )
                    }

                    items(history, key = { it.id }) { scan ->
                        ScanHistoryCard(
                            scan = scan,
                            onDelete = { viewModel.delete(scan) }
                        )
                    }

                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = Color(0xFF0D1B3E),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.7f),
            title = { Text("Clear All History") },
            text = { Text("This will permanently delete all scan records.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll()
                    showClearDialog = false
                }) {
                    Text("Clear", color = Color(0xFFEA4335))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.5f))
                }
            }
        )
    }
}

// — Summary card showing aggregate stats —

@Composable
private fun SummaryCard(history: List<ScanHistory>) {
    val totalScans = history.size
    val totalDuplicates = history.sumOf { it.totalDuplicates }
    val totalReclaimable = history.sumOf { it.reclaimableBytes }
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = PrimaryBlue.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SummaryStatColumn(
                value = totalScans.toString(),
                label = "Total Scans",
                color = PrimaryBlue
            )
            VerticalDivider(
                modifier = Modifier.height(48.dp),
                color = Color.White.copy(alpha = 0.1f)
            )
            SummaryStatColumn(
                value = totalDuplicates.toString(),
                label = "Duplicates",
                color = Color(0xFFFBC02D)
            )
            VerticalDivider(
                modifier = Modifier.height(48.dp),
                color = Color.White.copy(alpha = 0.1f)
            )
            SummaryStatColumn(
                value = if (totalReclaimable > 0)
                    Formatter.formatShortFileSize(context, totalReclaimable)
                else "—",
                label = "Reclaimable",
                color = Color(0xFF4DB6AC)
            )
        }
    }
}

@Composable
private fun SummaryStatColumn(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = color
            )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = Color.White.copy(alpha = 0.5f)
            )
        )
    }
}

// — Individual scan history card —

@Composable
private fun ScanHistoryCard(scan: ScanHistory, onDelete: () -> Unit) {
    val (icon, iconColor, label) = scanTypeDisplay(scan.scanType)
    val statusColor = if (scan.status == "COMPLETED") Color(0xFF4CAF50) else Color(0xFFFF9800)
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row: icon + label + status badge + delete button
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = iconColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    )
                    Text(
                        text = formatTimestamp(scan.timestamp),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.45f)
                        )
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = scan.status,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = statusColor,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Delete",
                        tint = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
            Spacer(Modifier.height(12.dp))

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatChip(
                    icon = Icons.Default.FolderOpen,
                    value = "${scan.totalScanned}",
                    label = "scanned"
                )
                StatChip(
                    icon = Icons.Default.ContentCopy,
                    value = "${scan.duplicateGroups}",
                    label = "groups"
                )
                StatChip(
                    icon = Icons.Default.FileCopy,
                    value = "${scan.totalDuplicates}",
                    label = "dupes"
                )
                StatChip(
                    icon = Icons.Default.Savings,
                    value = if (scan.reclaimableBytes > 0)
                        Formatter.formatShortFileSize(context, scan.reclaimableBytes)
                    else "—",
                    label = "saveable"
                )
            }

            Spacer(Modifier.height(10.dp))

            // Duration footer
            Text(
                text = "Duration: ${formatDuration(scan.durationMs)}",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.White.copy(alpha = 0.3f)
                )
            )
        }
    }
}

@Composable
private fun StatChip(icon: ImageVector, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 10.sp
            )
        )
    }
}

// — Empty state —

@Composable
private fun EmptyHistoryState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.05f),
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.ManageSearch,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "No scan history yet",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = Color.White.copy(alpha = 0.6f),
                    fontWeight = FontWeight.SemiBold
                )
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Run a scan to start tracking results",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.3f)
                )
            )
        }
    }
}

// — Helpers —

private data class ScanTypeDisplay(
    val icon: ImageVector,
    val color: Color,
    val label: String
)

private fun scanTypeDisplay(scanType: String): ScanTypeDisplay = when (scanType) {
    "IMAGE"    -> ScanTypeDisplay(Icons.Default.Image,          Color(0xFF4285F4), "Photos Scan")
    "VIDEO"    -> ScanTypeDisplay(Icons.Default.Videocam,       Color(0xFFEA4335), "Videos Scan")
    "FILE_PDF" -> ScanTypeDisplay(Icons.Default.PictureAsPdf,   Color(0xFFFBC02D), "Documents Scan")
    "FILE_APK" -> ScanTypeDisplay(Icons.Default.Android,        Color(0xFF34A853), "APKs Scan")
    else       -> ScanTypeDisplay(Icons.Default.FolderOpen,     Color(0xFF9E9E9E), "File Scan")
}

private fun formatTimestamp(epochMillis: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault())
    return sdf.format(Date(epochMillis))
}

private fun formatDuration(ms: Long): String {
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    return when {
        minutes > 0  -> "${minutes}m ${seconds % 60}s"
        seconds > 0  -> "${seconds}s"
        else         -> "${ms}ms"
    }
}
