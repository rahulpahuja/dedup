package com.rp.dedup.screens

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.rp.dedup.R
import com.rp.dedup.core.analytics.AnalyticsManager
import com.rp.dedup.core.model.TrashItem
import com.rp.dedup.core.trash.TrashManager
import com.rp.dedup.core.viewmodels.TrashUiEvent
import com.rp.dedup.core.viewmodels.TrashViewModel
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(navController: NavHostController) {
    val context = LocalContext.current
    val viewModel: TrashViewModel = viewModel(factory = TrashViewModel.Factory(context))
    val trashManager = remember { TrashManager(context) }
    val analyticsManager = remember { AnalyticsManager.getInstance(context) }

    val items by viewModel.items.collectAsState()
    val isBusy by viewModel.isBusy.collectAsState()
    val event  by viewModel.event.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { analyticsManager.logScreenView("Trash") }

    LaunchedEffect(event) {
        when (val e = event) {
            is TrashUiEvent.RestoreSuccess ->
                snackbarHost.showSnackbar("\"${e.name}\" restored")
            is TrashUiEvent.RestoreFailed ->
                snackbarHost.showSnackbar("Could not restore \"${e.name}\"")
            TrashUiEvent.EmptiedTrash ->
                snackbarHost.showSnackbar("Trash emptied")
            null -> Unit
        }
        viewModel.clearEvent()
    }

    var showEmptyConfirm by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<TrashItem?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.trash_title), fontWeight = FontWeight.Bold)
                        if (items.isNotEmpty()) {
                            Text(
                                "${items.size} item${if (items.size == 1) "" else "s"} · auto-deleted after 30 days",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (items.isNotEmpty()) {
                        IconButton(onClick = { showEmptyConfirm = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.trash_empty))
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.trash_empty_state),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(padding)
            ) {
                items(items, key = { it.id }) { item ->
                    TrashItemCard(
                        item          = item,
                        trashManager  = trashManager,
                        onRestore     = { viewModel.restore(item) },
                        onDeleteForever = { pendingDelete = item }
                    )
                }
            }
        }
    }

    AnimatedVisibility(isBusy, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }

    if (showEmptyConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyConfirm = false },
            icon = { Icon(Icons.Default.DeleteSweep, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.trash_empty_confirm_title), fontWeight = FontWeight.Bold) },
            text  = { Text(stringResource(R.string.trash_empty_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = { showEmptyConfirm = false; viewModel.emptyTrash() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.trash_empty)) }
            },
            dismissButton = { TextButton(onClick = { showEmptyConfirm = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.trash_delete_forever_title), fontWeight = FontWeight.Bold) },
            text  = { Text(stringResource(R.string.trash_delete_forever_body, item.name)) },
            confirmButton = {
                Button(
                    onClick = { pendingDelete = null; viewModel.deleteForever(item) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.delete_forever)) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun TrashItemCard(
    item: TrashItem,
    trashManager: TrashManager,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit
) {
    val daysLeft = remember(item.expiresAtMs) {
        val remaining = item.expiresAtMs - System.currentTimeMillis()
        if (remaining <= 0) 0L else TimeUnit.MILLISECONDS.toDays(remaining)
    }

    val thumbnail = remember(item.trashFileName) {
        if (item.mediaType == "IMAGE") {
            runCatching {
                trashManager.openTrashInputStream(item.trashFileName)?.use { stream ->
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    BitmapFactory.decodeStream(stream, null, opts)?.asImageBitmap()
                }
            }.getOrNull()
        } else null
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail / icon
            Box(
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    Image(thumbnail, contentDescription = null, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(
                        when (item.mediaType) {
                            "VIDEO" -> Icons.Default.Videocam
                            "IMAGE" -> Icons.Default.Image
                            else    -> Icons.Default.InsertDriveFile
                        },
                        contentDescription = null,
                        tint   = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    formatBytes(item.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (daysLeft <= 1) "Expires soon" else "Expires in $daysLeft days",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (daysLeft <= 3) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Spacer(Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onRestore, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.RestoreFromTrash, contentDescription = stringResource(R.string.restore),
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDeleteForever, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.DeleteForever, contentDescription = stringResource(R.string.delete_forever),
                        tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val df = DecimalFormat("#.##")
    return when {
        bytes >= 1_000_000_000L -> "${df.format(bytes / 1_000_000_000.0)} GB"
        bytes >= 1_000_000L     -> "${df.format(bytes / 1_000_000.0)} MB"
        bytes >= 1_000L         -> "${df.format(bytes / 1_000.0)} KB"
        else                    -> "$bytes B"
    }
}
