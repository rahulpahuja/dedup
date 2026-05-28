package com.rp.dedup.screens

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Scanner
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.rp.dedup.core.data.ScanHistory
import com.rp.dedup.core.db.AppDatabase
import com.rp.dedup.core.repository.ScanHistoryRepository
import com.rp.dedup.core.viewmodels.ScanHistoryViewModel
import com.rp.dedup.ui.theme.DarkBlue
import com.rp.dedup.ui.theme.DeDupTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import com.rp.dedup.core.ui.DeDupTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLogScreen(navController: NavHostController) {
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
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val weekStart = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
    val weeklyReclaimable = history.filter { it.timestamp >= weekStart }.sumOf { it.reclaimableBytes }
    val weeklyScans = history.count { it.timestamp >= weekStart }

    Scaffold(
        topBar = {
            DeDupTopBar(
                title = "Activity Log",
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        bottomBar = { BottomNavigationBar(navController) }
    ) { paddingValues ->
        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Scanner,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outlineVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No activity yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Run a scan to see results here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (isDark) {
                        Text(
                            "WEEKLY SUMMARY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                Formatter.formatShortFileSize(context, weeklyReclaimable),
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Reclaimable · $weeklyScans scans",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    } else {
                        Text(
                            "RECENT ACTIVITY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00838F)
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Scan History",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = DarkBlue
                                )
                            )
                            Text(
                                "${history.size} Records",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                items(history) { scan ->
                    ScanActivityItem(scan = scan, isDark = isDark, context = context)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                item { Spacer(modifier = Modifier.height(100.dp)) }
            }
        }
    }
}

@Composable
private fun ScanActivityItem(scan: ScanHistory, isDark: Boolean, context: Context) {
    val icon = scanTypeIcon(scan.scanType)
    val label = scanTypeLabel(scan.scanType)
    val timeAgo = formatTimeAgo(scan.timestamp)
    val reclaimable = Formatter.formatShortFileSize(context, scan.reclaimableBytes)
    val description = buildString {
        append("Scanned ${scan.totalScanned} files · ${scan.totalDuplicates} duplicates found")
        if (scan.reclaimableBytes > 0) append(" · $reclaimable reclaimable")
        if (scan.status == "CANCELLED") append(" · Cancelled")
    }
    val iconColor = when (scan.scanType) {
        "IMAGE"    -> Color(0xFF4285F4)
        "VIDEO"    -> Color(0xFFEA4335)
        "FILE_PDF" -> Color(0xFFFF5722)
        "FILE_APK" -> Color(0xFF34A853)
        else       -> Color(0xFF7986CB)
    }

    if (isDark) {
        CompactActivityItem(
            icon = icon,
            title = label,
            description = description,
            time = timeAgo,
            iconColor = iconColor
        )
    } else {
        ActivityListItem(
            icon = icon,
            title = label,
            description = description,
            time = timeAgo,
            iconTint = iconColor
        )
    }
}

private fun scanTypeIcon(type: String): ImageVector = when (type) {
    "IMAGE"    -> Icons.Default.PhotoLibrary
    "VIDEO"    -> Icons.Default.Videocam
    "FILE_PDF" -> Icons.Default.Description
    "FILE_APK" -> Icons.Default.Android
    else       -> Icons.Default.Scanner
}

private fun scanTypeLabel(type: String): String = when (type) {
    "IMAGE"    -> "Photos Scan"
    "VIDEO"    -> "Videos Scan"
    "FILE_PDF" -> "Documents Scan"
    "FILE_APK" -> "APKs Scan"
    else       -> "File Scan"
}

private fun formatTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        hours < 1  -> "JUST NOW"
        hours < 24 -> "${hours}H AGO"
        days == 1L -> "YESTERDAY"
        days < 7   -> "${days}D AGO"
        else       -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}

@Composable
fun CompactActivityItem(
    icon: ImageVector,
    title: String,
    description: String,
    time: String,
    iconColor: Color
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = iconColor)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ActivityListItem(
    icon: ImageVector,
    title: String,
    description: String,
    time: String,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    actions: List<String>? = null,
    buttonText: String? = null,
    buttonColor: Color = MaterialTheme.colorScheme.primary
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    time,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (actions != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    actions.forEach { action ->
                        TextButton(
                            onClick = { },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                action,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (action == "Dismiss") MaterialTheme.colorScheme.onSurfaceVariant
                                            else MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }
            }

            if (buttonText != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { },
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        Icons.Default.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        buttonText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ActivityLogScreenPreview() {
    DeDupTheme(darkTheme = false) {
        ActivityLogScreen(rememberNavController())
    }
}

@Preview(showBackground = true)
@Composable
fun ActivityLogScreenDarkPreview() {
    DeDupTheme(darkTheme = true) {
        ActivityLogScreen(rememberNavController())
    }
}
