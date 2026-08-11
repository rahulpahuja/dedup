package com.rp.dedup.screens

import android.content.res.Configuration
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.rp.dedup.R
import com.rp.dedup.core.analytics.AnalyticsManager
import com.rp.dedup.core.repository.MemoryGroup
import com.rp.dedup.core.viewmodels.MemoriesViewModel
import com.rp.dedup.ui.theme.DeDupTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoriesScreen(navController: NavHostController) {
    val context = LocalContext.current
    val viewModel: MemoriesViewModel = viewModel(factory = MemoriesViewModel.Factory(context))
    val analyticsManager = remember { AnalyticsManager.getInstance(context) }

    val memories by viewModel.memories.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    com.rp.dedup.core.analytics.TrackFeatureUsage("Memories")
    LaunchedEffect(Unit) { analyticsManager.logScreenView("Memories") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.memories_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                memories.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.PhotoLibrary, null, modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.memories_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(memories) { group -> MemoryGroupCard(group) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryGroupCard(group: MemoryGroup) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Event, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    pluralYearsAgo(group.yearsAgo),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                group.uris.take(4).forEach { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
                if (group.uris.size > 4) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+${group.uris.size - 4}", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

private fun pluralYearsAgo(yearsAgo: Int): String =
    if (yearsAgo == 1) "1 year ago" else "$yearsAgo years ago"

@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
private fun MemoriesScreenPreview() {
    DeDupTheme {
        MemoriesScreen(navController = rememberNavController())
    }
}

@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
private fun MemoryGroupCardPreview() {
    DeDupTheme {
        MemoryGroupCard(group = MemoryGroup(yearsAgo = 2, uris = listOf(Uri.EMPTY)))
    }
}
