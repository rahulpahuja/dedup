package com.rp.dedup.feature.voicestorage.presentation

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import android.provider.MediaStore
import android.speech.SpeechRecognizer
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rp.dedup.feature.voicestorage.data.model.MediaType
import com.rp.dedup.feature.voicestorage.data.model.StorageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceStorageScreen(
    onNavigateUp: () -> Unit = {},
) {
    val context = LocalContext.current
    val fa      = remember { FirebaseAnalytics.getInstance(context) }
    val viewModel: VoiceStorageViewModel = viewModel(factory = VoiceStorageViewModel.Factory(context))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Show ViewModel-surfaced mic errors (e.g. ERROR_INSUFFICIENT_PERMISSIONS, network errors)
    LaunchedEffect(state.micError) {
        state.micError?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.onIntent(VoiceStorageIntent.ClearMicError)
        }
    }

    // Request RECORD_AUDIO at the moment the user taps the mic, not upfront
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.onIntent(VoiceStorageIntent.StartListening)
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Microphone permission is required for voice search")
            }
        }
    }

    val onMicClick: () -> Unit = {
        when {
            state.isListening -> viewModel.onIntent(VoiceStorageIntent.StopListening)
            !SpeechRecognizer.isRecognitionAvailable(context) -> {
                scope.launch { snackbarHostState.showSnackbar("Voice recognition is not available on this device") }
            }
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED -> {
                viewModel.onIntent(VoiceStorageIntent.StartListening)
            }
            else -> micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val freedBytes = state.filteredFiles
                .filter { it.uri in state.selectedFileUris }
                .sumOf { it.sizeInBytes }
            fa.logEvent("files_deleted", Bundle().apply {
                putString("scan_type", "VOICE_STORAGE")
                putInt("deleted_count", state.selectedFileUris.size)
                putLong("freed_bytes", freedBytes)
            })
        }
        viewModel.onIntent(VoiceStorageIntent.OnDeletionResult(result.resultCode == Activity.RESULT_OK))
    }

    if (state.showDeleteConfirmation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        LaunchedEffect(state.selectedFileUris) {
            val pending = MediaStore.createDeleteRequest(
                context.contentResolver,
                state.selectedFileUris.toList()
            )
            deleteLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
            viewModel.onIntent(VoiceStorageIntent.DismissDeletion)
        }
    }

    // Fallback dialog for API < 30 (no system sheet; inform the user)
    if (state.showDeleteConfirmation && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        LegacyDeleteDialog(
            count = state.selectedFileUris.size,
            onConfirm = {
                val freedBytes = state.filteredFiles
                    .filter { it.uri in state.selectedFileUris }
                    .sumOf { it.sizeInBytes }
                fa.logEvent("files_deleted", Bundle().apply {
                    putString("scan_type", "VOICE_STORAGE")
                    putInt("deleted_count", state.selectedFileUris.size)
                    putLong("freed_bytes", freedBytes)
                })
                viewModel.onIntent(VoiceStorageIntent.DismissDeletion)
                state.selectedFileUris.forEach { context.contentResolver.delete(it, null, null) }
                viewModel.onIntent(VoiceStorageIntent.OnDeletionResult(true))
            },
            onDismiss = { viewModel.onIntent(VoiceStorageIntent.DismissDeletion) }
        )
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(snackbarData = data, containerColor = MaterialTheme.colorScheme.inverseSurface)
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Voice Storage",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = state.selectedFileUris.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit  = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            ) {
                SelectionBottomBar(
                    selectionCount = state.selectedFileUris.size,
                    onDelete  = { viewModel.onIntent(VoiceStorageIntent.RequestDeletion) },
                    onClear   = { viewModel.onIntent(VoiceStorageIntent.ClearSelection) },
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            VoiceSearchBar(
                query         = state.currentQueryText,
                isListening   = state.isListening,
                onQueryChange = { viewModel.onIntent(VoiceStorageIntent.UpdateQuery(it)) },
                onMicClick    = onMicClick,
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            AnimatedVisibility(visible = state.isListening) {
                Text(
                    text  = "Listening… tap mic to stop",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 20.dp, bottom = 4.dp),
                )
            }

            AnimatedVisibility(visible = state.voiceCommandSummary.isNotBlank()) {
                VoiceCommandSummaryChip(
                    summary  = state.voiceCommandSummary,
                    onDismiss = { viewModel.onIntent(VoiceStorageIntent.UpdateFilters(com.rp.dedup.feature.voicestorage.domain.FilterConfig())) },
                    modifier  = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                )
            }

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.filteredFiles.isEmpty() -> EmptyState(
                    query = state.currentQueryText,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> StorageGrid(
                    items = state.filteredFiles,
                    selectedUris = state.selectedFileUris,
                    onToggleSelect = { viewModel.onIntent(VoiceStorageIntent.ToggleSelectFile(it)) },
                )
            }
        }
    }
}

// ─── Search bar ──────────────────────────────────────────────────────────────

