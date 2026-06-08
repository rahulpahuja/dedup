package com.rp.dedup.screens

import android.net.Uri
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.rp.dedup.R
import com.rp.dedup.LocalUserProfileViewModel
import com.rp.dedup.Screen
import com.rp.dedup.core.model.state.WhatsAppCleanerState
import com.rp.dedup.core.model.WhatsAppFile
import com.rp.dedup.core.model.WhatsAppScanResult
import com.rp.dedup.core.ui.DeDupTopBar
import com.rp.dedup.core.viewmodels.WhatsAppCleanerViewModel
import com.rp.dedup.ui.theme.DeDupTheme

private val WaGreen = Color(0xFF25D366)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAppCleanerScreen(navController: NavHostController) {
    val context = LocalContext.current
    val viewModel: WhatsAppCleanerViewModel =
        viewModel(factory = WhatsAppCleanerViewModel.factory(context))
    val profileViewModel = LocalUserProfileViewModel.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showGuestSignInDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            DeDupTopBar(
                title = stringResource(R.string.screen_whatsapp_cleaner),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                is WhatsAppCleanerState.Idle ->
                    WaIdleView(modifier = Modifier, onScan = viewModel::startScan)
                is WhatsAppCleanerState.Scanning ->
                    WaScanningView(modifier = Modifier, phase = s.phase)
                is WhatsAppCleanerState.Results ->
                    WaResultsView(
                        modifier = Modifier,
                        data     = s.data,
                        onDelete = { uris ->
                            if (profileViewModel.isGuest) showGuestSignInDialog = true
                            else viewModel.deleteFiles(uris)
                        }
                    )
                is WhatsAppCleanerState.Error ->
                    WaErrorView(
                        modifier = Modifier,
                        message  = s.message,
                        onRetry  = viewModel::startScan
                    )
            }
        }
    }

    if (showGuestSignInDialog) {
        GuestSignInDialog(
            onDismiss = { showGuestSignInDialog = false },
            onSignIn = {
                showGuestSignInDialog = false
                navController.navigate(Screen.Login.route) {
                    popUpTo(Screen.Dashboard.route) { inclusive = false }
                }
            }
        )
    }
}

// ─── Idle ─────────────────────────────────────────────────────────────────────

@Composable
private fun WaIdleView(modifier: Modifier, onScan: () -> Unit) {
    Column(
        modifier          = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = WaGreen
        )
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.scan_whatsapp_storage), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.whatsapp_cleaner_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onScan,
            colors = ButtonDefaults.buttonColors(containerColor = WaGreen),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(0.6f).height(48.dp)
        ) {
            Text(stringResource(R.string.start_scan), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun WaScanningView(modifier: Modifier, phase: String) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = WaGreen)
        Spacer(Modifier.height(24.dp))
        Text(phase, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.checking_media_folders), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WaErrorView(modifier: Modifier, message: String, onRetry: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.ErrorOutline, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Text(message, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
private fun WaResultsView(
    modifier: Modifier,
    data: WhatsAppScanResult,
    onDelete: (List<Uri>) -> Unit
) {
    val context = LocalContext.current
    val totalReclaimable = data.duplicateMedia.sumOf { g -> g.drop(1).sumOf { it.size } } +
                          data.duplicateStatuses.sumOf { g -> g.drop(1).sumOf { it.size } } +
                          data.duplicateDocs.sumOf { g -> g.drop(1).sumOf { it.size } } +
                          data.redundantSentMedia.sumOf { it.size }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = WaGreen.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.potential_savings),
                        style = MaterialTheme.typography.labelMedium,
                        color = WaGreen
                    )
                    Text(
                        android.text.format.Formatter.formatFileSize(context, totalReclaimable),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = WaGreen
                    )
                }
            }
        }

        if (data.redundantSentMedia.isNotEmpty()) {
            item {
                WaSectionHeader(
                    title = stringResource(R.string.redundant_sent_media),
                    subtitle = stringResource(R.string.sent_media_desc),
                    onDeleteAll = { onDelete(data.redundantSentMedia.map { it.uri }) }
                )
            }
            items(data.redundantSentMedia) { file ->
                WaFileItem(file = file, onDelete = { onDelete(listOf(file.uri)) })
            }
        }

        if (data.duplicateMedia.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.duplicate_groups),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            data.duplicateMedia.forEach { group ->
                items(group.drop(1)) { file ->
                    WaFileItem(file = file, onDelete = { onDelete(listOf(file.uri)) })
                }
            }
        }

        if (data.largeFiles.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.large_files),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(data.largeFiles.take(10)) { file ->
                WaFileItem(file = file, onDelete = { onDelete(listOf(file.uri)) })
            }
        }
    }
}

@Composable
private fun WaSectionHeader(title: String, subtitle: String, onDeleteAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onDeleteAll, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
            Text(stringResource(R.string.delete_all))
        }
    }
}

@Composable
private fun WaFileItem(file: WhatsAppFile, onDelete: () -> Unit) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when {
                    file.name.endsWith(".mp4", true) -> Icons.Default.Movie
                    file.name.endsWith(".jpg", true) || file.name.endsWith(".png", true) -> Icons.Default.Image
                    else -> Icons.Default.Description
                },
                contentDescription = null,
                tint = WaGreen,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(android.text.format.Formatter.formatFileSize(context, file.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WhatsAppCleanerScreenPreview() {
    DeDupTheme {
        WhatsAppCleanerScreen(navController = NavHostController(LocalContext.current))
    }
}
