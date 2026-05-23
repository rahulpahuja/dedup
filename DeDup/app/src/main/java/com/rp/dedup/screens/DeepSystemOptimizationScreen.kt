package com.rp.dedup.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.FolderDelete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.rp.dedup.UIConstants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeepSystemOptimizationScreen(navController: NavHostController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Deep System Optimization", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "Advanced tools to reclaim space and keep your storage clean.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
            }
            item {
                OptimizationFeatureCard(
                    icon = Icons.Default.Chat,
                    iconTint = Color(0xFF25D366),
                    title = "Social Media Cleaner",
                    description = "Find and remove duplicate photos, videos, and files inside WhatsApp and Telegram media folders.",
                    ctaLabel = "Scan Now",
                    onClick = { navController.navigate(UIConstants.ROUTE_SOCIAL_MEDIA_CLEANER) }
                )
            }
            item {
                OptimizationFeatureCard(
                    icon = Icons.Default.FolderDelete,
                    iconTint = Color(0xFFFF6D00),
                    title = "Empty Folder Remover",
                    description = "Deep sweep your storage to find and delete leftover empty directory trees after cleanup.",
                    ctaLabel = "Deep Sweep",
                    onClick = { navController.navigate(UIConstants.ROUTE_EMPTY_FOLDER) }
                )
            }
            item {
                OptimizationFeatureCard(
                    icon = Icons.Default.AccountTree,
                    iconTint = Color(0xFF1A73E8),
                    title = "Big File Map",
                    description = "Visual treemap showing exactly which folders are consuming the most storage space.",
                    ctaLabel = "View Map",
                    onClick = { navController.navigate(UIConstants.ROUTE_BIG_FILE_MAP) }
                )
            }
        }
    }
}

@Composable
private fun OptimizationFeatureCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    ctaLabel: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(iconTint.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(26.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onClick,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(ctaLabel, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
