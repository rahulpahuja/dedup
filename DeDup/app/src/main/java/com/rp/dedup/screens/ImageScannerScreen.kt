package com.rp.dedup.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.format.Formatter.formatFileSize
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.core.content.ContextCompat
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.canopas.lib.showcase.IntroShowcase
import com.canopas.lib.showcase.component.ShowcaseStyle
import com.rp.dedup.LocalDrawerState
import com.rp.dedup.R
import com.rp.dedup.ScannerViewModelFactory
import com.rp.dedup.core.ScannerContent
import com.rp.dedup.core.viewmodels.ScannerViewModel
import com.rp.dedup.ui.theme.DeDupTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.rp.dedup.core.ui.DeDupTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageScannerScreen(navController: NavHostController) {
    val drawerState = LocalDrawerState.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val viewModel: ScannerViewModel = viewModel(factory = ScannerViewModelFactory(context))

    val settingsViewModel: com.rp.dedup.core.viewmodels.SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = com.rp.dedup.core.viewmodels.SettingsViewModel.Factory(
            com.rp.dedup.core.caching.DataStoreManager(context.applicationContext)
        )
    )

    val duplicateGroups by viewModel.duplicateGroups.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val isStale by viewModel.isStale.collectAsState()
    val cacheLoaded by viewModel.cacheLoaded.collectAsState()
    val analyticsManager = remember { com.rp.dedup.core.analytics.AnalyticsManager(context) }

    LaunchedEffect(Unit) {
        analyticsManager.logScreenView("ImageScanner")
    }

    val selectedForDeletion = remember { mutableStateListOf<Uri>() }
    var showAutoClearWarning by remember { mutableStateOf(false) }
    var pendingDeleteUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    // True if we have anything to show — either from cache or a completed scan.
    val hasResults = duplicateGroups.isNotEmpty()

    // Auto-scan: wait for cache load to finish, then only scan if nothing was cached.
    LaunchedEffect(Unit) {
        val autoScan = settingsViewModel.autoScanOnStartup.first()
        if (!autoScan) return@LaunchedEffect
        viewModel.cacheLoaded.first { it }
        if (duplicateGroups.isEmpty()) viewModel.startScanning()
    }

    LaunchedEffect(isScanning) {
        if (isScanning) selectedForDeletion.clear()
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

    val tutorialShown by settingsViewModel.dataStoreManager.readData(
        com.rp.dedup.core.caching.DataStoreManager.LONG_PRESS_TUTORIAL_SHOWN,
        false
    ).collectAsState(initial = true)

    LaunchedEffect(tutorialShown, hasResults, isScanning) {
        if (!tutorialShown && hasResults && !isScanning) {
            analyticsManager.logTutorialInteraction("LONG_PRESS_PREVIEW", "VIEWED")
        }
    }

    var showBubblePermRationale by remember { mutableStateOf(false) }
    val bubblePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) com.rp.dedup.core.bubble.BubbleLauncher.launch(context)
        else showBubblePermRationale = true
    }
    val onBubbleClick: () -> Unit = {
        val hasPermission = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasPermission) com.rp.dedup.core.bubble.BubbleLauncher.launch(context)
        else bubblePermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    val tutorialStyle = ShowcaseStyle.Default.copy(
        backgroundColor = Color.Black,
        backgroundAlpha = 0.92f,
        targetCircleColor = MaterialTheme.colorScheme.primary
    )

    IntroShowcase(
        showIntroShowCase = !tutorialShown && hasResults && !isScanning,
        dismissOnClickOutside = true,
        onShowCaseCompleted = {
            analyticsManager.logTutorialInteraction("LONG_PRESS_PREVIEW", "COMPLETED")
            scope.launch {
                settingsViewModel.dataStoreManager.writeData(
                    com.rp.dedup.core.caching.DataStoreManager.LONG_PRESS_TUTORIAL_SHOWN,
                    true
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                DeDupTopBar(
                    title = stringResource(R.string.app_name),
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
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
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                ScannerHeader(
                    isScanning = isScanning,
                    hasResults = hasResults,
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
                    onDeleteSelected = { triggerOSDeletionPrompt(selectedForDeletion.toList()) },
                    onBubbleClick = onBubbleClick
                )

                if (isScanning) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                if (isStale && !isScanning && hasResults) {
                    StaleBanner(onRescan = { viewModel.startScanning() })
                }

                Box(modifier = Modifier.weight(1f)) {
                    ScannerContent(
                        duplicateGroups = duplicateGroups,
                        selectedUris = selectedForDeletion,
                        isScanning = isScanning,
                        hasScannedAtLeastOnce = cacheLoaded || hasResults,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        onImageSelected = { uri, isSelected ->
                            if (isSelected) selectedForDeletion.add(uri)
                            else selectedForDeletion.remove(uri)
                        },
                        onDeleteImage = { triggerOSDeletionPrompt(listOf(it)) }
                    )

                    if (!isScanning && hasResults) {
                        // Invisible target for the tutorial overlaying the first image result area
                        Box(
                            modifier = Modifier
                                .size(150.dp)
                                .padding(16.dp)
                                .introShowCaseTarget(
                                    index = 0,
                                    style = tutorialStyle,
                                    content = {
                                        TutorialTooltip(
                                            title = stringResource(R.string.tut_preview_title),
                                            body = stringResource(R.string.tut_preview_body)
                                        )
                                    }
                                )
                        )
                    }
                }
            }
        }

        if (showBubblePermRationale) {
            AlertDialog(
                onDismissRequest = { showBubblePermRationale = false },
                title = { Text("Notifications Required") },
                text = { Text("Allow notifications to show the floating scanner bubble. Enable them in Settings.") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        showBubblePermRationale = false
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.fromParts("package", context.packageName, null)
                            }
                        )
                    }) { Text("Open Settings") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showBubblePermRationale = false }) {
                        Text("Not now")
                    }
                }
            )
        }

        if (showAutoClearWarning) {
            AlertDialog(
                onDismissRequest = { showAutoClearWarning = false },
                title = { Text(stringResource(R.string.auto_clear_confirm_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.auto_clear_confirm_msg,
                            formatFileSize(context, autoClearSavingsBytes)
                        )
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showAutoClearWarning = false
                            analyticsManager.logAutoClearInitiated("IMAGE", autoClearSavingsBytes)
                            triggerOSDeletionPrompt(viewModel.getAutoClearUris().map { Uri.parse(it) })
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text(stringResource(R.string.auto_clear_confirm_btn)) }
                },
                dismissButton = {
                    TextButton(onClick = { showAutoClearWarning = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
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
private fun StaleBanner(onRescan: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Results may be outdated — new photos detected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRescan, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(
                    "Re-scan",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
private fun ScannerHeader(
    isScanning: Boolean,
    hasResults: Boolean,
    groupCount: Int,
    autoClearSavings: Long,
    selectedCount: Int,
    manualSavings: Long,
    context: android.content.Context,
    onScanClick: () -> Unit,
    onAutoClear: () -> Unit,
    onDeleteSelected: () -> Unit,
    onBubbleClick: () -> Unit = {},
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
                        isScanning -> stringResource(R.string.scanning_gallery)
                        hasResults && groupCount > 0 ->
                            stringResource(R.string.duplicate_groups_found, groupCount)

                        hasResults -> stringResource(R.string.gallery_clean)
                        else -> stringResource(R.string.scanner_title)
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
            
            // App Bubble Action (Android 17)
            if (android.os.Build.VERSION.SDK_INT >= 37 && isScanning) {
                IconButton(
                    onClick = onBubbleClick,
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                ) {
                    Icon(
                        Icons.Default.OpenInFull,
                        contentDescription = "Bubble Scan",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
            }

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
                Text(if (isScanning) stringResource(R.string.stop_btn) else stringResource(R.string.scan_btn))
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
                        stringResource(R.string.auto_clear_btn, formatFileSize(context, autoClearSavings)),
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
                            stringResource(R.string.delete_selected_btn, selectedCount, formatFileSize(context, manualSavings)),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }

    HorizontalDivider()
}


@Preview
@Composable
private fun ScannerScreenPreview() {
    DeDupTheme() {
        ImageScannerScreen(navController = NavHostController(LocalContext.current))
    }
}
