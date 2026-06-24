package com.rp.dedup.feature.voicestorage.presentation

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.speech.SpeechRecognizer
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rp.dedup.feature.voicestorage.data.model.MediaType
import com.rp.dedup.feature.voicestorage.data.model.StorageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

// ─── Root screen ─────────────────────────────────────────────────────────────

@Composable
fun DeDupChatScreen(onNavigateUp: () -> Unit = {}) {
    val context      = LocalContext.current
    val viewModel: ChatViewModel = viewModel(factory = ChatViewModel.Factory(context))
    val chatState    by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var inputText    by remember { mutableStateOf("") }

    LaunchedEffect(chatState.partialTranscript) {
        if (chatState.partialTranscript.isNotEmpty()) inputText = chatState.partialTranscript
    }
    LaunchedEffect(chatState.messages.size) {
        if (!chatState.isListening && chatState.partialTranscript.isEmpty() && !chatState.isStreaming) inputText = ""
    }
    LaunchedEffect(chatState.micError) {
        chatState.micError?.let { snackbarHost.showSnackbar(it); viewModel.clearMicError() }
    }

    val micPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startListening()
    }
    val onMicClick: () -> Unit = {
        when {
            chatState.isListening -> viewModel.stopListening()
            !SpeechRecognizer.isRecognitionAvailable(context) -> {}
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> viewModel.startListening()
            else -> micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val onSend: () -> Unit = { viewModel.send(inputText); inputText = "" }

    // Intercept system back when the full-screen preview is open
    BackHandler(enabled = chatState.isPreviewExpanded) { viewModel.collapsePreview() }

    // MediaStore delete launcher
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onDeletionResult(result.resultCode == Activity.RESULT_OK)
    }
    if (chatState.showDeleteConfirmation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // Snapshot the selection at the moment confirmation was requested so that any
        // in-flight selection changes don't restart this effect mid-way.
        val snapshotUris   = chatState.selectedUris
        val snapshotItems  = chatState.previewItems
        LaunchedEffect(snapshotUris) {
            // createDeleteRequest rejects URIs where media_type = 0 (documents from
            // MediaStore.Files). Split: media items via system dialog, docs deleted directly.
            val selectedItems = snapshotItems.filter { it.uri in snapshotUris }
            val mediaUris = selectedItems.filter { it.mediaType != MediaType.DOCUMENT }.map { it.uri }
            val docUris   = selectedItems.filter { it.mediaType == MediaType.DOCUMENT }.map { it.uri }

            // Direct deletion may throw SecurityException on API 30+ for files not owned
            // by this app, so wrap each call in runCatching.
            if (mediaUris.isNotEmpty()) {
                if (docUris.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        docUris.forEach { runCatching { context.contentResolver.delete(it, null, null) } }
                    }
                }
                val pending = MediaStore.createDeleteRequest(context.contentResolver, mediaUris)
                deleteLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
            } else {
                withContext(Dispatchers.IO) {
                    docUris.forEach { runCatching { context.contentResolver.delete(it, null, null) } }
                }
                viewModel.onDeletionResult(true)
            }
            viewModel.dismissDeletion()
        }
    }
    // Legacy delete dialog (API < 30)
    if (chatState.showDeleteConfirmation && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeletion() },
            title            = { Text("Delete ${chatState.selectedUris.size} file${if (chatState.selectedUris.size != 1) "s" else ""}?") },
            text             = { Text("This will permanently remove the selected files.") },
            confirmButton    = {
                Button(
                    onClick = {
                        chatState.selectedUris.forEach { context.contentResolver.delete(it, null, null) }
                        viewModel.onDeletionResult(true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton    = { OutlinedButton(onClick = { viewModel.dismissDeletion() }) { Text("Cancel") } },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost        = { SnackbarHost(snackbarHost) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
                .background(MaterialTheme.colorScheme.background)
        ) {
            DeDupAnimatedBackground()

            Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                ChatHeader(
                    onNavigateUp       = onNavigateUp,
                    onClear            = { viewModel.clearHistory() },
                    isTtsEnabled       = chatState.isTtsEnabled,
                    isTtsSpeaking      = chatState.isTtsSpeaking,
                    onToggleTts        = { viewModel.toggleTts() },
                    isVibrationEnabled = chatState.isVibrationEnabled,
                    onToggleVibration  = { viewModel.toggleVibration() },
                )
                OnDeviceBadge()
                ChatMessageList(
                    messages      = chatState.messages,
                    streamingText = chatState.streamingText,
                    isStreaming   = chatState.isStreaming,
                    modifier      = Modifier.weight(1f),
                )
                // Inline preview strip — appears when storage query returns results
                AnimatedVisibility(
                    visible = chatState.previewItems.isNotEmpty(),
                    enter   = fadeIn() + slideInVertically { it / 2 },
                    exit    = fadeOut() + slideOutVertically { it / 2 },
                ) {
                    StoragePreviewRow(
                        items    = chatState.previewItems,
                        onExpand = { viewModel.expandPreview() },
                    )
                }
                AnimatedVisibility(
                    visible = chatState.suggestions.isNotEmpty() && !chatState.isStreaming,
                    enter   = fadeIn(),
                    exit    = fadeOut(),
                ) {
                    SuggestionChips(
                        suggestions = chatState.suggestions,
                        onSelect    = { viewModel.send(it); inputText = "" },
                    )
                }
                ChatInputBar(
                    text         = inputText,
                    onTextChange = { inputText = it },
                    isListening  = chatState.isListening,
                    isStreaming  = chatState.isStreaming,
                    onMicClick   = onMicClick,
                    onSend       = onSend,
                )
            }

            // Full-screen preview overlay — slides up over everything
            AnimatedVisibility(
                visible  = chatState.isPreviewExpanded,
                enter    = slideInVertically { it },
                exit     = slideOutVertically { it },
                modifier = Modifier.fillMaxSize(),
            ) {
                FullScreenPreview(
                    items          = chatState.previewItems,
                    selectedUris   = chatState.selectedUris,
                    onToggleSelect = { viewModel.toggleSelectItem(it) },
                    onRequestDelete = { viewModel.requestDeletion() },
                    onClose        = { viewModel.collapsePreview() },
                )
            }
        }
    }
}

// ─── Header ──────────────────────────────────────────────────────────────────

@Composable
private fun ChatHeader(
    onNavigateUp: () -> Unit,
    onClear: () -> Unit,
    isTtsEnabled: Boolean,
    isTtsSpeaking: Boolean,
    onToggleTts: () -> Unit,
    isVibrationEnabled: Boolean,
    onToggleVibration: () -> Unit,
) {
    val speakerPulse = rememberInfiniteTransition(label = "speaker")
    val speakerScale by speakerPulse.animateFloat(
        1f, 1.25f, infiniteRepeatable(tween(600), RepeatMode.Reverse), "ss"
    )
    Row(
        modifier          = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onNavigateUp) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onBackground)
        }
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                "DeDup",
                color      = MaterialTheme.colorScheme.onBackground,
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(10.dp))
                Text(
                    "Talk to My Storage",
                    color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                )
            }
        }

        @Suppress("DEPRECATION")
        val volIcon = if (isTtsEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff
        IconButton(onClick = onToggleTts, modifier = Modifier.scale(if (isTtsSpeaking) speakerScale else 1f)) {
            Icon(volIcon, if (isTtsEnabled) "Disable TTS" else "Enable TTS",
                tint = if (isTtsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f))
        }
        IconButton(onClick = onToggleVibration) {
            Icon(
                if (isVibrationEnabled) Icons.Default.Vibration else Icons.Default.PhoneDisabled,
                if (isVibrationEnabled) "Disable vibration" else "Enable vibration",
                tint = if (isVibrationEnabled) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = onClear) {
            Icon(Icons.Default.DeleteSweep, "Clear history", tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f))
        }
    }
}

