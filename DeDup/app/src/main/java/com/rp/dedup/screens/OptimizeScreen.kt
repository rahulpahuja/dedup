package com.rp.dedup.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.rp.dedup.LocalDrawerState
import com.rp.dedup.R
import com.rp.dedup.Screen
import com.rp.dedup.core.ui.DeDupTopBar
import com.rp.dedup.screens.dashboard.components.DeepOptimizationCard
import com.rp.dedup.screens.dashboard.components.OptimizationCard
import com.rp.dedup.screens.dashboard.components.OptimizationSection
import com.rp.dedup.screens.dashboard.components.SmartAiCleanupCard
import com.rp.dedup.ui.theme.DeDupTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptimizeScreen(navController: NavHostController) {
    val drawerState = LocalDrawerState.current
    val scope = rememberCoroutineScope()

    com.rp.dedup.core.analytics.TrackFeatureUsage("Optimize")

    Scaffold(
        topBar = {
            DeDupTopBar(
                title = stringResource(R.string.screen_optimize),
                navigationIcon = {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.menu))
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                SmartAiCleanupCard(onClick = { navController.navigate(Screen.SmartJunk.route) })
                Spacer(Modifier.height(12.dp))
            }
            item {
                DeepOptimizationCard(onClick = { navController.navigate(Screen.DeepOptimization.route) })
                Spacer(Modifier.height(24.dp))
            }
            item {
                OptimizationSection(navController = navController)
                Spacer(Modifier.height(12.dp))
            }
            item {
                OptimizationCard(
                    title = stringResource(R.string.semantic_scan_title),
                    description = stringResource(R.string.semantic_scan_desc),
                    icon = Icons.Default.AutoAwesome,
                    isOptimized = false,
                    onClick = { navController.navigate(Screen.SemanticScanner.route) }
                )
                Spacer(Modifier.height(12.dp))
                OptimizationCard(
                    title = stringResource(R.string.compression_title),
                    description = stringResource(R.string.compression_desc),
                    icon = Icons.Default.Compress,
                    isOptimized = false,
                    onClick = { navController.navigate(Screen.ImageCompression.route) }
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Light Mode")
@Composable
fun OptimizeScreenPreview() {
    DeDupTheme {
        CompositionLocalProvider(
            LocalDrawerState provides rememberDrawerState(DrawerValue.Closed)
        ) {
            OptimizeScreen(navController = rememberNavController())
        }
    }
}
