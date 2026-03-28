package com.rp.dedup.screens

import android.text.format.Formatter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.rp.dedup.core.utils.CacheCleaner
import com.rp.dedup.core.utils.CleaningProgress
import com.rp.dedup.ui.theme.DeDupTheme
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheCleanerScreen(navController: NavHostController) {
    val context = LocalContext.current
    var progressState by remember { mutableStateOf<CleaningProgress>(CleaningProgress.Scanning(0)) }
    var startCleaning by remember { mutableStateOf(false) }

    LaunchedEffect(startCleaning) {
        if (startCleaning) {
            CacheCleaner.clearAllCacheFlow(context).collect {
                progressState = it
            }
        } else {
            // Initially just show the size
            val size = CacheCleaner.getCacheSize(context)
            if (size == 0L) {
                progressState = CleaningProgress.Finished(0)
            } else {
                progressState = CleaningProgress.Scanning(0) // Reset to scanning if needed or just idle
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cache Cleaner") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(32.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (progressState is CleaningProgress.Finished) Icons.Default.DoneAll else Icons.Default.CleaningServices,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            when (val state = progressState) {
                is CleaningProgress.Scanning -> {
                    Text(
                        "Analyzing Cache...",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Found ${state.filesFound} files so far",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = { startCleaning = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Start Cleaning")
                    }
                }
                is CleaningProgress.Cleaning -> {
                    Text(
                        "Cleaning in Progress",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val animatedProgress by animateFloatAsState(targetValue = state.progress, label = "cleanProgress")
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp),
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "${(state.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Cleared: ${Formatter.formatFileSize(context, state.bytesCleared)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                is CleaningProgress.Finished -> {
                    Text(
                        "Cache Cleaned!",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF34A853)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Successfully cleared ${Formatter.formatFileSize(context, state.totalBytesCleared)}",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Back to Dashboard")
                    }
                }
                is CleaningProgress.Error -> {
                    Text(
                        "Error Occurred",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(state.message, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { startCleaning = true }) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}


@Preview
@Composable
private fun CacheCleanerScreenPreview() {
    DeDupTheme() {
        CacheCleanerScreen(navController = NavHostController(LocalContext.current))

    }
    
}