// ─── On-device badge ─────────────────────────────────────────────────────────

@Composable
private fun OnDeviceBadge() {
    Row(
        modifier              = Modifier.padding(start = 16.dp, bottom = 8.dp).clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Default.Shield, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(11.dp))
        Text("On-Device AI · Your data never leaves this phone", fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Medium)
    }
}

// ─── Message list ─────────────────────────────────────────────────────────────

@Composable
private fun ChatMessageList(
    messages: List<ChatMessage>,
    streamingText: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val itemCount = messages.size + if (isStreaming) 1 else 0
    LaunchedEffect(itemCount, streamingText.length) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }
    LazyColumn(
        state               = listState,
        modifier            = modifier.padding(horizontal = 12.dp),
        contentPadding      = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(messages, key = { it.id }) { msg ->
            if (msg.isUser) UserBubble(msg.text) else BotBubble(msg.text)
        }
        if (isStreaming) item(key = "streaming") { StreamingBubble(streamingText) }
    }
}

@Composable
private fun BotAvatar() {
    Box(Modifier.size(28.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center) {
        Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                .background(MaterialTheme.colorScheme.primary).padding(horizontal = 14.dp, vertical = 10.dp)
        ) { Text(text, color = MaterialTheme.colorScheme.onPrimary, fontSize = 15.sp, lineHeight = 22.sp) }
    }
}

