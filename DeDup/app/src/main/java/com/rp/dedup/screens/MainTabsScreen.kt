package com.rp.dedup.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.rp.dedup.FileBrowserGatekeeper
import com.rp.dedup.core.viewmodels.UserProfileViewModel
import kotlinx.coroutines.launch

@Composable
fun MainTabsScreen(
    navController: NavHostController,
    profileViewModel: UserProfileViewModel,
    pagerState: PagerState
) {
    val scope = rememberCoroutineScope()

    // Hardware back from any non-Dashboard tab returns to Dashboard first, instead of leaving the app.
    BackHandler(enabled = pagerState.currentPage != 0) {
        scope.launch { pagerState.animateScrollToPage(0) }
    }

    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        when (page) {
            0 -> DashboardScreen(navController, profileViewModel)
            1 -> FileCleanupScreen(navController)
            2 -> FileBrowserGatekeeper(navController)
            3 -> OptimizeScreen(navController)
            else -> StorageInsightsScreen(navController)
        }
    }
}
