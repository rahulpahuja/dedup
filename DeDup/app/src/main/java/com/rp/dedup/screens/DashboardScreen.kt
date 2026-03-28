package com.rp.dedup.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rp.dedup.LocalDrawerState
import com.rp.dedup.Screen
import com.rp.dedup.ui.theme.DeDupTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavHostController) {
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
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = MaterialTheme.colorScheme.primary
                        )
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = { BottomNavigationBar(navController) }
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
                    "Storage Dashboard",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                StorageSummaryCard()
                Spacer(modifier = Modifier.height(32.dp))
            }

            item {
                Text(
                    "Quick Scan",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                QuickScanGrid(navController)
                Spacer(modifier = Modifier.height(32.dp))
            }

            item {
                OptimizationSection()
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun StorageSummaryCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Used Space",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        "42.5 GB / 128 GB",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Icon(
                    Icons.Default.Storage,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            LinearProgressIndicator(
                progress = { 0.33f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "33% of your storage is occupied",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun QuickScanGrid(navController: NavHostController) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanCategoryCard(
                title = "Images",
                count = "1.2k",
                icon = Icons.Default.Image,
                color = Color(0xFF4285F4),
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate(Screen.ResultsContacts.route) }
            )
            ScanCategoryCard(
                title = "Videos",
                count = "45",
                icon = Icons.Default.Videocam,
                color = Color(0xFFEA4335),
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate(Screen.VideoScanner.route) }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanCategoryCard(
                title = "Documents",
                count = "230",
                icon = Icons.Default.Description,
                color = Color(0xFFFBBC05),
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate(Screen.Cleanup.route) }
            )
            ScanCategoryCard(
                title = "APKs",
                count = "12",
                icon = Icons.Default.Android,
                color = Color(0xFF34A853),
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate(Screen.FileScanner.createRoute("apk")) }
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

@Composable
fun OptimizationSection() {
    Column {
        Text(
            "Optimization Suggestions",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
        OptimizationCard(
            title = "Clear Cache Files",
            description = "Remove 1.2 GB of temporary app data",
            icon = Icons.Default.CleaningServices,
            isOptimized = false
        )
        Spacer(modifier = Modifier.height(12.dp))
        OptimizationCard(
            title = "Large File Review",
            description = "Analyze 4 files larger than 500 MB",
            icon = Icons.Default.Assessment,
            isOptimized = true
        )
    }
}

@Composable
fun OptimizationCard(
    title: String,
    description: String,
    icon: ImageVector,
    isOptimized: Boolean
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
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
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = if (isOptimized) Icons.Default.CheckCircle else Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (isOptimized) Color(0xFF34A853) else Color(0xFFEA4335)
            )
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    
    val selectedIndex = when {
        currentRoute == Screen.Dashboard.route -> 0
        currentRoute == Screen.Cleanup.route -> 1
        currentRoute?.startsWith("file_scanner") == true -> 2
        currentRoute == Screen.VideoScanner.route -> 3
        currentRoute == Screen.Settings.route -> 4
        else -> 0
    }

    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(
            icon = { Icon(Icons.Default.GridView, contentDescription = null) },
            label = { Text("DASH") },
            selected = selectedIndex == 0,
            onClick = { navController.navigate(Screen.Dashboard.route) }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Search, contentDescription = null) },
            label = { Text("SCAN") },
            selected = selectedIndex == 1,
            onClick = { navController.navigate(Screen.Cleanup.route) }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Description, contentDescription = null) },
            label = { Text("FILES") },
            selected = selectedIndex == 2,
            onClick = { navController.navigate(Screen.FileScanner.createRoute("pdf")) }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Videocam, contentDescription = null) },
            label = { Text("VIDEO") },
            selected = selectedIndex == 3,
            onClick = { navController.navigate(Screen.VideoScanner.route) }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text("SETTINGS") },
            selected = selectedIndex == 4,
            onClick = { navController.navigate(Screen.Settings.route) }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DashboardScreenPreview() {
    DeDupTheme {
        DashboardScreen(rememberNavController())
    }
}