@Composable
private fun BotBubble(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.Bottom) {
        BotAvatar()
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) { Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, lineHeight = 22.sp) }
    }
}

@Composable
private fun StreamingBubble(text: String) {
    val cursorAlpha by rememberInfiniteTransition(label = "cursor")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(530), RepeatMode.Reverse), "blink")
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.Bottom) {
        BotAvatar()
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (text.isEmpty()) ThinkingDots()
            else Text(
                buildAnnotatedString {
                    append(text)
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary.copy(alpha = cursorAlpha))) { append("▎") }
                },
                color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, lineHeight = 22.sp,
            )
        }
    }
}

@Composable
private fun ThinkingDots() {
    val t = rememberInfiniteTransition(label = "thinking")
    val d1 by t.animateFloat(0.2f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse, StartOffset(0)), "d1")
    val d2 by t.animateFloat(0.2f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse, StartOffset(160)), "d2")
    val d3 by t.animateFloat(0.2f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse, StartOffset(320)), "d3")
    val c  = MaterialTheme.colorScheme.onSurface
    Row(Modifier.height(20.dp), horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically) {
        listOf(d1, d2, d3).forEach { a -> Box(Modifier.size(7.dp).background(c.copy(alpha = a), CircleShape)) }
    }
}

// ─── Inline storage preview strip ────────────────────────────────────────────

@Composable
private fun StoragePreviewRow(items: List<StorageItem>, onExpand: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                "${items.size} file${if (items.size != 1) "s" else ""}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onExpand, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.OpenInFull,
                    contentDescription = "Expand to full screen",
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(16.dp),
                )
            }
        }
        LazyRow(
            contentPadding        = PaddingValues(start = 8.dp, end = 8.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(items, key = { it.uri }) { item ->
                PreviewThumbCard(item)
            }
        }
    }
}

@Composable
private fun PreviewThumbCard(item: StorageItem) {
    Column(
        modifier            = Modifier.width(76.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(76.dp).clip(RoundedCornerShape(10.dp))
        ) {
            FileThumbnail(item = item, modifier = Modifier.fillMaxSize())
            // Size badge
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.52f)),
            ) {
                Text(
                    item.sizeInBytes.fmtBytes(),
                    color    = Color.White,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                )
            }
        }
        Text(
            item.displayName,
            fontSize  = 9.sp,
            maxLines  = 1,
            overflow  = TextOverflow.Ellipsis,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier  = Modifier.padding(top = 2.dp),
        )
    }
}

// ─── Full-screen preview overlay ─────────────────────────────────────────────

@Composable
private fun PreviewDragHandle(onDismiss: () -> Unit) {
    var dragOffset by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd    = { if (dragOffset > 80.dp.toPx()) onDismiss(); dragOffset = 0f },
                    onDragCancel = { dragOffset = 0f },
                    onVerticalDrag = { _, delta -> if (delta > 0) dragOffset += delta },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
        )
    }
}

