package com.rp.dedup.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.rp.dedup.LocalDrawerState
import com.rp.dedup.R
import com.rp.dedup.Screen
import com.rp.dedup.core.analytics.AnalyticsManager
import com.rp.dedup.core.caching.DataStoreManager
import com.rp.dedup.core.ui.DeDupTopBar
import com.rp.dedup.core.viewmodels.DashboardViewModel
import com.rp.dedup.core.viewmodels.SettingsViewModel
import com.rp.dedup.core.viewmodels.StorageHealthViewModel
import com.rp.dedup.screens.dashboard.components.OptimizationCard
import com.rp.dedup.screens.dashboard.components.SavingsCalculatorCard
import com.rp.dedup.screens.dashboard.components.StorageHealthScoreCard
import com.rp.dedup.screens.dashboard.components.StorageSummaryCard
import com.rp.dedup.ui.theme.DeDupTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageInsightsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val drawerState = LocalDrawerState.current
    val scope = rememberCoroutineScope()
    val dashboardViewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory(context))
    val healthViewModel: StorageHealthViewModel = viewModel(factory = StorageHealthViewModel.Factory(context))
    val dataStoreManager = remember { DataStoreManager(context) }
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(dataStoreManager))
    val analyticsManager = remember { AnalyticsManager.getInstance(context) }

    val storageStats     by dashboardViewModel.storageStats.collectAsState()
    val totalReclaimable by dashboardViewModel.totalReclaimableBytes.collectAsState()
    val healthScore      by healthViewModel.score.collectAsState()
    val selectedCurrency by settingsViewModel.selectedCurrency.collectAsState()

    com.rp.dedup.core.analytics.TrackFeatureUsage("StorageInsights")
    LaunchedEffect(Unit) { analyticsManager.logScreenView("StorageInsights") }

    Scaffold(
        topBar = {
            DeDupTopBar(
                title = stringResource(R.string.screen_storage_insights),
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
                StorageSummaryCard(
                    stats = storageStats,
                    reclaimableBytes = totalReclaimable,
                    onClick = { analyticsManager.logTreemapViewed(); navController.navigate(Screen.BigFileMap.route) }
                )
                Spacer(Modifier.height(24.dp))
            }
            item {
                if (healthScore.overallScore > 0) {
                    StorageHealthScoreCard(score = healthScore)
                    Spacer(Modifier.height(16.dp))
                }
                SavingsCalculatorCard(reclaimableBytes = totalReclaimable, overrideCurrencyCode = selectedCurrency)
                Spacer(Modifier.height(24.dp))
            }
            item {
                OptimizationCard(
                    title = stringResource(R.string.screen_activity_log),
                    description = stringResource(R.string.activity_log_desc),
                    icon = Icons.Default.History,
                    isOptimized = false,
                    onClick = { navController.navigate(Screen.Activity.route) }
                )
                Spacer(Modifier.height(12.dp))
                OptimizationCard(
                    title = stringResource(R.string.screen_scan_history),
                    description = stringResource(R.string.scan_history_desc),
                    icon = Icons.AutoMirrored.Filled.ManageSearch,
                    isOptimized = false,
                    onClick = { navController.navigate(Screen.ScanHistory.route) }
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Light Mode")
@Composable
fun StorageInsightsScreenPreview() {
    DeDupTheme {
        CompositionLocalProvider(
            LocalDrawerState provides rememberDrawerState(DrawerValue.Closed)
        ) {
            StorageInsightsScreen(navController = rememberNavController())
        }
    }
}
