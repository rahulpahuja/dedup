package com.rp.dedup.screens.dashboard.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rp.dedup.R
import com.rp.dedup.core.search.ImageSearchRepository
import com.rp.dedup.core.ui.ImagePreviewDialog

@Composable
fun AiSearchIcon() {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
        Icon(
            Icons.Default.Search,
            contentDescription = "AI Search",
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(10.dp).align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp),
            tint = Color(0xFF9C6FFF)
        )
    }
}

@Composable
fun SearchSuggestionsRow(onSuggestionClick: (String) -> Unit) {
    val suggestions = listOf("Screenshots", "WhatsApp", "Camera", "Downloads", "Telegram", "Instagram")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        suggestions.forEach { suggestion ->
            SuggestionChip(
                onClick = { onSuggestionClick(suggestion) },
                label = { Text(suggestion) },
                shape = RoundedCornerShape(12.dp),
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            )
        }
    }
}

@Composable
fun ImageSearchContent(
    query: String,
    results: List<ImageSearchRepository.SearchResult>,
    isSearching: Boolean,
    progress: Pair<Int, Int>,
    error: String?,
    onDeleteRequest: (Uri) -> Unit = {}
) {
    when {
        query.isBlank() -> {
            Box(modifier = Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ImageSearch, contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.search_desc),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.search_example),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
        }
        isSearching -> {
            Column(modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                val (labeled, total) = progress
                Text(
                    if (total > 0) stringResource(R.string.analyzing_images_progress, labeled, total)
                    else stringResource(R.string.analyzing_images),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (total > 0) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { labeled.toFloat() / total }, modifier = Modifier.fillMaxWidth(0.6f))
                }
            }
        }
        error != null -> {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }
        }
        results.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.SearchOff, contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.no_matching_images),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.try_different_words),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
        }
        else -> {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    if (results.size == 1) stringResource(R.string.image_matched, 1)
                    else stringResource(R.string.images_matched, results.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(results) { result -> ImageSearchResultItem(result, onDeleteRequest) }
                }
            }
        }
    }
}

@Composable
private fun ImageSearchResultItem(
    result: ImageSearchRepository.SearchResult,
    onDeleteRequest: (Uri) -> Unit = {}
) {
    var showPreview by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(Unit) { detectTapGestures(onLongPress = { showPreview = true }) }
    ) {
        AsyncImage(
            model = result.uri,
            contentDescription = result.matchedLabels.joinToString(),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
                .padding(horizontal = 4.dp, vertical = 3.dp)
        ) {
            Text(
                text = result.matchedLabels.take(2).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = Color.White),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(26.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable { onDeleteRequest(result.uri) },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete",
                tint = Color.White, modifier = Modifier.size(15.dp))
        }
    }

    if (showPreview) {
        ImagePreviewDialog(
            uri = result.uri,
            matchedLabels = result.matchedLabels,
            onDismiss = { showPreview = false }
        )
    }
}
