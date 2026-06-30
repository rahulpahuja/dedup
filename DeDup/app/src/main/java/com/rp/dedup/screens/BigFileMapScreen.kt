package com.rp.dedup.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.rp.dedup.core.model.FolderNode
import com.rp.dedup.core.deepoptimization.TreemapLayoutCalculator
import com.rp.dedup.core.deepoptimization.TreemapLayoutCalculator.TreemapCell
import com.rp.dedup.core.model.state.BigFileMapState
import com.rp.dedup.core.viewmodels.BigFileMapViewModel
import com.rp.dedup.ui.theme.DeDupTheme
import androidx.compose.ui.res.stringResource
import com.rp.dedup.R
import com.rp.dedup.core.ui.DeDupTopBar

private val TREEMAP_PALETTE = listOf(
    Color(0xFF4285F4), Color(0xFFEA4335), Color(0xFF34A853), Color(0xFFFBBC05),
    Color(0xFF9C27B0), Color(0xFF00ACC1), Color(0xFFFF6D00), Color(0xFF1A73E8)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BigFileMapScreen(navController: NavHostController) {
    val context = LocalContext.current
    val viewModel: BigFileMapViewModel = viewModel(
        factory = BigFileMapViewModel.factory(context)
    )
    val state by viewModel.state.collectAsState()
    val analyticsManager = remember { com.rp.dedup.core.analytics.AnalyticsManager.getInstance(context) }
    var navStack by remember { mutableStateOf(listOf<FolderNode>()) }

    LaunchedEffect(Unit) {
        analyticsManager.logScreenView("BigFileMap")
    }

    Scaffold(
        topBar = {
            DeDupTopBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.screen_big_file_map),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        if (navStack.isNotEmpty()) {
                            Text(
                                navStack.last().name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (navStack.isNotEmpty()) navStack = navStack.dropLast(1)
                        else navController.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                is BigFileMapState.Idle -> BigFileMapIdleView(onScan = { viewModel.startScan() })
                is BigFileMapState.Scanning -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(24.dp))
                    Text("Building storage map...", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Walking directory tree",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is BigFileMapState.Results -> {
                    val displayNodes = if (navStack.isEmpty()) s.root.children else navStack.last().children
                    BigFileMapContent(
                        nodes = displayNodes,
                        totalBytes = if (navStack.isEmpty()) s.root.sizeBytes else navStack.last().sizeBytes,
                        onNodeTap = { node ->
                            if (node.children.isNotEmpty()) navStack = navStack + node
                        },
                        onRescan = { viewModel.startScan() }
                    )
                }
                is BigFileMapState.Error -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    Text(s.message, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.startScan() }) { Text("Retry") }
                }
            }
        }
    }
}

@Composable
private fun BigFileMapIdleView(onScan: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.AccountTree,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Color(0xFF1A73E8)
        )
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.storage_treemap), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.big_file_map_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onScan, shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.build_map))
        }
    }
}

@Composable
private fun BigFileMapContent(
    nodes: List<FolderNode>,
    totalBytes: Long,
    onNodeTap: (FolderNode) -> Unit,
    onRescan: () -> Unit
) {
    if (nodes.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("No subfolders found at this level.", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Total: ${totalBytes.toReadableSize()}  ·  Tap to drill down",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onRescan) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Rescan", style = MaterialTheme.typography.labelSmall)
            }
        }

        TreemapCanvas(
            nodes = nodes,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp),
            onNodeTap = onNodeTap
        )

        Spacer(Modifier.height(8.dp))
        TreemapLegend(
            nodes = nodes.take(TreemapLayoutCalculator.COLOR_COUNT),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun TreemapCanvas(
    nodes: List<FolderNode>,
    modifier: Modifier = Modifier,
    onNodeTap: (FolderNode) -> Unit
) {
    val textMeasurer = rememberTextMeasurer()
    var cells by remember(nodes) { mutableStateOf(listOf<TreemapCell>()) }

    Canvas(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .pointerInput(cells) {
                detectTapGestures { tapOffset ->
                    val tapped = cells
                        .filter { cell -> cell.rect.contains(tapOffset) }
                        .minByOrNull { cell -> cell.rect.width * cell.rect.height }
                    tapped?.let { onNodeTap(it.node) }
                }
            }
    ) {
        if (cells.isEmpty() || cells.first().let { it.rect.width != size.width || it.rect.height != size.height }) {
            val bounds = Rect(Offset.Zero, Size(size.width, size.height))
            cells = TreemapLayoutCalculator.compute(nodes, bounds, maxDepth = 1)
        }

        cells.forEach { cell ->
            val color = TREEMAP_PALETTE[cell.colorIndex % TREEMAP_PALETTE.size]
            val alpha = if (cell.depth == 0) 1f else 0.7f

            drawRect(
                color = color.copy(alpha = alpha),
                topLeft = Offset(cell.rect.left, cell.rect.top),
                size = Size(cell.rect.width, cell.rect.height)
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.25f),
                topLeft = Offset(cell.rect.left, cell.rect.top),
                size = Size(cell.rect.width, cell.rect.height),
                style = Stroke(width = 1.5f)
            )

            if (cell.depth == 0 && cell.rect.width > 60f && cell.rect.height > 40f) {
                val nameStyle = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                val sizeStyle = TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp)

                val nameMeasured = textMeasurer.measure(cell.node.name, nameStyle)
                val sizeMeasured = textMeasurer.measure(cell.node.sizeBytes.toReadableSize(), sizeStyle)

                val totalTextHeight = nameMeasured.size.height + 2 + sizeMeasured.size.height
                val cx = cell.rect.left + cell.rect.width / 2
                val cy = cell.rect.top + cell.rect.height / 2

                if (nameMeasured.size.width < cell.rect.width - 8) {
                    drawText(
                        textMeasurer = textMeasurer,
                        text = cell.node.name,
                        topLeft = Offset(cx - nameMeasured.size.width / 2, cy - totalTextHeight / 2),
                        style = nameStyle
                    )
                    drawText(
                        textMeasurer = textMeasurer,
                        text = cell.node.sizeBytes.toReadableSize(),
                        topLeft = Offset(cx - sizeMeasured.size.width / 2, cy - totalTextHeight / 2 + nameMeasured.size.height + 2),
                        style = sizeStyle
                    )
                }
            }
        }
    }
}

@Composable
private fun TreemapLegend(nodes: List<FolderNode>, modifier: Modifier = Modifier) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(nodes) { index, node ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(TREEMAP_PALETTE[index % TREEMAP_PALETTE.size], RoundedCornerShape(2.dp))
                )
                Text(
                    "${node.name} (${node.sizeBytes.toReadableSize()})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun Long.toReadableSize(): String = when {
    this < 1_024 -> "$this B"
    this < 1_048_576 -> "${this / 1_024} KB"
    this < 1_073_741_824 -> "${this / 1_048_576} MB"
    else -> "${"%.1f".format(this.toDouble() / 1_073_741_824)} GB"
}

@Preview(showBackground = true)
@Composable
private fun BigFileMapScreenPreview() {
    DeDupTheme {
        val navController = rememberNavController()
        BigFileMapScreen(navController = navController)
    }
}
