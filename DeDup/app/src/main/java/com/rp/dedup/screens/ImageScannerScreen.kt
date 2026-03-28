package com.rp.dedup.screens

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.format.Formatter.formatFileSize
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.rp.dedup.LocalDrawerState
import com.rp.dedup.ScannerViewModelFactory
import com.rp.dedup.core.ScannerContent
import com.rp.dedup.core.image.ScannerViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageScannerScreen(navController: NavHostController) {
    val drawerState = LocalDrawerState.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val viewModel: ScannerViewModel = viewModel(factory = ScannerViewModelFactory(context))

    val duplicateGroups by viewModel.duplicateGroups.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val selectedForDeletion = remember { mutableStateListOf<Uri>() }
    var showAutoClearWarning by remember { mutableStateOf(false) }
    var pendingDeleteUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var hasScannedAtLeastOnce by remember { mutableStateOf(false) }

    LaunchedEffect(isScanning) {
        if (isScanning) {
            hasScannedAtLeastOnce = true
            selectedForDeletion.clear()
        }
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.removeDeletedImagesFromUI(pendingDeleteUris.map { it.toString() })
            selectedForDeletion.removeAll(pendingDeleteUris)
            pendingDeleteUris = emptyList()
        }
    }

    fun triggerOSDeletionPrompt(uris: List<Uri>) {
        if (uris.isEmpty()) return
        pendingDeleteUris = uris
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pi = MediaStore.createDeleteRequest(context.contentResolver, uris)
            deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        } else {
            uris.forEach { context.contentResolver.delete(it, null, null) }
            viewModel.removeDeletedImagesFromUI(uris.map { it.toString() })
            selectedForDeletion.removeAll(uris)
        }
    }

    val autoClearSavingsBytes = remember(duplicateGroups) {
        duplicateGroups.sumOf { group ->
            if (group.size > 1) group.drop(1).sumOf { it.sizeInBytes } else 0L
        }
    }
    val manualSavingsBytes = remember(selectedForDeletion.size) {
        val selectedUriStrings = selectedForDeletion.map { it.toString() }.toHashSet()
        duplicateGroups.flatten()
            .filter { it.uri in selectedUriStrings }
            .sumOf { it.sizeInBytes }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "DeDuplicator",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(
                            Icons.Default.Menu, contentDescription = "Menu",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Surface(
                            shape = CircleShape,
                            modifier = Modifier.size(32.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        ) {
                            Icon(
                                Icons.Default.Person, contentDescription = "Profile",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = { BottomNavigationBar(navController) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            ScannerHeader(
                isScanning = isScanning,
                hasScannedAtLeastOnce = hasScannedAtLeastOnce,
                groupCount = duplicateGroups.size,
                autoClearSavings = autoClearSavingsBytes,
                selectedCount = selectedForDeletion.size,
                manualSavings = manualSavingsBytes,
                context = context,
                onScanClick = {
                    if (isScanning) viewModel.cancelScanning()
                    else viewModel.startScanning()
                },
                onAutoClear = { showAutoClearWarning = true },
                onDeleteSelected = { triggerOSDeletionPrompt(selectedForDeletion.toList()) }
            )

            if (isScanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            ScannerContent(
                duplicateGroups = duplicateGroups,
                selectedUris = selectedForDeletion,
                isScanning = isScanning,
                hasScannedAtLeastOnce = hasScannedAtLeastOnce,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                onImageSelected = { uri, isSelected ->
                    if (isSelected) selectedForDeletion.add(uri)
                    else selectedForDeletion.remove(uri)
                },
                onDeleteImage = { triggerOSDeletionPrompt(listOf(it)) }
            )
        }
    }

    if (showAutoClearWarning) {
        AlertDialog(
            onDismissRequest = { showAutoClearWarning = false },
            title = { Text("Auto Clear") },
            text = {
                Text(
                    "Auto clear keeps one copy from each group and deletes the rest. " +
                            "This will free up ${
                                formatFileSize(
                                    context,
                                    autoClearSavingsBytes
                                )
                            }.\n\n" +
                            "Visual similarity detection can occasionally make mistakes. Proceed?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAutoClearWarning = false
                        triggerOSDeletionPrompt(viewModel.getAutoClearUris().map { Uri.parse(it) })
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Yes, Auto Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showAutoClearWarning = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ScannerHeader(
    isScanning: Boolean,
    hasScannedAtLeastOnce: Boolean,
    groupCount: Int,
    autoClearSavings: Long,
    selectedCount: Int,
    manualSavings: Long,
    context: android.content.Context,
    onScanClick: () -> Unit,
    onAutoClear: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        isScanning -> "Scanning gallery…"
                        hasScannedAtLeastOnce && groupCount > 0 ->
                            "$groupCount duplicate group${if (groupCount != 1) "s" else ""} found"

                        hasScannedAtLeastOnce -> "Your gallery is clean"
                        else -> "Image Scanner"
                    },
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                if (!isScanning && groupCount > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Potential savings: ${formatFileSize(context, autoClearSavings)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onScanClick,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = if (isScanning) Icons.Default.Stop else Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (isScanning) "Stop" else "Scan")
            }
        }

        if (!isScanning && groupCount > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onAutoClear,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        Icons.Default.AutoAwesome, contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Auto Clear (${formatFileSize(context, autoClearSavings)})",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                if (selectedCount > 0) {
                    Button(
                        onClick = onDeleteSelected,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.Default.Delete, contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Delete $selectedCount (${formatFileSize(context, manualSavings)})",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }

    HorizontalDivider()
}
