package com.rp.dedup.screens

import android.text.format.Formatter
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.rp.dedup.LocalDrawerState
import com.rp.dedup.Screen
import com.rp.dedup.core.repository.FileScannerRepository
import com.rp.dedup.core.viewmodels.CleanupViewModel
import com.rp.dedup.ui.theme.DeDupTheme
import kotlinx.coroutines.launch

private data class LargeFileItemLocal(
    val title: String,
    val subtitle: String,
    val sizeBytes: Long,      // used for filtering
    val sizeLabel: String,    // displayed value (may be a count or size string)
    val icon: ImageVector,
    val iconBg: Color,
    val iconTint: Color,
    val isCountType: Boolean = false
)

private val MB = 1024L * 1024L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileCleanupScreen(navController: NavHostController) {
    val context = LocalContext.current
    val cleanupViewModel: CleanupViewModel = viewModel(
        factory = CleanupViewModel.Factory(FileScannerRepository(context))
    )
    val cleanupState by cleanupViewModel.uiState.collectAsState()

    val drawerState = LocalDrawerState.current
    val scope = rememberCoroutineScope()
    var selectedFilter by remember { mutableStateOf(">100MB") }

    val sizeFilters = listOf(
        ">50MB"  to  50L * MB,
        ">100MB" to 100L * MB,
        ">200MB" to 200L * MB
    )

    val largeFileItems = listOf(
        LargeFileItemLocal(
            title = "Unused Video Assets",
            subtitle = if (cleanupState.videoStats.isLoading) "Scanning..." else "${cleanupState.videoStats.count} high-res recordings found",
            sizeBytes = cleanupState.videoStats.totalSize,
            sizeLabel = Formatter.formatShortFileSize(context, cleanupState.videoStats.totalSize),
            icon = Icons.Default.VideoLibrary,
            iconBg = Color(0xFFB2EBF2),
            iconTint = Color(0xFF006064)
        ),
        LargeFileItemLocal(
            title = "Obsolete Archives",
            subtitle = if (cleanupState.archiveStats.isLoading) "Scanning..." else "${cleanupState.archiveStats.count} ZIP & RAR files found",
            sizeBytes = cleanupState.archiveStats.totalSize,
            sizeLabel = Formatter.formatShortFileSize(context, cleanupState.archiveStats.totalSize),
            icon = Icons.Default.Archive,
            iconBg = Color(0xFFEDE7F6),
            iconTint = Color(0xFF512DA8)
        ),
        LargeFileItemLocal(
            title = "Large App Downloads",
            subtitle = if (cleanupState.appDownloadStats.isLoading) "Scanning..." else "${cleanupState.appDownloadStats.count} APKs and OBBs found",
            sizeBytes = cleanupState.appDownloadStats.totalSize,
            sizeLabel = Formatter.formatShortFileSize(context, cleanupState.appDownloadStats.totalSize),
            icon = Icons.Default.Android,
            iconBg = Color(0xFFE8F5E9),
            iconTint = Color(0xFF2E7D32)
        )
    )

    val minBytes = sizeFilters.firstOrNull { it.first == selectedFilter }?.second ?: 0L
    val filteredLargeFiles = largeFileItems.filter { it.sizeBytes >= minBytes }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "DeDuplicator",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
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
                                contentDescription = "Profile",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
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
                    "File Cleanup",
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
                            "SCAN TO FIND REDUNDANT FILES",
                            color = MaterialTheme.colorScheme.onSecondary,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Manage documents & APKs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            // Scanner Categories
            item {
                Text(
                    "Scanner Categories",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CategoryCard(
                        title = "PDFs",
                        icon = Icons.Default.Description,
                        color = Color(0xFFE53935),
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate(Screen.FileScanner.createRoute("pdf")) }
                    )
                    CategoryCard(
                        title = "APKs",
                        icon = Icons.Default.Android,
                        color = Color(0xFF43A047),
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate(Screen.FileScanner.createRoute("apk")) }
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            // Large File Finder Section
            item {
                Text(
                    "Large File Finder",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
                Text(
                    "Target massive storage consumers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sizeFilters.forEach { (label, _) ->
                        SizeFilterChip(
                            text = label,
                            isSelected = selectedFilter == label,
                            onClick = { selectedFilter = label }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            items(filteredLargeFiles) { file ->
                LargeFileCard(
                    title = file.title,
                    subtitle = file.subtitle,
                    size = file.sizeLabel,
                    icon = file.icon,
                    iconBg = file.iconBg,
                    iconTint = file.iconTint,
                    isCountType = file.isCountType,
                    onClick = {
                        if (file.title == "Large App Downloads") {
                            navController.navigate(Screen.FileScanner.createRoute("apk"))
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            item {
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
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

@Composable
fun SizeFilterChip(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .height(32.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(
                text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
fun LargeFileCard(
    title: String,
    subtitle: String,
    size: String,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    isCountType: Boolean = false,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
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
    }
}


@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
fun FileCleanupScreenPreview() {
    DeDupTheme {
        FileCleanupScreen(rememberNavController())
    }
}
