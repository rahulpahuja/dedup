package com.rp.dedup.screens.dashboard.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.rp.dedup.R
import com.rp.dedup.Screen
import com.rp.dedup.UIConstants
import com.rp.dedup.core.model.MediaCounts

@Composable
fun QuickScanGrid(
    navController: NavHostController,
    mediaCounts: MediaCounts = MediaCounts(),
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanCategoryCard(
                title = stringResource(R.string.quick_scan_images),
                count = formatCount(mediaCounts.images),
                icon = Icons.Default.Image,
                color = UIConstants.ColorImages,
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate(Screen.ImageScanner.route) }
            )
            ScanCategoryCard(
                title = stringResource(R.string.quick_scan_videos),
                count = formatCount(mediaCounts.videos),
                icon = Icons.Default.Videocam,
                color = UIConstants.ColorVideos,
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate(Screen.VideoScanner.route) }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanCategoryCard(
                title = stringResource(R.string.quick_scan_documents),
                count = formatCount(mediaCounts.pdfs),
                icon = Icons.Default.Description,
                color = UIConstants.ColorDocuments,
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate(Screen.FileScanner.createRoute("pdf")) }
            )
            ScanCategoryCard(
                title = stringResource(R.string.quick_scan_apks),
                count = formatCount(mediaCounts.apks),
                icon = Icons.Default.Android,
                color = UIConstants.ColorApks,
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate(Screen.FileScanner.createRoute("apk")) }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanCategoryCard(
                title = stringResource(R.string.quick_scan_browse_files),
                count = "All",
                icon = Icons.Default.FolderOpen,
                color = UIConstants.ColorBrowseFiles,
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate(Screen.FileBrowser.route) }
            )
            ScanCategoryCard(
                title = stringResource(R.string.quick_scan_history),
                count = "Log",
                icon = Icons.Default.History,
                color = UIConstants.ColorScanHistory,
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate(Screen.ScanHistory.route) }
            )
        }
    }
}

@Composable
fun ScanCategoryCard(
    title: String,
    count: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = 0.1f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                count,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatCount(n: Int): String = when {
    n <= 0    -> "–"
    n >= 1_000_000 -> "${"%.1f".format(n / 1_000_000.0)}M"
    n >= 1_000     -> "${"%.1f".format(n / 1_000.0)}k"
    else -> n.toString()
}
