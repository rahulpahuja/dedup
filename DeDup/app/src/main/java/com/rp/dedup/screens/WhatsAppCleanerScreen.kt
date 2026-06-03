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
import com.rp.dedup.core.model.WhatsAppCleanerState
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
    val state by viewModel.state.collectAsStateWithLifecycle()

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
                        onDelete = viewModel::deleteFiles
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
    // Basic implementation to show something localized
    Column(modifier = modifier.fillMaxSize()) {
        Text(stringResource(R.string.results), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
        // ... list groups ...
    }
}

@Preview(showBackground = true)
@Composable
private fun WhatsAppCleanerScreenPreview() {
    DeDupTheme {
        WhatsAppCleanerScreen(navController = NavHostController(LocalContext.current))
    }
}
