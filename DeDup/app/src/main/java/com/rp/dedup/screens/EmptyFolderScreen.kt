package com.rp.dedup.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.rp.dedup.core.data.EmptyFolder
import com.rp.dedup.core.viewmodels.EmptyFolderState
import com.rp.dedup.core.viewmodels.EmptyFolderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmptyFolderScreen(navController: NavHostController) {
    val context = LocalContext.current
    val viewModel: EmptyFolderViewModel = viewModel(
        factory = EmptyFolderViewModel.factory(context)
    )
    val state by viewModel.state.collectAsState()
    val selectedPaths = remember { mutableStateSetOf<String>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Empty Folder Remover",
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
                    val results = state as? EmptyFolderState.Results
                    if (results != null && results.folders.isNotEmpty()) {
                        TextButton(onClick = {
                            if (selectedPaths.size == results.folders.size) {
                                selectedPaths.clear()
                            } else {
                                selectedPaths.addAll(results.folders.map { it.path })
                            }
                        }) {
                            Text(if (selectedPaths.size == results.folders.size) "None" else "All")
                        }
                    }
                }
            )
        },
        bottomBar = {
            val results = state as? EmptyFolderState.Results
            if (results != null && selectedPaths.isNotEmpty()) {
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
                            "${selectedPaths.size} folder${if (selectedPaths.size != 1) "s" else ""} selected",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = {
                                val toDelete = results.folders.filter { it.path in selectedPaths }
                                viewModel.deleteFolders(toDelete)
                                selectedPaths.clear()
                            },
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
                is EmptyFolderState.Idle -> EmptyFolderIdleView(onSweep = { viewModel.startScan() })
                is EmptyFolderState.Scanning -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(24.dp))
                        Text("Sweeping storage...", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Scanning for empty directory trees",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is EmptyFolderState.Results -> EmptyFolderResultsView(
                    folders = s.folders,
                    selectedPaths = selectedPaths,
                    onToggle = { path ->
                        if (path in selectedPaths) selectedPaths.remove(path) else selectedPaths.add(path)
                    },
                    onRescan = { viewModel.startScan() }
                )
                is EmptyFolderState.Error -> Column(
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
private fun EmptyFolderIdleView(onSweep: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.FolderDelete,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text("Deep Sweep", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Scan for empty folders left behind after cleaning.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onSweep, shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Start Sweep")
        }
    }
}

@Composable
private fun EmptyFolderResultsView(
    folders: List<EmptyFolder>,
    selectedPaths: Set<String>,
    onToggle: (String) -> Unit,
    onRescan: () -> Unit
) {
    if (folders.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text("No empty folders found!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onRescan) { Text("Scan again") }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${folders.size} empty folder${if (folders.size != 1) "s" else ""} found",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onRescan) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Rescan")
                    }
                }
            }
        }
        itemsIndexed(folders) { _, folder ->
            EmptyFolderItem(
                folder = folder,
                isSelected = folder.path in selectedPaths,
                onToggle = { onToggle(folder.path) }
            )
        }
    }
}

@Composable
private fun EmptyFolderItem(folder: EmptyFolder, isSelected: Boolean, onToggle: () -> Unit) {
    Card(
        onClick = onToggle,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
            Icon(
                Icons.Default.FolderOff,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    folder.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    folder.path,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
