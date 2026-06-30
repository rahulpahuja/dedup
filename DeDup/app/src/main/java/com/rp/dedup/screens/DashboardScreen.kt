package com.rp.dedup.screens

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.canopas.lib.showcase.IntroShowcase
import com.canopas.lib.showcase.component.ShowcaseStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.DrawerValue
import androidx.compose.runtime.CompositionLocalProvider
import com.rp.dedup.LocalDrawerState
import com.rp.dedup.R
import com.rp.dedup.Screen
import com.rp.dedup.core.analytics.AnalyticsManager
import com.rp.dedup.core.caching.DataStoreManager
import com.rp.dedup.core.model.MediaCounts
import com.rp.dedup.core.model.StorageStats
import com.rp.dedup.core.search.ImageSearchRepository
import com.rp.dedup.core.viewmodels.DashboardViewModel
import com.rp.dedup.core.viewmodels.ImageSearchViewModel
import com.rp.dedup.core.viewmodels.SettingsViewModel
import com.rp.dedup.core.viewmodels.UserProfileViewModel
import com.rp.dedup.screens.dashboard.components.*
import com.rp.dedup.ui.theme.DeDupTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavHostController,
    profileViewModel: UserProfileViewModel
) {
    val context = LocalContext.current
    val dashboardViewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory(context))
    val searchViewModel: ImageSearchViewModel  = viewModel(factory = ImageSearchViewModel.Factory(context))
    val analyticsManager = remember { AnalyticsManager.getInstance(context) }

    val dataStoreManager = remember { DataStoreManager(context) }
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(dataStoreManager))
    val selectedCurrency by settingsViewModel.selectedCurrency.collectAsState()

    val storageStats     by dashboardViewModel.storageStats.collectAsState()
    val totalReclaimable by dashboardViewModel.totalReclaimableBytes.collectAsState()
    val mediaCounts      by dashboardViewModel.mediaCounts.collectAsState()
    val searchResults    by searchViewModel.results.collectAsState()
    val isSearching      by searchViewModel.isSearching.collectAsState()
    val searchProgress   by searchViewModel.progress.collectAsState()
    val searchError      by searchViewModel.error.collectAsState()

    val tutorialShown by dataStoreManager.readData(DataStoreManager.TUTORIAL_SHOWN, false)
        .collectAsState(initial = true)

    LaunchedEffect(Unit) { analyticsManager.logScreenView("Dashboard") }
    LaunchedEffect(tutorialShown) {
        if (!tutorialShown) analyticsManager.logTutorialInteraction("DASHBOARD", "VIEWED")
    }

    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) dashboardViewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DashboardScreenContent(
        navController        = navController,
        userName             = profileViewModel.name,
        userImageUrl         = profileViewModel.profileImageUrl,
        isGuest              = profileViewModel.isGuest,
        storageStats         = storageStats,
        totalReclaimable     = totalReclaimable,
        mediaCounts          = mediaCounts,
        searchResults        = searchResults,
        isSearching          = isSearching,
        searchProgress       = searchProgress,
        searchError          = searchError,
        onSearch             = { searchViewModel.search(it) },
        onClearSearch        = { searchViewModel.clear() },
        onDeleteSearchResult = { searchViewModel.removeDeletedResult(it) },
        analyticsManager     = analyticsManager,
        showTutorial         = !tutorialShown,
        selectedCurrencyCode = selectedCurrency,
        onTutorialComplete   = {
            analyticsManager.logTutorialInteraction("DASHBOARD", "COMPLETED")
            coroutineScope.launch { dataStoreManager.writeData(DataStoreManager.TUTORIAL_SHOWN, true) }
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
    var pendingDeleteUri by remember { mutableStateOf<Uri?>(null) }
    var pendingDeleteFileSize by remember { mutableStateOf(0L) }
    var showGuestDeleteDialog by remember { mutableStateOf(false) }

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            analyticsManager?.logFilesDeleted("DASHBOARD_SEARCH", 1, pendingDeleteFileSize)
            pendingDeleteUri?.let { onDeleteSearchResult(it) }
            pendingDeleteUri = null; pendingDeleteFileSize = 0L
        }
    }

    fun requestDelete(uri: Uri) {
        if (isGuest) { showGuestDeleteDialog = true; return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pendingDeleteUri = uri
            scope.launch {
                pendingDeleteFileSize = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getLong(0) else 0L } ?: 0L
                }
            }
            val pi = MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
            deleteLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(pi.intentSender).build())
        } else {
            val size = context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getLong(0) else 0L } ?: 0L
            context.contentResolver.delete(uri, null, null)
            analyticsManager?.logFilesDeleted("DASHBOARD_SEARCH", 1, size)
            onDeleteSearchResult(uri)
        }
    }

    val tutorialStyle = ShowcaseStyle.Default.copy(
        backgroundColor = Color(0xFF090F20), backgroundAlpha = 0.97f, targetCircleColor = Color(0xFF5FA3FF)
    )

    IntroShowcase(showIntroShowCase = showTutorial, dismissOnClickOutside = true, onShowCaseCompleted = onTutorialComplete) {
        Scaffold(
            topBar = {
                DashboardTopBar(
                    userName = userName,
                    userImageUrl = userImageUrl,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onSearchClick = { showSearchSheet = true },
                    searchButtonModifier = Modifier.introShowCaseTarget(
                        index = 0,
                        style = tutorialStyle,
                        content = { TutorialTooltip(stringResource(R.string.tut_search_title), stringResource(R.string.tut_search_body)) }
                    )
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (isGuest) {
                    item {
                        GuestBanner(onSignIn = {
                            navController.navigate(Screen.Login.route) {
                                popUpTo(Screen.Dashboard.route) { inclusive = false }
                            }
                        })
                    }
                }
                item {
                    Text(stringResource(R.string.dashboard_title),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground))
                    Spacer(Modifier.height(16.dp))
                    AiAssistantCard(onClick = { navController.navigate(Screen.VoiceStorage.route) })
                    Spacer(Modifier.height(24.dp))
                }
                item {
                    StorageSummaryCard(
                        stats = storageStats,
                        reclaimableBytes = totalReclaimable,
                        onClick = { analyticsManager?.logTreemapViewed(); navController.navigate(Screen.BigFileMap.route) },
                        modifier = Modifier.introShowCaseTarget(index = 1, style = tutorialStyle,
                            content = { TutorialTooltip(stringResource(R.string.tut_storage_title), stringResource(R.string.tut_storage_body)) })
                    )
                    Spacer(Modifier.height(24.dp))
                }
                item {
                    SavingsCalculatorCard(reclaimableBytes = totalReclaimable, overrideCurrencyCode = selectedCurrencyCode)
                    Spacer(Modifier.height(32.dp))
                }
                item {
                    Text(stringResource(R.string.quick_scan_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground))
                    Spacer(Modifier.height(16.dp))
                    QuickScanGrid(
                        navController = navController,
                        mediaCounts = mediaCounts,
                        modifier = Modifier.introShowCaseTarget(index = 2, style = tutorialStyle,
                            content = { TutorialTooltip(stringResource(R.string.tut_quick_scan_title), stringResource(R.string.tut_quick_scan_body)) })
                    )
                    Spacer(Modifier.height(32.dp))
                }
                item {
                    OptimizationSection(
                        navController = navController,
                        modifier = Modifier.introShowCaseTarget(index = 3, style = tutorialStyle,
                            content = { TutorialTooltip(stringResource(R.string.tut_opt_tips_title), stringResource(R.string.tut_opt_tips_body)) })
                    )
                    Spacer(Modifier.height(24.dp))
                }
                item {
                    SmartAiCleanupCard(onClick = { analyticsManager?.logSmartCleanupViewed(); navController.navigate(Screen.SmartJunk.route) })
                    Spacer(Modifier.height(12.dp))
                    DeepOptimizationCard(onClick = { navController.navigate(Screen.DeepOptimization.route) })
                    Spacer(Modifier.height(12.dp))
                    ContactDedupCard(onClick = { navController.navigate(Screen.ContactDedup.route) })
                    Spacer(Modifier.height(12.dp))
                    ShareAppCard()
                    Spacer(Modifier.height(32.dp))
                }
            }
        }

        if (showSearchSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showSearchSheet = false; searchQuery = ""; onClearSearch() },
                sheetState = sheetState
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null,
                            tint = Color(0xFF9C6FFF), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.search_placeholder),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.search_placeholder)) },
                        leadingIcon = { Icon(Icons.Default.ImageSearch, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = ""; onClearSearch() }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_search))
                            }
                        },
                        singleLine = true,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch(searchQuery) })
                    )
                    if (searchQuery.isEmpty()) {
                        SearchSuggestionsRow { suggestion -> searchQuery = suggestion; onSearch(suggestion) }
                    } else {
                        LaunchedEffect(searchQuery) { onSearch(searchQuery) }
                    }
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
                icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) },
                title = { Text("Sign in to delete", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
                text = { Text("Deleting files is only available to signed-in users. Sign in with Google to unlock delete and other premium actions.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                confirmButton = {
                    Button(onClick = { showGuestDeleteDialog = false; navController.navigate(Screen.Login.route) { popUpTo(Screen.Dashboard.route) { inclusive = false } } }) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sign in with Google")
                    }
                },
                dismissButton = { TextButton(onClick = { showGuestDeleteDialog = false }) { Text("Not now") } }
            )
        }
    }
}

@Preview(showBackground = true, name = "Light Mode")
@Composable
fun DashboardScreenPreview() {
    DeDupTheme {
        CompositionLocalProvider(LocalDrawerState provides rememberDrawerState(DrawerValue.Closed)) {
            DashboardScreenContent(
                navController = rememberNavController(),
                userName = "John Doe",
                userImageUrl = "",
                storageStats = StorageStats(
                    totalBytes = 128L * 1024 * 1024 * 1024,
                    usedBytes  = 82L  * 1024 * 1024 * 1024,
                    freeBytes  = 46L  * 1024 * 1024 * 1024
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
