package com.rp.dedup.screens

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.rp.dedup.R
import com.rp.dedup.core.model.SmartJunkState
import com.rp.dedup.core.search.SmartJunkRepository
import com.rp.dedup.core.ui.DeDupTopBar
import com.rp.dedup.core.viewmodels.SmartJunkViewModel
import com.rp.dedup.ui.theme.DeDupTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
                title = stringResource(R.string.screen_smart_cleanup),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
                            stringResource(R.string.delete_selected_btn, results.selectedUris.size, ""),
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
                        Text(stringResource(R.string.ai_image_cleanup), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.ai_image_cleanup_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(32.dp))
                        Button(onClick = { viewModel.startScan() }) {
                            Text(stringResource(R.string.start_scanning))
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
                    Button(onClick = { viewModel.startScan() }) { Text(stringResource(R.string.retry)) }
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
        CircularProgressIndicator(progress = { progress })
        Spacer(Modifier.height(16.dp))
        Text("AI is analyzing your images...", style = MaterialTheme.typography.bodyMedium)
        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        results.groups.forEach { (category, items) ->
            item {
                JunkCategorySection(
                    category = category,
                    items = items,
                    selectedUris = results.selectedUris,
                    isExpanded = results.expandedCategories.contains(category),
                    onToggleFile = onToggleFile,
                    onToggleCategory = { onToggleCategory(category) },
                    onSelectAll = { onSelectAllInCategory(category) },
                    onDeselectAll = { onDeselectAllInCategory(category) }
                )
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JunkCategorySection(
    category: SmartJunkRepository.JunkCategory,
    items: List<SmartJunkRepository.JunkItem>,
    selectedUris: Set<Uri>,
    isExpanded: Boolean,
    onToggleFile: (Uri) -> Unit,
    onToggleCategory: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit
) {
    val categorySelectedCount = items.count { selectedUris.contains(it.uri) }
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleCategory() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isExpanded) Icons.Default.ExpandMore else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(category.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${items.size} items · $categorySelectedCount selected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isExpanded) {
                TextButton(onClick = if (categorySelectedCount == items.size) onDeselectAll else onSelectAll) {
                    Text(if (categorySelectedCount == items.size) "Deselect All" else "Select All")
                }
            }
        }
        
        if (isExpanded) {
            FlowRow(
                modifier = Modifier.padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items.forEach { item ->
                    JunkItemThumbnail(
                        item = item,
                        isSelected = selectedUris.contains(item.uri),
                        onToggle = { onToggleFile(item.uri) }
                    )
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
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
            .clickable { onToggle() }
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        if (isSelected) {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)))
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SmartJunkScreenPreview() {
    DeDupTheme {
        SmartJunkScreen(navController = NavHostController(LocalContext.current))
    }
}
