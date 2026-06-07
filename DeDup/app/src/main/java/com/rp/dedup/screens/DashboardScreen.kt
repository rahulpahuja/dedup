package com.rp.dedup.screens

import android.content.res.Configuration
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.canopas.lib.showcase.IntroShowcase
import com.canopas.lib.showcase.component.ShowcaseStyle
import com.rp.dedup.LocalDrawerState
import com.rp.dedup.R
import com.rp.dedup.Screen
import com.rp.dedup.UIConstants
import com.rp.dedup.core.analytics.AnalyticsManager
import com.rp.dedup.core.ui.ImagePreviewDialog
import com.rp.dedup.core.caching.DataStoreManager
import com.rp.dedup.core.db.AppDatabase
import com.rp.dedup.core.repository.ScanHistoryRepository
import com.rp.dedup.core.search.ImageSearchRepository
import com.rp.dedup.core.viewmodels.DashboardViewModel
import com.rp.dedup.core.viewmodels.ImageSearchViewModel
import com.rp.dedup.core.model.MediaCounts
import com.rp.dedup.core.model.StorageStats
import com.rp.dedup.core.viewmodels.UserProfileViewModel
import com.rp.dedup.ui.theme.DeDupTheme
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavHostController,
    profileViewModel: UserProfileViewModel
) {
    val context = LocalContext.current
    val dashboardViewModel: DashboardViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DashboardViewModel(
                    ScanHistoryRepository(AppDatabase.getDatabase(context).scanHistoryDao()),
                    context.applicationContext
                ) as T
            }
        }
    )
    val searchViewModel: ImageSearchViewModel = viewModel(
        factory = ImageSearchViewModel.Factory(context)
    )
    val analyticsManager = remember { AnalyticsManager(context) }

    val storageStats by dashboardViewModel.storageStats.collectAsState()
    val totalReclaimable by dashboardViewModel.totalReclaimableBytes.collectAsState()
    val mediaCounts by dashboardViewModel.mediaCounts.collectAsState()
    val searchResults by searchViewModel.results.collectAsState()
    val isSearching by searchViewModel.isSearching.collectAsState()
    val searchProgress by searchViewModel.progress.collectAsState()
    val searchError by searchViewModel.error.collectAsState()

    val dataStoreManager = remember { DataStoreManager(context) }
    // Default true so tutorial doesn't flash before DataStore loads
    val tutorialShown by dataStoreManager.readData(DataStoreManager.TUTORIAL_SHOWN, false)
        .collectAsState(initial = true)
    
    LaunchedEffect(Unit) {
        analyticsManager.logScreenView("Dashboard")
    }

    LaunchedEffect(tutorialShown) {
        if (!tutorialShown) {
            analyticsManager.logTutorialInteraction("DASHBOARD", "VIEWED")
        }
    }

    val coroutineScope = rememberCoroutineScope()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                dashboardViewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DashboardScreenContent(
        navController = navController,
        userName = profileViewModel.name,
        userImageUrl = profileViewModel.profileImageUrl,
        storageStats = storageStats,
        totalReclaimable = totalReclaimable,
        mediaCounts = mediaCounts,
        searchResults = searchResults,
        isSearching = isSearching,
        searchProgress = searchProgress,
        searchError = searchError,
        onSearch = { searchViewModel.search(it) },
        onClearSearch = { searchViewModel.clear() },
        analyticsManager = analyticsManager,
        showTutorial = !tutorialShown,
        onTutorialComplete = {
            analyticsManager.logTutorialInteraction("DASHBOARD", "COMPLETED")
            coroutineScope.launch {
                dataStoreManager.writeData(DataStoreManager.TUTORIAL_SHOWN, true)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreenContent(
    navController: NavHostController,
    userName: String,
    userImageUrl: String,
    storageStats: StorageStats,
    totalReclaimable: Long,
    mediaCounts: MediaCounts = MediaCounts(),
    searchResults: List<ImageSearchRepository.SearchResult>,
    isSearching: Boolean,
    searchProgress: Pair<Int, Int>,
    searchError: String?,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    analyticsManager: AnalyticsManager? = null,
    showTutorial: Boolean = false,
    onTutorialComplete: () -> Unit = {}
) {
    val drawerState = LocalDrawerState.current
    val scope = rememberCoroutineScope()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showSearchSheet by rememberSaveable { mutableStateOf(false) }

    val tutorialStyle = ShowcaseStyle.Default.copy(
        backgroundColor = Color(0xFF090F20),
        backgroundAlpha = 0.97f,
        targetCircleColor = Color(0xFF5FA3FF)
    )

    IntroShowcase(
        showIntroShowCase = showTutorial,
        dismissOnClickOutside = true,
        onShowCaseCompleted = onTutorialComplete
    ) {

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = stringResource(R.string.menu),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        stringResource(R.string.app_name),
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    IconButton(
                        onClick = { showSearchSheet = true },
                        modifier = Modifier.introShowCaseTarget(
                            index = 0,
                            style = tutorialStyle,
                            content = {
                                TutorialTooltip(
                                    title = stringResource(R.string.tut_search_title),
                                    body = stringResource(R.string.tut_search_body)
                                )
                            }
                        )
                    ) {
                        AiSearchIcon()
                    }
                    IconButton(onClick = {}) {
                        Surface(
                            shape = CircleShape,
                            modifier = Modifier.size(30.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (userImageUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = userImageUrl,
                                        contentDescription = stringResource(R.string.profile),
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        text = userName.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    item {
                        Text(
                            stringResource(R.string.dashboard_title),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                    item {
                        StorageSummaryCard(
                            stats = storageStats,
                            reclaimableBytes = totalReclaimable,
                            onClick = { 
                                analyticsManager?.logTreemapViewed()
                                navController.navigate(Screen.BigFileMap.route) 
                            },
                            modifier = Modifier.introShowCaseTarget(
                                index = 1,
                                style = tutorialStyle,
                                content = {
                                    TutorialTooltip(
                                        title = stringResource(R.string.tut_storage_title),
                                        body = stringResource(R.string.tut_storage_body)
                                    )
                                }
                            )
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                    item {
                        SavingsCalculatorCard(reclaimableBytes = totalReclaimable)
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                    item {
                        Text(
                            stringResource(R.string.quick_scan_title),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        QuickScanGrid(
                            navController = navController,
                            mediaCounts = mediaCounts,
                            modifier = Modifier.introShowCaseTarget(
                                index = 2,
                                style = tutorialStyle,
                                content = {
                                    TutorialTooltip(
                                        title = stringResource(R.string.tut_quick_scan_title),
                                        body = stringResource(R.string.tut_quick_scan_body)
                                    )
                                }
                            )
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                    item {
                        OptimizationSection(
                            navController = navController,
                            modifier = Modifier.introShowCaseTarget(
                                index = 3,
                                style = tutorialStyle,
                                content = {
                                    TutorialTooltip(
                                        title = stringResource(R.string.tut_opt_tips_title),
                                        body = stringResource(R.string.tut_opt_tips_body)
                                    )
                                }
                            )
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                    item {
                        SmartAiCleanupCard(
                            onClick = { 
                                analyticsManager?.logSmartCleanupViewed()
                                navController.navigate(Screen.SmartJunk.route) 
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        DeepOptimizationCard(
                            onClick = { navController.navigate(Screen.DeepOptimization.route) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        ContactDedupCard(
                            onClick = { navController.navigate(Screen.ContactDedup.route) }
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
        }
    }

    if (showSearchSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                showSearchSheet = false
                searchQuery = ""
                onClearSearch()
            },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFF9C6FFF),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.search_placeholder),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                // Search input
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                    leadingIcon = {
                        Icon(Icons.Default.ImageSearch, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = ""; onClearSearch() }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_search))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Search
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = { onSearch(searchQuery) }
                    )
                )
                // Suggestions when query is empty
                if (searchQuery.isEmpty()) {
                    SearchSuggestionsRow { suggestion ->
                        searchQuery = suggestion
                        onSearch(suggestion)
                    }
                } else {
                    LaunchedEffect(searchQuery) { onSearch(searchQuery) }
                }
                // Results
                Box(modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 480.dp)) {
                    ImageSearchContent(
                        query = searchQuery,
                        results = searchResults,
                        isSearching = isSearching,
                        progress = searchProgress,
                        error = searchError
                    )
                }
            }
        }
    }

    } // end IntroShowcase
}

/** Search icon with a small AutoAwesome sparkle badge to signal AI-powered search. */
@Composable
fun AiSearchIcon() {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
        Icon(
            Icons.Default.Search,
            contentDescription = "AI Search",
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            modifier = Modifier
                .size(10.dp)
                .align(Alignment.TopEnd)
                .offset(x = 4.dp, y = (-4).dp),
            tint = Color(0xFF9C6FFF)
        )
    }
}

@Composable
fun SearchSuggestionsRow(onSuggestionClick: (String) -> Unit) {
    val suggestions = listOf("Pet", "Food", "Nature", "Document", "Vehicle", "Portrait")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        suggestions.forEach { suggestion ->
            SuggestionChip(
                onClick = { onSuggestionClick(suggestion) },
                label = { Text(suggestion) },
                shape = RoundedCornerShape(12.dp),
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            )
        }
    }
}

@Composable
fun SmartAiCleanupCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    stringResource(R.string.smart_ai_cleanup_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    stringResource(R.string.smart_ai_cleanup_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun DeepOptimizationCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF1A73E8).copy(alpha = 0.15f),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                        tint = Color(0xFF1A73E8),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.deep_system_opt_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    stringResource(R.string.deep_system_opt_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun ContactDedupCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Contact Deduplication",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    "Find and merge duplicate contacts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun SavingsCalculatorCard(reclaimableBytes: Long) {
    val locale = Locale.getDefault()
    val currency = try {
        Currency.getInstance(locale)
    } catch (_: Exception) {
        Currency.getInstance("USD")
    }
    
    val costPerGb = when (currency.currencyCode) {
        "INR" -> 5.0
        "EUR" -> 0.05
        "GBP" -> 0.04
        else  -> 0.06
    }

    val reclaimableGb = reclaimableBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    val moneySaved = reclaimableGb * costPerGb

    val currencyFormatter = NumberFormat.getCurrencyInstance(locale).apply {
        this.currency = currency
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = UIConstants.ColorSavingsGreen.copy(alpha = 0.15f),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Payments,
                        contentDescription = null,
                        tint = UIConstants.ColorSavingsGreen,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    stringResource(R.string.potential_savings),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    "Money saved: ${currencyFormatter.format(moneySaved)}",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
                Text(
                    "Based on storage cost of ${currencyFormatter.format(costPerGb)} / GB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun StorageSummaryCard(
    stats: StorageStats = StorageStats(),
    reclaimableBytes: Long = 0L,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val usedFractionAnimated by animateFloatAsState(
        targetValue = stats.usedFraction,
        animationSpec = tween(durationMillis = 900),
        label = "usedFraction"
    )
    val savingsFraction = if (stats.usedBytes > 0)
        (reclaimableBytes.toFloat() / stats.usedBytes).coerceIn(0f, 1f)
    else 0f
    val savingsFractionAnimated by animateFloatAsState(
        targetValue = savingsFraction,
        animationSpec = tween(durationMillis = 900, delayMillis = 200),
        label = "savingsFraction"
    )

    val usedLabel = if (stats.totalBytes > 0)
        "${Formatter.formatShortFileSize(context, stats.usedBytes)} / ${
            Formatter.formatShortFileSize(context, stats.totalBytes)
        }"
    else stringResource(R.string.calculating)

    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        stringResource(R.string.used_space),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        usedLabel,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Icon(
                    Icons.Default.Storage,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                stringResource(R.string.device_storage),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { usedFractionAnimated },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                strokeCap = StrokeCap.Round
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                if (stats.totalBytes > 0)
                    "${"%.0f".format(stats.usedFraction * 100)}% of storage used  •  " +
                    "${Formatter.formatShortFileSize(context, stats.freeBytes)} free"
                else stringResource(R.string.loading_storage_info),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f)
            )

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.dedup_savings),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                    )
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = UIConstants.ColorSavingsGreen.copy(alpha = 0.15f)
                ) {
                    Text(
                        if (reclaimableBytes > 0)
                            Formatter.formatShortFileSize(context, reclaimableBytes)
                        else "0 B",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = UIConstants.ColorSavingsGreen
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { savingsFractionAnimated },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = UIConstants.ColorSavingsGreen,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                strokeCap = StrokeCap.Round
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                if (reclaimableBytes > 0)
                    stringResource(R.string.storage_summary_reclaimable, Formatter.formatShortFileSize(context, reclaimableBytes))
                else stringResource(R.string.run_scan_prompt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f)
            )
        }
    }
}

private fun formatCount(n: Int): String = when {
    n <= 0 -> "–"
    n >= 1_000_000 -> "${"%.1f".format(n / 1_000_000.0)}M"
    n >= 1_000 -> "${"%.1f".format(n / 1_000.0)}k"
    else -> n.toString()
}

@Composable
fun QuickScanGrid(
    navController: NavHostController,
    mediaCounts: MediaCounts = MediaCounts(),
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanCategoryCard(
                title = stringResource(R.string.quick_scan_images),
                count = formatCount(mediaCounts.images),
                icon = Icons.Default.Image,
                color = UIConstants.ColorImages,
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate(Screen.ImageScanner.route) }
            )
            ScanCategoryCard(
                title = stringResource(R.string.quick_scan_videos),
                count = formatCount(mediaCounts.videos),
                icon = Icons.Default.Videocam,
                color = UIConstants.ColorVideos,
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate(Screen.VideoScanner.route) }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanCategoryCard(
                title = stringResource(R.string.quick_scan_documents),
                count = formatCount(mediaCounts.pdfs),
                icon = Icons.Default.Description,
                color = UIConstants.ColorDocuments,
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate(Screen.FileScanner.createRoute("pdf")) }
            )
            ScanCategoryCard(
                title = stringResource(R.string.quick_scan_apks),
                count = formatCount(mediaCounts.apks),
                icon = Icons.Default.Android,
                color = UIConstants.ColorApks,
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate(Screen.FileScanner.createRoute("apk")) }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanCategoryCard(
                title = stringResource(R.string.quick_scan_browse_files),
                count = "All",
                icon = Icons.Default.FolderOpen,
                color = UIConstants.ColorBrowseFiles,
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate(Screen.FileBrowser.route) }
            )
            ScanCategoryCard(
                title = stringResource(R.string.quick_scan_history),
                count = "Log",
                icon = Icons.Default.History,
                color = UIConstants.ColorScanHistory,
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate(Screen.ScanHistory.route) }
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
            onClick = { }
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
                tint = if (isOptimized) UIConstants.ColorSavingsGreen else UIConstants.ColorError
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
        currentRoute?.startsWith("file_scanner") == true || currentRoute == Screen.FileBrowser.route -> 2
        currentRoute == Screen.VideoScanner.route -> 3
        currentRoute == Screen.Settings.route || currentRoute == Screen.ScanHistory.route -> 4
        else -> 0
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .navigationBarsPadding(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            modifier = Modifier.height(72.dp)
        ) {
            NavigationBarItem(
                icon = { Icon(Icons.Default.GridView, contentDescription = null) },
                label = { Text(stringResource(R.string.nav_dash), style = MaterialTheme.typography.labelSmall) },
                selected = selectedIndex == 0,
                onClick = { navController.navigate(Screen.Dashboard.route) }
            )
            NavigationBarItem(
                icon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text(stringResource(R.string.nav_scan), style = MaterialTheme.typography.labelSmall) },
                selected = selectedIndex == 1,
                onClick = { navController.navigate(Screen.Cleanup.route) }
            )
            NavigationBarItem(
                icon = { Icon(Icons.Default.Description, contentDescription = null) },
                label = { Text(stringResource(R.string.nav_files), style = MaterialTheme.typography.labelSmall) },
                selected = selectedIndex == 2,
                onClick = { navController.navigate(Screen.FileBrowser.route) }
            )
            NavigationBarItem(
                icon = { Icon(Icons.Default.Videocam, contentDescription = null) },
                label = { Text(stringResource(R.string.nav_video), style = MaterialTheme.typography.labelSmall) },
                selected = selectedIndex == 3,
                onClick = { navController.navigate(Screen.VideoScanner.route) }
            )
            NavigationBarItem(
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                label = { Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.labelSmall) },
                selected = selectedIndex == 4,
                onClick = { navController.navigate(Screen.Settings.route) }
            )
        }
    }
}

@Composable
fun ImageSearchContent(
    query: String,
    results: List<ImageSearchRepository.SearchResult>,
    isSearching: Boolean,
    progress: Pair<Int, Int>,
    error: String?
) {
    when {
        query.isBlank() -> {
            Box(
                modifier = Modifier.fillMaxSize().padding(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ImageSearch,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.search_desc),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.search_example),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }

        isSearching -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                val (labeled, total) = progress
                Text(
                    if (total > 0) stringResource(R.string.analyzing_images_progress, labeled, total)
                    else stringResource(R.string.analyzing_images),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (total > 0) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { labeled.toFloat() / total },
                        modifier = Modifier.fillMaxWidth(0.6f)
                    )
                }
            }
        }

        error != null -> {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }
        }

        results.isEmpty() -> {
            Box(
                modifier = Modifier.fillMaxSize().padding(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.SearchOff,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.no_matching_images),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.try_different_words),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }

        else -> {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    if (results.size == 1) stringResource(R.string.image_matched, 1) else stringResource(R.string.images_matched, results.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(results) { result ->
                        ImageSearchResultItem(result)
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageSearchResultItem(result: ImageSearchRepository.SearchResult) {
    var showPreview by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { showPreview = true })
            }
    ) {
        AsyncImage(
            model = result.uri,
            contentDescription = result.matchedLabels.joinToString(),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
                .padding(horizontal = 4.dp, vertical = 3.dp)
        ) {
            Text(
                text = result.matchedLabels.take(2).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    color = Color.White
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    if (showPreview) {
        ImagePreviewDialog(
            uri = result.uri,
            matchedLabels = result.matchedLabels,
            onDismiss = { showPreview = false }
        )
    }
}

@Composable
private fun TutorialTooltip(title: String, body: String) {
    Column(modifier = Modifier.width(260.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.80f)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.tap_anywhere_continue),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF5FA3FF)
        )
    }
}

@Preview(showBackground = true, name = "Bottom Navigation - Light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Bottom Navigation - Dark")
@Composable
fun BottomNavigationBarPreview() {
    DeDupTheme {
        BottomNavigationBar(navController = rememberNavController())
    }
}

@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
fun DashboardScreenPreview() {
    DeDupTheme {
        // Fix: Provide a LocalDrawerState to avoid IllegalStateException in Preview
        CompositionLocalProvider(LocalDrawerState provides rememberDrawerState(DrawerValue.Closed)) {
            DashboardScreenContent(
                navController = rememberNavController(),
                userName = "John Doe",
                userImageUrl = "",
                storageStats = StorageStats(
                    totalBytes = 128L * 1024 * 1024 * 1024,
                    usedBytes = 82L * 1024 * 1024 * 1024,
                    freeBytes = 46L * 1024 * 1024 * 1024
                ),
                totalReclaimable = 12L * 1024 * 1024 * 1024,
                searchResults = emptyList(),
                isSearching = false,
                searchProgress = 0 to 0,
                searchError = null,
                onSearch = {},
                onClearSearch = {},
                showTutorial = false
            )
        }
    }
}
