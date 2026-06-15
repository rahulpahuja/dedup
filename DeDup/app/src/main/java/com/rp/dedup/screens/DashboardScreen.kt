package com.rp.dedup.screens

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.format.Formatter
import androidx.activity.result.IntentSenderRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.luminance
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
import com.rp.dedup.core.viewmodels.SettingsViewModel
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
    val isGuest = profileViewModel.isGuest
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
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(dataStoreManager)
    )
    val selectedCurrency by settingsViewModel.selectedCurrency.collectAsState()

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
        isGuest = isGuest,
        storageStats = storageStats,
        totalReclaimable = totalReclaimable,
        mediaCounts = mediaCounts,
        searchResults = searchResults,
        isSearching = isSearching,
        searchProgress = searchProgress,
        searchError = searchError,
        onSearch = { searchViewModel.search(it) },
        onClearSearch = { searchViewModel.clear() },
        onDeleteSearchResult = { searchViewModel.removeDeletedResult(it) },
        analyticsManager = analyticsManager,
        showTutorial = !tutorialShown,
        selectedCurrencyCode = selectedCurrency,
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
    isGuest: Boolean = false,
    storageStats: StorageStats,
    totalReclaimable: Long,
    mediaCounts: MediaCounts = MediaCounts(),
    searchResults: List<ImageSearchRepository.SearchResult>,
    isSearching: Boolean,
    searchProgress: Pair<Int, Int>,
    searchError: String?,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    onDeleteSearchResult: (Uri) -> Unit = {},
    selectedCurrencyCode: String = "",
    analyticsManager: AnalyticsManager? = null,
    showTutorial: Boolean = false,
    onTutorialComplete: () -> Unit = {}
) {
    val drawerState = LocalDrawerState.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showSearchSheet by rememberSaveable { mutableStateOf(false) }

    // Delete flow for search results
    var pendingDeleteUri by remember { mutableStateOf<Uri?>(null) }
    var showGuestDeleteDialog by remember { mutableStateOf(false) }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingDeleteUri?.let { onDeleteSearchResult(it) }
            pendingDeleteUri = null
        }
    }

    fun requestDelete(uri: Uri) {
        if (isGuest) {
            showGuestDeleteDialog = true
            return
        }
        pendingDeleteUri = uri
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pi = MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
            deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        } else {
            context.contentResolver.delete(uri, null, null)
            onDeleteSearchResult(uri)
            pendingDeleteUri = null
        }
    }

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
                    if (isGuest) {
                        item {
                            GuestBanner(
                                onSignIn = {
                                    navController.navigate(Screen.Login.route) {
                                        popUpTo(Screen.Dashboard.route) { inclusive = false }
                                    }
                                }
                            )
                        }
                    }
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
                        SavingsCalculatorCard(reclaimableBytes = totalReclaimable, overrideCurrencyCode = selectedCurrencyCode)
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
                        Spacer(modifier = Modifier.height(12.dp))
                        ShareAppCard()
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
                        error = searchError,
                        onDeleteRequest = { uri -> requestDelete(uri) }
                    )
                }
            }
        }
    }

    if (showGuestDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showGuestDeleteDialog = false },
            icon = {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    "Sign in to delete",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    "Deleting files is only available to signed-in users. Sign in with Google to unlock delete and other premium actions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(onClick = {
                    showGuestDeleteDialog = false
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = false }
                    }
                }) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Sign in with Google")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGuestDeleteDialog = false }) {
                    Text("Not now")
                }
            }
        )
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
    val suggestions = listOf("Screenshots", "WhatsApp", "Camera", "Downloads", "Telegram", "Instagram")
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
fun ShareAppCard() {
    val context = LocalContext.current
    val shareMessage = stringResource(R.string.share_app_message)
    val chooserTitle = stringResource(R.string.share_app_chooser_title)

    Card(
        modifier = Modifier.fillMaxWidth().clickable {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareMessage)
            }
            context.startActivity(Intent.createChooser(sendIntent, chooserTitle))
        },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.share_app_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    stringResource(R.string.share_app_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun SavingsCalculatorCard(reclaimableBytes: Long, overrideCurrencyCode: String = "") {
    val locale = Locale.getDefault()
    val currency = try {
        if (overrideCurrencyCode.isNotEmpty()) Currency.getInstance(overrideCurrencyCode)
        else Currency.getInstance(locale)
    } catch (_: Exception) {
        Currency.getInstance("USD")
    }
    
    // Approximate local cloud storage cost per GB (based on Google One 100 GB plan / 100)
    val costPerGb = when (currency.currencyCode) {
        // Americas
        "USD" -> 0.03   // $2.99 / 100 GB
        "CAD" -> 0.04   // CA$3.99 / 100 GB
        "MXN" -> 0.49   // MX$49 / 100 GB
        "BRL" -> 0.04   // R$3.99 / 100 GB
        "ARS" -> 3.00   // AR$299 / 100 GB
        "CLP" -> 30.0   // CLP 2,990 / 100 GB
        "COP" -> 130.0  // COP 12,900 / 100 GB
        "PEN" -> 0.11   // PEN 10.99 / 100 GB
        // Europe
        "EUR" -> 0.02   // €0.99–1.99 / 100 GB (avg ~€1.99)
        "GBP" -> 0.016  // £1.59 / 100 GB
        "CHF" -> 0.011  // CHF 1.09 / 100 GB
        "SEK" -> 0.29   // SEK 29 / 100 GB
        "NOK" -> 0.29   // NOK 29 / 100 GB
        "DKK" -> 0.15   // DKK 15 / 100 GB
        "PLN" -> 0.05   // PLN 4.99 / 100 GB
        "CZK" -> 0.49   // CZK 49 / 100 GB
        "HUF" -> 9.90   // HUF 990 / 100 GB
        "RON" -> 0.05   // RON 4.99 / 100 GB
        "BGN" -> 0.04   // BGN 3.99 / 100 GB
        "HRK" -> 0.15   // HRK 14.99 / 100 GB (pre-EUR adoption)
        "TRY" -> 0.29   // TRY 29 / 100 GB
        "RUB" -> 0.69   // RUB 69 / 100 GB
        "UAH" -> 0.29   // UAH 29 / 100 GB
        // Asia-Pacific
        "INR" -> 1.30   // ₹130 / 100 GB
        "JPY" -> 2.50   // ¥250 / 100 GB
        "CNY" -> 0.20   // ¥20 / 100 GB
        "KRW" -> 39.0   // ₩3,900 / 100 GB
        "AUD" -> 0.045  // A$4.49 / 100 GB
        "NZD" -> 0.05   // NZ$4.99 / 100 GB
        "SGD" -> 0.04   // S$3.98 / 100 GB
        "HKD" -> 0.23   // HK$23 / 100 GB
        "TWD" -> 0.90   // NT$90 / 100 GB
        "MYR" -> 0.13   // RM13 / 100 GB
        "THB" -> 0.35   // ฿35 / 100 GB
        "IDR" -> 490.0  // Rp49,000 / 100 GB
        "PHP" -> 1.60   // ₱160 / 100 GB
        "VND" -> 750.0  // ₫75,000 / 100 GB
        "PKR" -> 8.49   // PKR 849 / 100 GB
        "BDT" -> 3.49   // BDT 349 / 100 GB
        // Middle East & Africa
        "SAR" -> 0.11   // SAR 10.99 / 100 GB
        "AED" -> 0.11   // AED 10.99 / 100 GB
        "ILS" -> 0.11   // ILS 10.99 / 100 GB
        "EGP" -> 0.49   // EGP 49 / 100 GB
        "NGN" -> 19.0   // NGN 1,900 / 100 GB
        "KES" -> 1.10   // KES 109 / 100 GB
        "ZAR" -> 0.45   // ZAR 45 / 100 GB
        else  -> 0.03   // USD fallback
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
        Row {
            ScanCategoryCard(
                title = "Voice Storage",
                count = "Search & delete by voice",
                icon = Icons.Default.Mic,
                color = UIConstants.ColorIconPalette,
                modifier = Modifier.fillMaxWidth(),
                onClick = { navController.navigate(Screen.VoiceStorage.route) }
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

    data class NavEntry(val icon: ImageVector, val labelRes: Int, val route: String)
    val items = listOf(
        NavEntry(Icons.Default.GridView,    R.string.nav_dash,     Screen.Dashboard.route),
        NavEntry(Icons.Default.Search,      R.string.nav_scan,     Screen.Cleanup.route),
        NavEntry(Icons.Default.Description, R.string.nav_files,    Screen.FileBrowser.route),
        NavEntry(Icons.Default.Videocam,    R.string.nav_video,    Screen.VideoScanner.route),
        NavEntry(Icons.Default.Settings,    R.string.nav_settings, Screen.Settings.route),
    )

    val isDark   = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primary  = MaterialTheme.colorScheme.primary

    // ── Glass colour tokens ────────────────────────────────────────────────
    val glassBase    = if (isDark) Color(0xFF1C1C1E).copy(alpha = 0.78f) else Color(0xFFF5F5F7).copy(alpha = 0.82f)
    val topSheen     = if (isDark) Color.White.copy(alpha = 0.09f)       else Color.White.copy(alpha = 0.60f)
    val bottomTint   = if (isDark) Color.Black.copy(alpha = 0.10f)       else Color.Black.copy(alpha = 0.04f)
    val borderBright = if (isDark) Color.White.copy(alpha = 0.22f)       else Color.White.copy(alpha = 0.90f)
    val borderDim    = if (isDark) Color.White.copy(alpha = 0.04f)       else Color.White.copy(alpha = 0.14f)
    val shimmer      = if (isDark) Color.White.copy(alpha = 0.075f)      else Color.White.copy(alpha = 0.45f)
    val shadowColor  = if (isDark) Color.Black.copy(alpha = 0.55f)       else Color.Black.copy(alpha = 0.12f)

    // ── Continuous glass animations ────────────────────────────────────────
    val glass = rememberInfiniteTransition(label = "glass")

    // Slow diagonal light sweep — like sunlight crossing a glass surface
    val sweep by glass.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )
    // Specular highlight breathing — top edge gently pulses in brightness
    val specular by glass.animateFloat(
        initialValue  = 0.55f,
        targetValue   = 1.00f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "specular"
    )

    val barHeight    = 72.dp
    val pillHeight   = 46.dp
    val pillWidth    = 52.dp
    val cornerRadius = 28.dp

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .navigationBarsPadding()
    ) {
        val itemWidth = maxWidth / items.size

        val pillOffsetX by animateDpAsState(
            targetValue   = itemWidth * selectedIndex + (itemWidth - pillWidth) / 2,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness    = Spring.StiffnessMediumLow
            ),
            label = "pill"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation    = 36.dp,
                    shape        = RoundedCornerShape(cornerRadius),
                    clip         = false,
                    ambientColor = shadowColor,
                    spotColor    = shadowColor
                )
                .clip(RoundedCornerShape(cornerRadius))
                .drawWithContent {
                    val w = size.width
                    val h = size.height

                    // ── 1. Frosted solid base ───────────────────────────────
                    drawRect(glassBase)

                    // ── 2. Vertical inner sheen (top light → bottom dark) ───
                    drawRect(
                        Brush.verticalGradient(listOf(topSheen, Color.Transparent, bottomTint))
                    )

                    // ── 3. Subtle radial centre glow for depth ───────────────
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (isDark) 0.04f else 0.18f),
                                Color.Transparent
                            ),
                            center = Offset(w / 2f, h / 2f),
                            radius = w * 0.55f
                        )
                    )

                    // ── 4. Pill + icon content ──────────────────────────────
                    drawContent()

                    // ── 5. Animated diagonal shimmer sweep on top ───────────
                    val sw = w * 0.30f
                    val sx = -sw + (w + sw * 2f) * sweep
                    drawRect(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                shimmer,
                                shimmer.copy(alpha = shimmer.alpha * 0.45f),
                                Color.Transparent
                            ),
                            start = Offset(sx, 0f),
                            end   = Offset(sx + sw, h)
                        )
                    )

                    // ── 6. Specular top line — animated brightness ───────────
                    val specAlpha = borderBright.alpha * specular
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                borderBright.copy(alpha = specAlpha * 0.5f),
                                borderBright.copy(alpha = specAlpha),
                                borderBright.copy(alpha = specAlpha),
                                borderBright.copy(alpha = specAlpha * 0.5f),
                                Color.Transparent
                            )
                        ),
                        start       = Offset(0f, 0.8f),
                        end         = Offset(w, 0.8f),
                        strokeWidth = 1.5f
                    )

                    // ── 7. Perimeter border (bright top → dim bottom) ────────
                    val bs = 1.4f
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                borderBright.copy(alpha = borderBright.alpha * specular),
                                borderDim
                            )
                        ),
                        topLeft      = Offset(bs / 2f, bs / 2f),
                        size         = Size(w - bs, h - bs),
                        cornerRadius = CornerRadius(cornerRadius.toPx()),
                        style        = Stroke(width = bs)
                    )
                }
        ) {
            // ── Glass selection pill (glass-within-glass) ───────────────────
            Box(
                modifier = Modifier
                    .padding(vertical = (barHeight - pillHeight) / 2)
                    .offset(x = pillOffsetX)
                    .width(pillWidth)
                    .height(pillHeight)
                    .clip(RoundedCornerShape(14.dp))
                    .drawWithContent {
                        val w = size.width
                        val h = size.height
                        drawRect(primary.copy(alpha = if (isDark) 0.26f else 0.14f))
                        drawRect(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = if (isDark) 0.18f else 0.42f),
                                    Color.Transparent
                                )
                            )
                        )
                        drawContent()
                        val ps = 1.0f
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = if (isDark) 0.28f else 0.65f),
                                    Color.White.copy(alpha = if (isDark) 0.06f else 0.12f)
                                )
                            ),
                            topLeft      = Offset(ps / 2f, ps / 2f),
                            size         = Size(w - ps, h - ps),
                            cornerRadius = CornerRadius(14.dp.toPx()),
                            style        = Stroke(width = ps)
                        )
                    }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, entry ->
                    GlassNavItem(
                        icon     = entry.icon,
                        label    = stringResource(entry.labelRes),
                        selected = selectedIndex == index,
                        onClick  = { navController.navigate(entry.route) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val iconColor by animateColorAsState(
        targetValue   = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.40f),
        animationSpec = tween(200),
        label         = "navColor"
    )
    // Radial glow fades in on selection, fades out on deselection
    val glowAlpha by animateFloatAsState(
        targetValue   = if (selected) 1f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label         = "glow"
    )
    val scale by animateFloatAsState(
        targetValue   = when {
            isPressed -> 0.78f
            selected  -> 1.14f
            else      -> 1.00f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier
                .size(36.dp)
                .scale(scale)
                .drawBehind {
                    if (glowAlpha > 0f) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    iconColor.copy(alpha = 0.30f * glowAlpha),
                                    iconColor.copy(alpha = 0.10f * glowAlpha),
                                    Color.Transparent
                                ),
                                radius = size.minDimension * 0.8f
                            )
                        )
                    }
                }
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = label,
                tint               = iconColor,
                modifier           = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color      = iconColor
            )
        )
    }
}

@Composable
fun ImageSearchContent(
    query: String,
    results: List<ImageSearchRepository.SearchResult>,
    isSearching: Boolean,
    progress: Pair<Int, Int>,
    error: String?,
    onDeleteRequest: (Uri) -> Unit = {}
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
                        ImageSearchResultItem(result, onDeleteRequest = onDeleteRequest)
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageSearchResultItem(
    result: ImageSearchRepository.SearchResult,
    onDeleteRequest: (Uri) -> Unit = {}
) {
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

        // Bottom label bar
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

        // Delete button — top-right corner
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(26.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable { onDeleteRequest(result.uri) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.DeleteOutline,
                contentDescription = "Delete",
                tint = Color.White,
                modifier = Modifier.size(15.dp)
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

@Composable
private fun GuestBanner(onSignIn: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Guest mode",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "Sign in to delete duplicates and merge contacts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onSignIn) {
                Text("Sign in", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            }
        }
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
