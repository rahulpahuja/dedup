package com.rp.dedup.screens

import android.net.Uri
import android.text.format.Formatter.formatFileSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.rp.dedup.core.repository.FileScannerRepository
import com.rp.dedup.core.viewmodels.FileScannerViewModel
import com.rp.dedup.core.data.ScannedFile
import com.rp.dedup.core.db.AppDatabase
import com.rp.dedup.core.repository.ScanHistoryRepository
import com.rp.dedup.ui.theme.DeDupTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileScannerScreen(
    navController: NavHostController,
    scanType: String, // "pdf" or "apk"
    extensions: List<String>
) {
    val context = LocalContext.current
    val historyType = if (scanType == "pdf") "FILE_PDF" else "FILE_APK"
    val viewModel: FileScannerViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return FileScannerViewModel(
                    repository = FileScannerRepository(context),
                    historyRepository = ScanHistoryRepository(AppDatabase.getDatabase(context).scanHistoryDao()),
                    scanTypeName = historyType
                ) as T
            }
        }
    )

    val duplicateGroups by viewModel.duplicateGroups.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val allFiles by viewModel.files.collectAsState()

    FileScannerContent(
        navController = navController,
        scanType = scanType,
        isScanning = isScanning,
        allFiles = allFiles,
        duplicateGroups = duplicateGroups,
        onScanClick = {
            if (isScanning) viewModel.cancelScanning()
            else viewModel.startScanning(extensions)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileScannerContent(
    navController: NavHostController,
    scanType: String,
    isScanning: Boolean,
    allFiles: List<ScannedFile>,
    duplicateGroups: List<List<ScannedFile>>,
    onScanClick: () -> Unit
) {
    val title = if (scanType == "pdf") "PDF Scanner" else "APK Scanner"
    val icon = if (scanType == "pdf") Icons.Default.Description else Icons.Default.SdCard

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
                .background(MaterialTheme.colorScheme.background)
        ) {
            ScannerHeader(
                title = title,
                isScanning = isScanning,
                groupCount = duplicateGroups.size,
                onScanClick = onScanClick
            )

            if (isScanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (!isScanning && allFiles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No ${scanType.uppercase()} files found. Tap Scan to search.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    duplicateGroups.forEach { group ->
                        item {
                            DuplicateGroupItem(group, icon)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScannerHeader(
    title: String,
    isScanning: Boolean,
    groupCount: Int,
    onScanClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isScanning) "Searching files..." else title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (!isScanning) {
                Text(
                    text = "$groupCount duplicate groups found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Button(
            onClick = onScanClick,
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = if (isScanning) Icons.Default.Stop else Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(if (isScanning) "Stop" else "Scan")
        }
    }
    HorizontalDivider()
}

@Composable
private fun DuplicateGroupItem(group: List<ScannedFile>, icon: ImageVector) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Group: ${group.first().name}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            group.forEach { file ->
                FileItem(file, icon)
            }
        }
    }
}

@Composable
private fun FileItem(file: ScannedFile, icon: ImageVector) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.path,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatFileSize(context, file.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FileScannerPreview() {
    val mockFiles = listOf(
        ScannedFile(Uri.EMPTY, "Invoice_1.pdf", 1024L * 1024L, "/storage/emulated/0/Download/Invoice_1.pdf", "pdf"),
        ScannedFile(Uri.EMPTY, "Invoice_1.pdf", 1024L * 1024L, "/storage/emulated/0/Documents/Invoice_1.pdf", "pdf")
    )
    DeDupTheme {
        FileScannerContent(
            navController = rememberNavController(),
            scanType = "pdf",
            isScanning = false,
            allFiles = mockFiles,
            duplicateGroups = listOf(mockFiles),
            onScanClick = {}
        )
    }
}
