package com.rp.dedup.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderDelete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.rp.dedup.R
import com.rp.dedup.core.model.EmptyFolder
import com.rp.dedup.core.model.EmptyFolderState
import com.rp.dedup.core.ui.DeDupTopBar
import com.rp.dedup.core.viewmodels.EmptyFolderViewModel
import com.rp.dedup.ui.theme.DeDupTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmptyFolderScreen(navController: NavHostController) {
    val context = LocalContext.current
    val viewModel: EmptyFolderViewModel = viewModel(
        factory = EmptyFolderViewModel.factory(context)
    )
    val state by viewModel.state.collectAsState()
    val analyticsManager = remember { com.rp.dedup.core.analytics.AnalyticsManager(context) }
    val selectedPaths = remember { mutableStateSetOf<String>() }

    LaunchedEffect(Unit) {
        analyticsManager.logScreenView("EmptyFolderRemover")
    }

    Scaffold(
        topBar = {
            DeDupTopBar(
                title = stringResource(R.string.screen_empty_folder_remover),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
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
                            Text(if (selectedPaths.size == results.folders.size) stringResource(R.string.none) else stringResource(R.string.all))
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
                            stringResource(R.string.folders_selected, selectedPaths.size),
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
                            Text(stringResource(R.string.delete_folders, selectedPaths.size))
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                is EmptyFolderState.Idle -> EmptyFolderIdleView(onSweep = viewModel::startScan)
                is EmptyFolderState.Scanning -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is EmptyFolderState.Results -> {
                    EmptyFolderResultsView(
                        folders = s.folders,
                        selectedPaths = selectedPaths,
                        onToggle = { path ->
                            if (selectedPaths.contains(path)) selectedPaths.remove(path)
                            else selectedPaths.add(path)
                        }
                    )
                }
                is EmptyFolderState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(s.message, color = MaterialTheme.colorScheme.error)
                    }
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
        Text(stringResource(R.string.deep_sweep), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.empty_folder_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onSweep, shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.start_sweep))
        }
    }
}

@Composable
private fun EmptyFolderResultsView(
    folders: List<EmptyFolder>,
    selectedPaths: Set<String>,
    onToggle: (String) -> Unit
) {
    if (folders.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No empty folders found.")
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            items(folders, key = { it.path }) { folder ->
                EmptyFolderItem(
                    folder = folder,
                    isSelected = selectedPaths.contains(folder.path),
                    onToggle = { onToggle(folder.path) }
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun EmptyFolderItem(
    folder: EmptyFolder,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(folder.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(folder.path, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyFolderScreenPreview() {
    DeDupTheme {
        EmptyFolderScreen(navController = NavHostController(LocalContext.current))
    }
}
