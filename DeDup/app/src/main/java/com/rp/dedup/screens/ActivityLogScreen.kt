package com.rp.dedup.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.rp.dedup.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLogScreen(navController: NavHostController) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Activity Log",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            BottomNavigationBar(navController)
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
                Spacer(modifier = Modifier.height(8.dp))

                if (isDark) {
                    Text(
                        "WEEKLY SUMMARY",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "14.2 GB",
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, color = Color.White)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Total Reclaimed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                } else {
                    Text(
                        "SYSTEM PULSE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF00838F))
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Recent Insights",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = DarkBlue)
                        )
                        Text(
                            "12 New",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Featured Card (Scan Complete / Smart Insight)
            item {
                if (isDark) {
                    ActivityCardDark(
                        icon = Icons.Default.AutoAwesome,
                        title = "Smart Insight",
                        time = "2H AGO",
                        description = "You have 842MB of unused video assets. These files haven't been opened in 6 months and have lower resolution duplicates.",
                        buttonText = "Optimize Now"
                    )
                } else {
                    ScanCompleteCard()
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Summary Section for Day Mode
            item {
                if (!isDark) {
                    StorageReclaimedCard()
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            // List Items
            item {
                if (isDark) {
                    CompactActivityItem(
                        icon = Icons.Default.Scanner,
                        title = "Scan Complete",
                        description = "Found 128 redundant documents across your connected Cloud Drives. 94% match accuracy detected.",
                        time = "5H AGO",
                        iconColor = Color(0xFF80DEEA)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    CompactActivityItem(
                        icon = Icons.Default.CloudDone,
                        title = "Storage Reclaimed",
                        description = "4.2 GB cleared. Successfully removed 1,402 temporary cache files and duplicate system backups.",
                        time = "YESTERDAY",
                        iconColor = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    EfficiencyBanner()
                    Spacer(modifier = Modifier.height(24.dp))
                    CompactActivityItem(
                        icon = Icons.Default.Warning,
                        title = "Storage Warning",
                        description = "Your main SSD is 92% full. We recommend running a \"Deep Clean\" to free up approximately 12GB of system junk.",
                        time = "2D AGO",
                        iconColor = Color(0xFFFFB74D)
                    )
                } else {
                    ActivityListItem(
                        icon = Icons.Default.Lightbulb,
                        title = "Smart Insight: Unused video assets found",
                        description = "You have 842MB of video files that haven't been opened in over 18 months. Consider archiving.",
                        time = "YESTERDAY",
                        actions = listOf("Move to Cloud", "Dismiss")
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    ActivityListItem(
                        icon = Icons.Default.CheckCircle,
                        title = "Backup Synchronization Successful",
                        description = "Your redundant-free manifest has been mirrored to the secure vault.",
                        time = "OCT 24",
                        iconTint = Color(0xFF00838F)
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    ActivityListItem(
                        icon = Icons.Default.Warning,
                        title = "Low Storage Threshold",
                        description = "Device storage is 92% full. Run a deep scan to free up space immediately.",
                        time = "OCT 22",
                        iconTint = Color(0xFFD32F2F),
                        buttonText = "OPTIMIZE NOW",
                        buttonColor = Color(0xFFD32F2F)
                    )
                }
                
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

@Composable
fun ScanCompleteCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFE8EAF6),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = PrimaryBlue)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Scan Complete: Found 128 redundant documents.",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, lineHeight = 28.sp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "High-confidence duplicates detected across your Cloud and Local storage volumes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "2H AGO",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF8DA9D4)
                )
                Button(
                    onClick = { },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Review All", modifier = Modifier.padding(horizontal = 8.dp))
                }
            }
        }
    }
}

@Composable
fun StorageReclaimedCard() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        shape = RoundedCornerShape(32.dp),
        color = Color(0xFFE0F7FA).copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Dehaze, contentDescription = null, tint = Color(0xFF00838F), modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "4.2 GB",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF00838F))
            )
            Text(
                "STORAGE RECLAIMED",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF00838F)
            )
        }
    }
}

@Composable
fun ActivityCardDark(
    icon: ImageVector,
    title: String,
    time: String,
    description: String,
    buttonText: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White))
                }
                Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(buttonText)
            }
        }
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
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color.White),
                    modifier = Modifier.weight(1f)
                )
                Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.weight(1f)
                )
                Text(time, style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        TextButton(onClick = { }, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(32.dp)) {
                            Text(
                                action,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = if (action == "Dismiss") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
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
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(buttonText, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

@Composable
fun EfficiencyBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF1E1E1E), Color(0xFF0D1B2A))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Efficiency is an art.", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = Color.White))
            Text("Keep your workspace lean.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
