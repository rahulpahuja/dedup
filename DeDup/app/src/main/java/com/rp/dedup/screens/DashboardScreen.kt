package com.rp.dedup.screens

import android.net.Uri
import android.text.format.Formatter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.rp.dedup.LocalDrawerState
import com.rp.dedup.Screen
import com.rp.dedup.UIConstants
import com.rp.dedup.core.db.AppDatabase
import com.rp.dedup.core.repository.ScanHistoryRepository
import com.rp.dedup.core.search.ImageSearchRepository
import com.rp.dedup.core.viewmodels.DashboardViewModel
import com.rp.dedup.core.viewmodels.ImageSearchViewModel
import com.rp.dedup.core.viewmodels.StorageStats
import com.rp.dedup.ui.theme.DeDupTheme
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavHostController) {
    val context = LocalContext.current
    val dashboardViewModel: DashboardViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DashboardViewModel(
                    ScanHistoryRepository(AppDatabase.getDatabase(context).scanHistoryDao())
                ) as T
            }
        }
    )
    val searchViewModel: ImageSearchViewModel = viewModel(
        factory = ImageSearchViewModel.Factory(context)
    )

    val storageStats by dashboardViewModel.storageStats.collectAsState()
    val totalReclaimable by dashboardViewModel.totalReclaimableBytes.collectAsState()
    val searchResults by searchViewModel.results.collectAsState()
    val isSearching by searchViewModel.isSearching.collectAsState()
    val searchProgress by searchViewModel.progress.collectAsState()
    val searchError by searchViewModel.error.collectAsState()

    val drawerState = LocalDrawerState.current
    val scope = rememberCoroutineScope()

    var searchQuery by rememberSaveable() { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (!searchActive) {
                TopAppBar(
                    title = {
                        Text(
                            UIConstants.APP_NAME,
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
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // ── Normal dashboard content (hidden while search is active) ───────
            if (!searchActive) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    // Leave room at the top for the floating SearchBar
                    contentPadding = PaddingValues(top = 72.dp, bottom = 16.dp)
                ) {
                    item {
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
                        StorageSummaryCard(
                            stats = storageStats,
                            reclaimableBytes = totalReclaimable
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                    item {
                        SavingsCalculatorCard(reclaimableBytes = totalReclaimable)
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
                        OptimizationSection(navController)
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }

            // ── Semantic search bar (floats at the top) ────────────────────────
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onSearch = { searchViewModel.search(it) },
                        expanded = searchActive,
                        onExpandedChange = { active ->
                            searchActive = active
                            if (!active) { searchQuery = ""; searchViewModel.clear() }
                        },
                        placeholder = {
                            Text(
                                "Find my image wearing a red shirt…",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.ImageSearch,
                                contentDescription = "Image search"
                            )
                        },
                        trailingIcon = {
                            if (searchActive) {
                                IconButton(onClick = {
                                    searchActive = false
                                    searchQuery = ""
                                    searchViewModel.clear()
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close search")
                                }
                            }
                        }
                    )
                },
                expanded = searchActive,
                onExpandedChange = { active ->
                    searchActive = active
                    if (!active) { searchQuery = ""; searchViewModel.clear() }
                },
                modifier = if (searchActive) Modifier.fillMaxWidth()
                           else Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // ── Search results shown inside expanded SearchBar ─────────────
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

@Composable
fun SavingsCalculatorCard(reclaimableBytes: Long) {
    val locale = Locale.getDefault()
    val currency = try {
        Currency.getInstance(locale)
    } catch (_: Exception) {
        Currency.getInstance("USD")
    }
    
    // Average cost of storage per GB (estimate)
    val costPerGb = when (currency.currencyCode) {
        "INR" -> 5.0  // ₹5 per GB
        "EUR" -> 0.05 // €0.05 per GB
        "GBP" -> 0.04 // £0.04 per GB
        else  -> 0.06 // $0.06 per GB default
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
                    "Potential Savings",
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
    reclaimableBytes: Long = 0L
) {
    val context = LocalContext.current

    // Animate both bars on first composition
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
    else "Calculating…"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {

            // ── Header ─────────────────────────────────────
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
                        usedLabel,
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

            Spacer(modifier = Modifier.height(20.dp))

            // ── Device storage bar ─────────────────────────
            Text(
                "DEVICE STORAGE",
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
                else "Loading storage info…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f)
            )

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(20.dp))

            // ── DeDup savings bar ──────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "DEDUP SAVINGS",
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
                    "${"%.1f".format(savingsFraction * 100)}% of used storage identified as reclaimable"
                else "Run a scan to identify reclaimable space",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f)
            )
        }
    }
}

@Composable
fun QuickScanGrid(navController: NavHostController) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanCategoryCard(
                title = UIConstants.QUICK_SCAN_IMAGES,
                count = "1.2k",
                icon = Icons.Default.Image,
                color = UIConstants.ColorImages,
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate(Screen.ResultsContacts.route) }
            )
            ScanCategoryCard(
                title = UIConstants.QUICK_SCAN_VIDEOS,
                count = "45",
                icon = Icons.Default.Videocam,
                color = UIConstants.ColorVideos,
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate(Screen.VideoScanner.route) }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanCategoryCard(
                title = UIConstants.QUICK_SCAN_DOCUMENTS,
                count = "230",
                icon = Icons.Default.Description,
                color = UIConstants.ColorDocuments,
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate(Screen.Cleanup.route) }
            )
            ScanCategoryCard(
                title = UIConstants.QUICK_SCAN_APKS,
                count = "12",
                icon = Icons.Default.Android,
                color = UIConstants.ColorApks,
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate(Screen.FileScanner.createRoute("apk")) }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanCategoryCard(
                title = UIConstants.QUICK_SCAN_BROWSE_FILES,
                count = "All",
                icon = Icons.Default.FolderOpen,
                color = UIConstants.ColorBrowseFiles,
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate(Screen.FileBrowser.route) }
            )
            ScanCategoryCard(
                title = UIConstants.QUICK_SCAN_HISTORY,
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
fun OptimizationSection(navController: NavHostController) {
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
            description = "Remove temporary app data to free up space",
            icon = Icons.Default.CleaningServices,
            isOptimized = false,
            onClick = { navController.navigate(Screen.CacheCleaner.route) }
        )
        Spacer(modifier = Modifier.height(12.dp))
        OptimizationCard(
            title = "Large File Review",
            description = "Analyze 4 files larger than 500 MB",
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

    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(
            icon = { Icon(Icons.Default.GridView, contentDescription = null) },
            label = { Text(UIConstants.NAV_LABEL_DASH) },
            selected = selectedIndex == 0,
            onClick = { navController.navigate(Screen.Dashboard.route) }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Search, contentDescription = null) },
            label = { Text(UIConstants.NAV_LABEL_SCAN) },
            selected = selectedIndex == 1,
            onClick = { navController.navigate(Screen.Cleanup.route) }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Description, contentDescription = null) },
            label = { Text(UIConstants.NAV_LABEL_FILES) },
            selected = selectedIndex == 2,
            onClick = { navController.navigate(Screen.FileScanner.createRoute("pdf")) }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Videocam, contentDescription = null) },
            label = { Text(UIConstants.NAV_LABEL_VIDEO) },
            selected = selectedIndex == 3,
            onClick = { navController.navigate(Screen.VideoScanner.route) }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text(UIConstants.NAV_LABEL_SETTINGS) },
            selected = selectedIndex == 4,
            onClick = { navController.navigate(Screen.Settings.route) }
        )
    }
}

// ── Semantic search results ───────────────────────────────────────────────────

@Composable
fun ImageSearchContent(
    query: String,
    results: List<ImageSearchRepository.SearchResult>,
    isSearching: Boolean,
    progress: Pair<Int, Int>,
    error: String?
) {
    when {
        // Nothing typed yet
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
                        "Describe what you're looking for",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "e.g. \"wearing a red shirt\" or \"sunset beach\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // Searching — show progress
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
                    if (total > 0) "Analyzing images… $labeled / $total"
                    else "Analyzing images…",
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

        // Error
        error != null -> {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }
        }

        // No matches
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
                        "No matching images found",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Try different words or check gallery permissions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // Results grid
        else -> {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    "${results.size} image${if (results.size != 1) "s" else ""} matched",
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
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
    ) {
        AsyncImage(
            model = result.uri,
            contentDescription = result.matchedLabels.joinToString(),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        // Matched label chips at the bottom
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
}

@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
fun DashboardScreenPreview() {
    DeDupTheme {
        DashboardScreen(rememberNavController())
    }
}