@Composable
private fun FullScreenPreview(
    items: List<StorageItem>,
    selectedUris: Set<Uri>,
    onToggleSelect: (Uri) -> Unit,
    onRequestDelete: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Drag handle — owns its own gesture area so the grid below can't steal touch events
            PreviewDragHandle(onDismiss = onClose)
            // Top bar
            Row(
                modifier          = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                        tint = MaterialTheme.colorScheme.onBackground)
                }
                Text(
                    if (selectedUris.isEmpty()) "${items.size} files"
                    else "${selectedUris.size} selected",
                    style    = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    color    = MaterialTheme.colorScheme.onBackground,
                )
                AnimatedVisibility(visible = selectedUris.isNotEmpty()) {
                    IconButton(onClick = onRequestDelete) {
                        Icon(Icons.Default.Delete, "Delete selected",
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            // Grid
            LazyVerticalGrid(
                columns               = GridCells.Adaptive(110.dp),
                modifier              = Modifier.weight(1f),
                contentPadding        = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement   = Arrangement.spacedBy(4.dp),
            ) {
                items(items, key = { it.uri }) { item ->
                    FullScreenCell(
                        item       = item,
                        isSelected = item.uri in selectedUris,
                        onTap      = { onToggleSelect(item.uri) },
                    )
                }
            }
        }

        // Bottom delete bar
        AnimatedVisibility(
            visible  = selectedUris.isNotEmpty(),
            enter    = slideInVertically { it },
            exit     = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text("${selectedUris.size} selected", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface)
                Button(
                    onClick = onRequestDelete,
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp).padding(end = 4.dp))
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun FullScreenCell(item: StorageItem, isSelected: Boolean, onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .border(
                if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
                else BorderStroke(0.dp, Color.Transparent),
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onTap)
    ) {
        FileThumbnail(item = item, modifier = Modifier.fillMaxSize())
        // Size chip
        Surface(
            color    = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
            shape    = RoundedCornerShape(topEnd = 8.dp),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            Text(item.sizeInBytes.fmtBytes(), color = Color.White, fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
        }
        // Selection check
        if (isSelected) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(5.dp).size(22.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ─── Thumbnail loader ─────────────────────────────────────────────────────────

@Composable
private fun FileThumbnail(item: StorageItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(item.uri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(item.uri) {
        bitmap = withContext(Dispatchers.IO) {
            if (item.mediaType == MediaType.AUDIO || item.mediaType == MediaType.DOCUMENT) return@withContext null
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(item.uri, Size(256, 256), null)
                } else {
                    val id = android.content.ContentUris.parseId(item.uri)
                    @Suppress("DEPRECATION")
                    when (item.mediaType) {
                        MediaType.IMAGE -> MediaStore.Images.Thumbnails.getThumbnail(
                            context.contentResolver, id, MediaStore.Images.Thumbnails.MINI_KIND, null)
                        MediaType.VIDEO -> MediaStore.Video.Thumbnails.getThumbnail(
                            context.contentResolver, id, MediaStore.Video.Thumbnails.MINI_KIND, null)
                        MediaType.AUDIO, MediaType.DOCUMENT -> null
                    }
                }
            }.getOrNull()
        }
    }

    if (bitmap != null) {
        Image(bitmap!!.asImageBitmap(), contentDescription = null,
            contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = when (item.mediaType) {
                    MediaType.VIDEO    -> Icons.Default.VideoFile
                    MediaType.AUDIO    -> Icons.Default.AudioFile
                    MediaType.DOCUMENT -> Icons.Default.Description
                    MediaType.IMAGE    -> Icons.Default.Image
                },
                contentDescription = null,
                tint     = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

// ─── Suggestion chips ────────────────────────────────────────────────────────

@Composable
private fun SuggestionChips(suggestions: List<String>, onSelect: (String) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(suggestions) { chip ->
            SuggestionChip(
                onClick = { onSelect(chip) },
                label   = { Text(chip, fontSize = 13.sp) },
                shape   = RoundedCornerShape(20.dp),
                colors  = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    labelColor     = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                border  = SuggestionChipDefaults.suggestionChipBorder(
                    enabled     = true,
                    borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    borderWidth = 1.dp,
                ),
            )
        }
    }
}

// ─── Animated background ─────────────────────────────────────────────────────

@Composable
fun DeDupAnimatedBackground() {
    val t  = rememberInfiniteTransition(label = "bg")
    val cs = MaterialTheme.colorScheme
    val p  by t.animateFloat(0f, (2 * Math.PI).toFloat(),
        infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Restart), "orb")

    Canvas(Modifier.fillMaxSize().blur(120.dp)) {
        val w = size.width; val h = size.height
        drawCircle(cs.primary.copy(alpha = 0.35f),          w * 0.6f,  Offset(w * (0.5f + 0.3f  * cos(p)),        h * (0.3f + 0.2f  * sin(p))))
        drawCircle(cs.secondary.copy(alpha = 0.30f),        w * 0.5f,  Offset(w * (0.5f + 0.4f  * sin(p * 2)),    h * (0.6f + 0.3f  * cos(p))))
        drawCircle(cs.tertiary.copy(alpha = 0.30f),         w * 0.4f,  Offset(w * (0.8f + 0.1f  * cos(p)),        h * (0.8f + 0.15f * sin(p * 3))))
        drawCircle(cs.primaryContainer.copy(alpha = 0.25f), w * 0.45f, Offset(w * (0.8f + 0.1f  * sin(p)),        h * (0.2f + 0.1f  * cos(p))))
        drawCircle(cs.secondary.copy(alpha = 0.20f),        w * 0.55f, Offset(w * (0.5f + 0.15f * cos(p * 1.5f)), h * (0.5f + 0.1f  * sin(p * 1.2f))))
    }
}

// ─── Input bar ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    isListening: Boolean,
    isStreaming: Boolean,
    onMicClick: () -> Unit,
    onSend: () -> Unit,
) {
    val pulse = rememberInfiniteTransition(label = "mic")
    val pulseScale by pulse.animateFloat(1f, 1.2f, infiniteRepeatable(tween(500), RepeatMode.Reverse), "p")
    val onSurface  = MaterialTheme.colorScheme.onSurface

    Row(
        modifier          = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
            .padding(horizontal = 4.dp, vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMicClick, enabled = !isStreaming,
            modifier = Modifier.scale(if (isListening) pulseScale else 1f)) {
            Icon(
                if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                if (isListening) "Stop" else "Voice input",
                tint = when { isListening -> MaterialTheme.colorScheme.error
                              isStreaming  -> onSurface.copy(alpha = 0.3f)
                              else         -> onSurface },
            )
        }
        BasicTextField(
            value         = text,
            onValueChange = onTextChange,
            enabled       = !isStreaming,
            modifier      = Modifier.weight(1f).padding(vertical = 12.dp),
            textStyle     = MaterialTheme.typography.bodyLarge.copy(color = onSurface),
            maxLines      = 4,
            decorationBox = { innerTextField ->
                Box {
                    if (text.isEmpty()) {
                        Text(
                            when { isListening -> "Listening…"; isStreaming -> "Responding…"; else -> "Ask me anything…" },
                            color = onSurface.copy(alpha = 0.45f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (text.isNotBlank() && !isStreaming) {
            IconButton(onClick = onSend,
                modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary).size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send",
                    tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ─── Shared util ─────────────────────────────────────────────────────────────

private fun Long.fmtBytes(): String = when {
    this >= 1_073_741_824L -> "%.1f GB".format(this / 1_073_741_824.0)
    this >= 1_048_576L     -> "%.1f MB".format(this / 1_048_576.0)
    this >= 1_024L         -> "%.0f KB".format(this / 1_024.0)
    else                   -> "$this B"
}

// ─── Preview ─────────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true, device = "spec:width=411dp,height=891dp,dpi=420")
@Composable
fun DeDupChatScreenPreview() {
    MaterialTheme { DeDupChatScreen() }
}
