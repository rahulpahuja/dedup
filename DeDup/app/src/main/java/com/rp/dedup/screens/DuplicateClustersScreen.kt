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
import androidx.compose.ui.text.font.FontWeight
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
fun DuplicateClustersScreen(navController: NavHostController) {
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
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
                PurgeSelectionBar()
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
                    "RESULTS / PHOTO & VIDEO",
                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF00838F), fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Duplicate clusters.",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Our ML engine identified 34 redundant groups across your library. Keep the sharpest, purge the rest.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    PotentialSpaceCard()
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            // Cluster 01
            item {
                ClusterHeader("01", "Golden Hour Beach", "4 similar items found", 3)
                Spacer(modifier = Modifier.height(16.dp))
                MainMediaItem(
                    badge = "ML BEST CHOICE",
                    similarity = "88% Similarity",
                    fileName = "IMG_8433.JPG",
                    fileInfo = "12.4 MB • 4032 × 3024"
                )
                Spacer(modifier = Modifier.height(12.dp))
                DuplicateListItem("SLOPPY BLUR", "IMG_8433.JPG", "1.2 MB | 72% Sharp")
                Spacer(modifier = Modifier.height(8.dp))
                DuplicateListItem("UNDEREXPOSED", "IMG_8454.JPG", "1.1 MB | 62% Sharp")
                Spacer(modifier = Modifier.height(32.dp))
            }

            // Cluster 02
            item {
                ClusterHeader("02", "Mountain Hike Burst", "2 identical video files", 2)
                Spacer(modifier = Modifier.height(16.dp))
                MainMediaItem(
                    isVideo = true,
                    similarity = "30 FPS — 4K HDR",
                    fileName = "VID_2023_E345.MP4",
                    fileInfo = "167.2 MB"
                )
                Spacer(modifier = Modifier.height(12.dp))
                DuplicateListItem("EXACT MATCH", "VID_2023_D01.MP4", "167.2 MB • Exact Copy")
                Spacer(modifier = Modifier.height(8.dp))
                DuplicateListItem("EXACT MATCH", "VID_2023_D01.MP4", "167.2 MB • Exact Copy")
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

@Composable
fun PotentialSpaceCard() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.height(80.dp).width(120.dp)
    ) {
        Row {
            Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.End) {
                Text("POTENTIAL SPACE", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Text("2.4 GB", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
            }
        }
    }
}

@Composable
fun ClusterHeader(id: String, title: String, subtitle: String, duplicateCount: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(id, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface))
            Text(subtitle, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(
            color = RedBadge.copy(alpha = if (MaterialTheme.colorScheme.onBackground == Color.White) 0.2f else 1f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(6.dp).background(RedText, CircleShape))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Delete $duplicateCount Duplicates", style = MaterialTheme.typography.labelSmall.copy(color = RedText, fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
fun MainMediaItem(
    badge: String? = null,
    isVideo: Boolean = false,
    similarity: String,
    fileName: String,
    fileInfo: String
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                // Placeholder for Image/Video
                Box(modifier = Modifier.fillMaxSize().background(if (isVideo) Color(0xFF0D47A1) else Color(0xFFFFCCBC))) {
                    if (isVideo) {
                        Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(64.dp).align(Alignment.Center))
                    }
                }
                
                if (badge != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(12.dp).align(Alignment.TopStart)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(badge, style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 8.sp))
                        }
                    }
                }

                if (isVideo) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(8.dp).align(Alignment.BottomEnd)
                    ) {
                        Text("25:34", modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontSize = 8.sp))
                    }
                }
            }
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (isVideo) Icons.Default.Videocam else Icons.Default.Image, contentDescription = null, tint = DarkCyan, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(similarity, style = MaterialTheme.typography.labelSmall.copy(color = DarkCyan, fontWeight = FontWeight.Bold))
                    }
                    Text("$fileName • $fileInfo", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    color = Color(0xFF80DEEA),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("KEEP", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = DarkCyan))
                }
            }
        }
    }
}

@Composable
fun DuplicateListItem(label: String, fileName: String, info: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold, fontSize = 8.sp))
                Text(fileName, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface))
                Text(info, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { }) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun PurgeSelectionBar() {
    Surface(
        color = SelectionBarBackground,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("TOTAL SELECTION", style = MaterialTheme.typography.labelSmall.copy(color = Color.LightGray, fontSize = 8.sp))
                Text("412.5 MB", style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
            }
            Spacer(modifier = Modifier.width(24.dp))
            Column {
                Text("FILES TO PURGE", style = MaterialTheme.typography.labelSmall.copy(color = Color.LightGray, fontSize = 8.sp))
                Text("12 Items", style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = { },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text("Purge All Duplicates", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DuplicateClustersScreenPreview() {
    DeDupTheme {
        DuplicateClustersScreen(rememberNavController())
    }
}
