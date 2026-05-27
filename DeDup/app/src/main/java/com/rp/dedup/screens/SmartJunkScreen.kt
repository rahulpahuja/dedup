package com.rp.dedup.screens

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.rp.dedup.core.search.SmartJunkRepository
import com.rp.dedup.core.viewmodels.SmartJunkState
import com.rp.dedup.core.viewmodels.SmartJunkViewModel
import com.rp.dedup.ui.theme.PrimaryBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartJunkScreen(navController: NavHostController) {
    val viewModel: SmartJunkViewModel = viewModel()
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var pendingDeleteUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.removeDeletedItems(pendingDeleteUris)
            pendingDeleteUris = emptyList()
        }
    }

    fun triggerDelete(uris: List<Uri>) {
        if (uris.isEmpty()) return
        pendingDeleteUris = uris
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pi = MediaStore.createDeleteRequest(context.contentResolver, uris)
            deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        } else {
            uris.forEach { context.contentResolver.delete(it, null, null) }
            viewModel.removeDeletedItems(uris)
            pendingDeleteUris = emptyList()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.startScan()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "AI Cleanup Review",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    if (state is SmartJunkState.Results) {
                        val results = state as SmartJunkState.Results
                        if (results.selectedUris.isNotEmpty()) {
                            IconButton(onClick = { triggerDelete(results.selectedUris.toList()) }) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (state is SmartJunkState.Results) {
                val results = state as SmartJunkState.Results
                if (results.selectedUris.isNotEmpty()) {
                    BottomAppBar(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Text(
                            "${results.selectedUris.size} items selected",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { triggerDelete(results.selectedUris.toList()) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Clear Selected")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                is SmartJunkState.Idle -> {}
                is SmartJunkState.Scanning -> {
                    ScanningView(s.progress)
                }
                is SmartJunkState.Results -> {
                    ResultsView(
                        groups = s.groups,
                        selectedUris = s.selectedUris,
                        expandedCategories = s.expandedCategories,
                        onToggleSelection = { viewModel.toggleSelection(it) },
                        onSelectAll = { viewModel.selectAllInCategory(it) },
                        onDeselectAll = { viewModel.deselectAllInCategory(it) },
                        onToggleExpansion = { viewModel.toggleCategoryExpansion(it) }
                    )
                }
                is SmartJunkState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(s.message, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanningView(progress: Float) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = PrimaryBlue
        )
        Spacer(Modifier.height(24.dp))
        Text("AI is analyzing your gallery...", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(0.7f).height(8.dp).clip(CircleShape),
            color = PrimaryBlue
        )
        Spacer(Modifier.height(8.dp))
        Text("${(progress * 100).toInt()}% complete", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ResultsView(
    groups: Map<SmartJunkRepository.JunkCategory, List<SmartJunkRepository.JunkItem>>,
    selectedUris: Set<Uri>,
    expandedCategories: Set<SmartJunkRepository.JunkCategory>,
    onToggleSelection: (Uri) -> Unit,
    onSelectAll: (SmartJunkRepository.JunkCategory) -> Unit,
    onDeselectAll: (SmartJunkRepository.JunkCategory) -> Unit,
    onToggleExpansion: (SmartJunkRepository.JunkCategory) -> Unit
) {
    if (groups.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CheckCircle, null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("Your gallery is already optimized!", color = Color.Gray)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val categoryList = groups.keys.toList()
        items(categoryList) { category ->
            val items = groups[category] ?: emptyList()
            JunkCategorySection(
                category = category,
                items = items,
                selectedUris = selectedUris,
                isExpanded = category in expandedCategories,
                onToggleSelection = onToggleSelection,
                onSelectAll = { onSelectAll(category) },
                onDeselectAll = { onDeselectAll(category) },
                onToggleExpansion = { onToggleExpansion(category) }
            )
        }
    }
}

@Composable
private fun JunkCategorySection(
    category: SmartJunkRepository.JunkCategory,
    items: List<SmartJunkRepository.JunkItem>,
    selectedUris: Set<Uri>,
    isExpanded: Boolean,
    onToggleSelection: (Uri) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onToggleExpansion: () -> Unit
) {
    val categoryUris = items.map { it.uri }.toSet()
    val allSelected = categoryUris.all { it in selectedUris }

    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(category.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onToggleExpansion) {
                        Icon(
                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand"
                        )
                    }
                }
                Text(category.description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            
            TextButton(
                onClick = { if (allSelected) onDeselectAll() else onSelectAll() }
            ) {
                Text(if (allSelected) "Deselect All" else "Select All")
            }
        }
        
        Spacer(Modifier.height(12.dp))
        
        if (isExpanded) {
            // Display as Grid within the column
            // We use a custom flow-like layout or just manually chunk items since we're already inside a LazyColumn
            items.chunked(3).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { item ->
                        Box(modifier = Modifier.weight(1f)) {
                            JunkItemThumbnail(
                                item = item,
                                isSelected = item.uri in selectedUris,
                                onClick = { onToggleSelection(item.uri) }
                            )
                        }
                    }
                    // Add empty boxes if the last row isn't full
                    repeat(3 - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items) { item ->
                    val isSelected = item.uri in selectedUris
                    JunkItemThumbnail(
                        item = item,
                        isSelected = isSelected,
                        onClick = { onToggleSelection(item.uri) }
                    )
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.5f))
    }
}

@Composable
private fun JunkItemThumbnail(
    item: SmartJunkRepository.JunkItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(2.dp)
                )
            }
        }
    }
}
