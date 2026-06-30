package com.rp.dedup.screens.dashboard.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.rp.dedup.R
import com.rp.dedup.Screen
import com.rp.dedup.UIConstants

@Composable
fun OptimizationSection(navController: NavHostController, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            stringResource(R.string.optimization_suggestions),
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
        OptimizationCard(
            title = stringResource(R.string.clear_cache_files),
            description = stringResource(R.string.clear_cache_desc),
            icon = Icons.Default.CleaningServices,
            isOptimized = false,
            onClick = { navController.navigate(Screen.CacheCleaner.route) }
        )
        Spacer(modifier = Modifier.height(12.dp))
        OptimizationCard(
            title = stringResource(R.string.large_file_review),
            description = stringResource(R.string.large_file_review_desc, 4),
            icon = Icons.Default.Assessment,
            isOptimized = true,
            onClick = {}
        )
    }
}

@Composable
fun OptimizationCard(
    title: String,
    description: String,
    icon: ImageVector,
    isOptimized: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
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
                Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = if (isOptimized) Icons.Default.CheckCircle else Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (isOptimized) UIConstants.ColorSavingsGreen else UIConstants.ColorError
            )
        }
    }
}
