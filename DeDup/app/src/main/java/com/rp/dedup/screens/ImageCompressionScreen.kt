package com.rp.dedup.screens

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
import com.rp.dedup.core.viewmodels.CompressionCandidate
import com.rp.dedup.core.viewmodels.ImageCompressionViewModel
import java.text.DecimalFormat
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageCompressionScreen(navController: NavHostController) {
    val context = LocalContext.current
    val viewModel: ImageCompressionViewModel =
        viewModel(factory = ImageCompressionViewModel.Factory(context))
    val analyticsManager = remember { AnalyticsManager.getInstance(context) }

    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { analyticsManager.logScreenView("ImageCompression") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.compression_title), fontWeight = FontWeight.Bold)
                        Text(
                            "${state.candidates.size} compressible images",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.candidates.isNotEmpty()) {
                        val allSelected = state.candidates.all { it.isSelected }
                        TextButton(onClick = { viewModel.selectAll(!allSelected) }) {
                            Text(if (allSelected) stringResource(R.string.deselect_all) else stringResource(R.string.select_all))
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (!state.isCompressing && state.candidates.any { it.isSelected }) {
                val selectedCount = state.candidates.count { it.isSelected }
                val estimatedSaving = state.candidates
                    .filter { it.isSelected }
                    .sumOf { it.sizeBytes } * (1.0 - state.quality / 100.0) * 0.6
                Surface(tonalElevation = 4.dp) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Est. savings: ~${formatBytes(estimatedSaving.toLong())} from $selectedCount images",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick  = { viewModel.compressSelected() },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape    = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Compress, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.compress_btn, selectedCount))
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (state.isCompressing) {
            val (done, total) = state.progress
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(progress = { if (total > 0) done.toFloat() / total else 0f })
                    Spacer(Modifier.height(16.dp))
                    Text("Compressing… $done / $total")
                    if (state.totalSavedBytes > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Saved ${formatBytes(state.totalSavedBytes)} so far",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF34A853)
                        )
                    }
                }
            }
            return@Scaffold
        }

        if (state.results.isNotEmpty() && !state.isCompressing) {
            // Results summary
            val saved = state.totalSavedBytes
            Surface(
                modifier      = Modifier.fillMaxWidth().padding(padding).padding(horizontal = 16.dp, vertical = 8.dp),
                shape         = RoundedCornerShape(16.dp),
                color         = Color(0xFF34A853).copy(alpha = 0.1f)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF34A853))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "${state.results.size} images compressed — saved ${formatBytes(saved)}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }

        LazyColumn(
            contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp) + padding,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Quality slider
            item {
                QualitySliderCard(
                    quality          = state.quality,
                    onQualityChange  = { viewModel.setQuality(it) },
                    deleteOriginals  = state.deleteOriginals,
                    onDeleteChange   = { viewModel.setDeleteOriginals(it) }
                )
                Spacer(Modifier.height(12.dp))
            }

            if (state.candidates.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(64.dp),
                                tint = Color(0xFF34A853).copy(alpha = 0.5f))
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.compression_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(state.candidates, key = { it.uri.toString() }) { candidate ->
                    CompressionCandidateRow(
                        candidate = candidate,
                        quality   = state.quality,
                        onToggle  = { viewModel.toggleSelection(candidate.uri) }
                    )
                }
            }
        }
    }
}

@Composable
private fun QualitySliderCard(
    quality: Int,
    onQualityChange: (Int) -> Unit,
    deleteOriginals: Boolean,
    onDeleteChange: (Boolean) -> Unit
) {
    Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Quality: $quality%",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f)
                )
                val label = when {
                    quality >= 90 -> "Lossless-ish"
                    quality >= 80 -> "Excellent"
                    quality >= 70 -> "Good"
                    quality >= 55 -> "Fair"
                    else          -> "Low"
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
            Slider(
                value         = quality.toFloat(),
                onValueChange = { onQualityChange(it.roundToInt()) },
                valueRange    = 50f..95f,
                steps         = 44
            )
            Text(
                "WebP lossy — avg 40-60% file size reduction",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Delete originals", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Switch(checked = deleteOriginals, onCheckedChange = onDeleteChange)
            }
        }
    }
}

@Composable
private fun CompressionCandidateRow(
    candidate: CompressionCandidate,
    quality: Int,
    onToggle: () -> Unit
) {
    val estimatedNewSize = (candidate.sizeBytes * (quality / 100.0) * 0.6).toLong()
    val savings = candidate.sizeBytes - estimatedNewSize

    Row(
        modifier          = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = candidate.isSelected, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(8.dp))
        AsyncImage(
            model             = candidate.uri,
            contentDescription = null,
            contentScale      = ContentScale.Crop,
            modifier          = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                candidate.name,
                style   = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                "${formatBytes(candidate.sizeBytes)} → ~${formatBytes(estimatedNewSize)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "-${formatBytes(savings)}",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = Color(0xFF34A853)
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val df = DecimalFormat("#.#")
    return when {
        bytes >= 1_000_000_000L -> "${df.format(bytes / 1_000_000_000.0)} GB"
        bytes >= 1_000_000L     -> "${df.format(bytes / 1_000_000.0)} MB"
        bytes >= 1_000L         -> "${df.format(bytes / 1_000.0)} KB"
        else                    -> "$bytes B"
    }
}

private operator fun PaddingValues.plus(other: PaddingValues): PaddingValues =
    PaddingValues(
        start  = calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) +
                 other.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
        top    = calculateTopPadding() + other.calculateTopPadding(),
        end    = calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) +
                 other.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
        bottom = calculateBottomPadding() + other.calculateBottomPadding()
    )
