package com.rp.dedup.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.rp.dedup.LocalDrawerState
import com.rp.dedup.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileCleanupScreen(navController: NavHostController) {
    val drawerState = LocalDrawerState.current
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "DeDuplicator",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    IconButton(onClick = { }) {
                        Surface(
                            shape = CircleShape,
                            modifier = Modifier.size(32.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        ) {
                            Icon(Icons.Default.Person, contentDescription = "Profile", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Column {
                DeleteSelectionBar()
                BottomNavigationBar(navController)
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
                    "File Cleanup",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "1.4 GB REDUNDANT FILES",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Ready to be reclaimed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        color = if (MaterialTheme.colorScheme.onBackground == Color.White) Color.White else Color(0xFF1A237E)
                    )
                )
                Text(
                    "Target massive storage consumers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(">50MB", isSelected = false)
                    FilterChip(">100MB", isSelected = true)
                    FilterChip(">500MB", isSelected = false)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                LargeFileCard(
                    title = "Unused Video Assets",
                    subtitle = "4 high-resolution recordings from June",
                    size = "842MB",
                    icon = Icons.Default.VideoLibrary,
                    iconBg = Color(0xFFB2EBF2),
                    iconTint = Color(0xFF006064)
                )
                Spacer(modifier = Modifier.height(16.dp))
                LargeFileCard(
                    title = "Obsolete Archives",
                    size = "12",
                    subtitle = "Obsolete Archives",
                    icon = Icons.Default.Archive,
                    iconBg = MaterialTheme.colorScheme.secondaryContainer,
                    iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                    isCountType = true
                )
                Spacer(modifier = Modifier.height(32.dp))
            }

            // Redundant Downloads Section
            item {
                Text(
                    "Redundant Downloads",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (MaterialTheme.colorScheme.onBackground == Color.White) Color.White else Color(0xFF1A237E)
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                DownloadItem(
                    name = "Invoice_2023_Final.pdf",
                    info = "2.4 MB  •  Downloaded 3 times",
                    badge = "HASH MATCH",
                    badgeColor = Color(0xFFB2EBF2),
                    badgeTextColor = Color(0xFF006064),
                    selectionInfo = "SELECTED\n2 Copies",
                    icon = Icons.Default.PictureAsPdf,
                    iconTint = Color(0xFFE53935)
                )
                Spacer(modifier = Modifier.height(12.dp))
                DownloadItem(
                    name = "Beta_Update_v2.4.apk",
                    info = "158 MB  •  Stored in Downloads/Old",
                    badge = "HASH MATCH",
                    badgeColor = Color(0xFFB2EBF2),
                    badgeTextColor = Color(0xFF006064),
                    selectionInfo = "SELECTED\n1 Cop",
                    icon = Icons.Default.Android,
                    iconTint = Color(0xFF43A047)
                )
                Spacer(modifier = Modifier.height(12.dp))
                DownloadItem(
                    name = "Annual_Report_Draft.docx",
                    info = "450 KB  •  Created 2 days ago",
                    badge = "PARTIAL MATCH",
                    badgeColor = MaterialTheme.colorScheme.outlineVariant,
                    badgeTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectionInfo = "Pe",
                    icon = Icons.Default.Description,
                    iconTint = Color(0xFF1E88E5)
                )
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

@Composable
fun FilterChip(text: String, isSelected: Boolean) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.height(32.dp)
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
    isCountType: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(28.dp))
                    }
                }
                if (!isCountType) {
                    Text(
                        size,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (MaterialTheme.colorScheme.onBackground == Color.White) Color.White else Color(0xFF1A237E)
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
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DownloadItem(
    name: String,
    info: String,
    badge: String,
    badgeColor: Color,
    badgeTextColor: Color,
    selectionInfo: String,
    icon: ImageVector,
    iconTint: Color
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = iconTint)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = badgeColor,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            badge,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = badgeTextColor,
                                fontSize = 8.sp
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    info,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                selectionInfo,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                ),
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
fun DeleteSelectionBar() {
    Surface(
        color = SelectionBarBackground,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "RECLAIMING",
                    style = MaterialTheme.typography.labelSmall.copy(color = Color.LightGray, fontSize = 10.sp)
                )
                Text(
                    "160.8 MB",
                    style = MaterialTheme.typography.titleLarge.copy(color = Color.White, fontWeight = FontWeight.Bold)
                )
            }
            Button(
                onClick = { },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.height(48.dp).fillMaxWidth(0.6f)
            ) {
                Text("DELETE SELECTED", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FileCleanupScreenPreview() {
    DeDupTheme {
        FileCleanupScreen(rememberNavController())
    }
}
