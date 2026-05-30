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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.rp.dedup.core.search.SmartJunkRepository
import com.rp.dedup.core.model.SmartJunkState
import com.rp.dedup.core.viewmodels.SmartJunkViewModel
import com.rp.dedup.ui.theme.DeDupTheme
import com.rp.dedup.core.ui.DeDupTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartJunkScreen(navController: NavHostController) {
    val context = LocalContext.current
    val viewModel: SmartJunkViewModel = viewModel()
    val state by viewModel.uiState.collectAsState()

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
        }
    }

    Scaffold(
        topBar = {
            DeDupTopBar(
                title = "Smart AI Cleanup",
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            val results = state as? SmartJunkState.Results
            if (results != null && results.selectedUris.isNotEmpty()) {
                Surface(shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${results.selectedUris.size} files selected",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = { triggerDelete(results.selectedUris.toList()) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Delete Selected")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                is SmartJunkState.Idle -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text("AI Image Cleanup", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Let AI find blurry, meme, and junk images for you.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(32.dp))
                        Button(onClick = { viewModel.startScan() }) {
                            Text("Start Scanning")
                        }
                    }
                }
                is SmartJunkState.Scanning -> ScanningView(s.progress)
                is SmartJunkState.Results -> ResultsView(
                    results = s,
                    onToggleFile = { viewModel.toggleSelection(it) },
                    onToggleCategory = { viewModel.toggleCategoryExpansion(it) },
                    onSelectAllInCategory = { viewModel.selectAllInCategory(it) },
                    onDeselectAllInCategory = { viewModel.deselectAllInCategory(it) }
                )
                is SmartJunkState.Error -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    Text(s.message, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.startScan() }) { Text("Retry") }
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
        CircularProgressIndicator(modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(24.dp))
        Text("AI is analyzing your gallery...", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(0.6f).clip(CircleShape)
        )
        Spacer(Modifier.height(8.dp))
        Text("${(progress * 100).toInt()}% complete", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ResultsView(
    results: SmartJunkState.Results,
    onToggleFile: (Uri) -> Unit,
    onToggleCategory: (SmartJunkRepository.JunkCategory) -> Unit,
    onSelectAllInCategory: (SmartJunkRepository.JunkCategory) -> Unit,
    onDeselectAllInCategory: (SmartJunkRepository.JunkCategory) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        results.groups.forEach { (category, items) ->
            item {
                JunkCategorySection(
                    category = category,
                    items = items,
                    selectedUris = results.selectedUris,
                    isExpanded = results.expandedCategories.contains(category),
                    onToggleFile = onToggleFile,
                    onToggleExpand = { onToggleCategory(category) },
                    onSelectAll = { onSelectAllInCategory(category) },
                    onDeselectAll = { onDeselectAllInCategory(category) }
                )
            }
        }
    }
}

@Composable
private fun JunkCategorySection(
    category: SmartJunkRepository.JunkCategory,
    items: List<SmartJunkRepository.JunkItem>,
    selectedUris: Set<Uri>,
    isExpanded: Boolean,
    onToggleFile: (Uri) -> Unit,
    onToggleExpand: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit
) {
    val selectedInCat = items.count { it.uri in selectedUris }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onToggleExpand() }
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(category.color.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(category.icon, contentDescription = null, tint = category.color, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(category.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${items.size} items · $selectedInCat selected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { if (selectedInCat == items.size) onDeselectAll() else onSelectAll() }) {
                    Icon(
                        if (selectedInCat == items.size) Icons.Default.CheckCircle else Icons.Default.SelectAll,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }

            if (isExpanded) {
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(items) { _, item ->
                        JunkItemThumbnail(
                            item = item,
                            isSelected = item.uri in selectedUris,
                            onToggle = { onToggleFile(item.uri) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JunkItemThumbnail(
    item: SmartJunkRepository.JunkItem,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(100.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.error else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onToggle() }
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
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SmartJunkScreenPreview() {
    DeDupTheme {
        val navController = rememberNavController()
        SmartJunkScreen(navController = navController)
    }
}
