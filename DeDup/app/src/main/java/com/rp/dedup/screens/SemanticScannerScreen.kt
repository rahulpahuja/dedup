package com.rp.dedup.screens

import android.net.Uri
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.rp.dedup.R
import com.rp.dedup.core.analytics.AnalyticsManager
import com.rp.dedup.core.viewmodels.SemanticScannerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemanticScannerScreen(navController: NavHostController) {
    val context = LocalContext.current
    val viewModel: SemanticScannerViewModel =
        viewModel(factory = SemanticScannerViewModel.Factory(context))
    val analyticsManager = remember { AnalyticsManager.getInstance(context) }

    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { analyticsManager.logScreenView("SemanticScanner") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.semantic_scan_title), fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(R.string.semantic_scan_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // Index status card
            Surface(
                shape         = RoundedCornerShape(16.dp),
                tonalElevation = 2.dp,
                modifier      = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${state.indexedCount} photos indexed",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            if (state.indexedCount == 0)
                                "Run a photo scan first to build the semantic index"
                            else
                                "AI embeddings ready — find visually similar duplicates",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Scan button / progress
            if (state.isScanning) {
                val (done, total) = state.progress
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Comparing albums… $done / $total",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Button(
                    onClick  = { viewModel.scan() },
                    enabled  = state.indexedCount > 0,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.ImageSearch, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.semantic_scan_btn))
                }
            }

            state.error?.let { err ->
                Spacer(Modifier.height(12.dp))
                Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(16.dp))

            if (state.groups.isEmpty() && !state.isScanning) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CenterFocusWeak, null, modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (state.indexedCount == 0) stringResource(R.string.semantic_empty_no_index)
                            else stringResource(R.string.semantic_empty_no_dupes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Text(
                    "${state.groups.size} similar group${if (state.groups.size == 1) "" else "s"} found",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding      = PaddingValues(bottom = 16.dp)
                ) {
                    items(state.groups) { group ->
                        SemanticGroupCard(
                            uris      = group,
                            onDismiss = { viewModel.dismissGroup(group) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SemanticGroupCard(uris: List<Uri>, onDismiss: () -> Unit) {
    Surface(
        shape         = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        modifier      = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment  = Alignment.CenterVertically,
                modifier           = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ContentCopy, null, tint = Color(0xFF9C6FFF), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "${uris.size} similar photos",
                    style  = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                uris.take(4).forEach { uri ->
                    AsyncImage(
                        model             = uri,
                        contentDescription = null,
                        contentScale      = ContentScale.Crop,
                        modifier          = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
                if (uris.size > 4) {
                    Box(
                        modifier         = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+${uris.size - 4}", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