@Composable
private fun VoiceSearchBar(
    query: String,
    isListening: Boolean,
    onQueryChange: (String) -> Unit,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier,
) {

    val keyboard = LocalSoftwareKeyboardController.current
    val pulseTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 1.25f,
        animationSpec = infiniteRepeatable(animation = tween(500), repeatMode = RepeatMode.Reverse),
        label = "mic_scale",
    )
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search files by name…") },
            leadingIcon  = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor   = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f),
        )

        FilledIconButton(
            onClick = onMicClick,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (isListening) MaterialTheme.colorScheme.error
                                 else MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .padding(start = 8.dp)
                .scale(if (isListening) pulseScale else 1f),
        ) {
            Icon(
                imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = if (isListening) "Stop listening" else "Start voice search",
            )
        }
    }
}

// ─── Voice command summary chip ──────────────────────────────────────────────

@Composable
private fun VoiceCommandSummaryChip(
    summary: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = null,
                tint     = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text     = summary,
                style    = MaterialTheme.typography.labelMedium,
                color    = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Clear voice filter",
                    tint     = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

// ─── Adaptive grid ───────────────────────────────────────────────────────────

@Composable
private fun StorageGrid(
    items: List<StorageItem>,
    selectedUris: Set<Uri>,
    onToggleSelect: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement   = Arrangement.spacedBy(4.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(items = items, key = { it.uri }) { item ->
            StorageItemCell(
                item       = item,
                isSelected = item.uri in selectedUris,
                onClick    = { onToggleSelect(item.uri) },
            )
        }
    }
}

@Composable
private fun StorageItemCell(
    item: StorageItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(BorderStroke(3.dp, borderColor), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        MediaThumbnail(
            uri       = item.uri,
            mediaType = item.mediaType,
            modifier  = Modifier.fillMaxSize(),
        )

        // File-size chip — always visible so users know what they'd reclaim
        Surface(
            color  = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
            shape  = RoundedCornerShape(topEnd = 8.dp),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            Text(
                text  = item.sizeInBytes.formatBytes(),
                color = Color.White,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }

        // Selection check badge
        if (isSelected) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint     = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

// ─── Thumbnail loader (no Coil — pure ContentResolver) ───────────────────────

@Composable
private fun MediaThumbnail(
    uri: Uri,
    mediaType: MediaType,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            // Audio and document files have no visual thumbnail
            if (mediaType == MediaType.AUDIO || mediaType == MediaType.DOCUMENT) return@withContext null
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(uri, Size(256, 256), null)
                } else {
                    val id = android.content.ContentUris.parseId(uri)
                    @Suppress("DEPRECATION")
                    when (mediaType) {
                        MediaType.IMAGE -> MediaStore.Images.Thumbnails.getThumbnail(
                            context.contentResolver, id,
                            MediaStore.Images.Thumbnails.MINI_KIND, null
                        )
                        MediaType.VIDEO -> MediaStore.Video.Thumbnails.getThumbnail(
                            context.contentResolver, id,
                            MediaStore.Video.Thumbnails.MINI_KIND, null
                        )
                        MediaType.AUDIO, MediaType.DOCUMENT -> null
                    }
                }
            }.getOrNull()
        }
    }

    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = when (mediaType) {
                    MediaType.VIDEO    -> Icons.Default.VideoFile
                    MediaType.AUDIO    -> Icons.Default.AudioFile
                    MediaType.DOCUMENT -> Icons.Default.Description
                    MediaType.IMAGE    -> Icons.Default.Image
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

// ─── Selection bottom bar ────────────────────────────────────────────────────

@Composable
private fun SelectionBottomBar(
    selectionCount: Int,
    onDelete: () -> Unit,
    onClear: () -> Unit,
) {
    BottomAppBar(
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                text  = "$selectionCount selected",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onClear) { Text("Cancel") }
                Button(
                    onClick = onDelete,
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text("Delete")
                }
            }
        }
    }
}

// ─── Empty state ─────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(query: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint     = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text  = if (query.isNotBlank()) "No files match \"$query\"" else "No media found",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.outline,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
        }
    }
}

// ─── Legacy delete dialog (API < 30) ─────────────────────────────────────────

@Composable
private fun LegacyDeleteDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Delete $count file${if (count != 1) "s" else ""}?") },
        text    = { Text("This will permanently remove the selected files from your device.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("Delete") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ─── Utilities ───────────────────────────────────────────────────────────────

private fun Long.formatBytes(): String = when {
    this >= 1_073_741_824L -> "%.1f GB".format(this / 1_073_741_824.0)
    this >= 1_048_576L     -> "%.1f MB".format(this / 1_048_576.0)
    this >= 1_024L         -> "%.0f KB".format(this / 1_024.0)
    else                   -> "$this B"
}

@Preview(showBackground = true)
@Composable
fun VoiceStorageScreenPreview() {
    MaterialTheme {
        VoiceStorageScreen()
    }
}
