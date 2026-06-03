package com.rp.dedup.screens

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.format.Formatter.formatFileSize
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.rp.dedup.LocalDrawerState
import com.rp.dedup.R
import com.rp.dedup.core.repository.FileScannerRepository
import com.rp.dedup.core.viewmodels.FileScannerViewModel
import com.rp.dedup.core.model.ScannedFile
import com.rp.dedup.core.db.AppDatabase
import com.rp.dedup.core.repository.ScanHistoryRepository
import com.rp.dedup.ui.theme.DeDupTheme
import com.rp.dedup.core.ui.DeDupTopBar

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
    
    val selectedUris = remember { mutableStateListOf<Uri>() }
    var pendingDeleteUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.removeDeletedFilesFromUI(pendingDeleteUris)
            selectedUris.removeAll(pendingDeleteUris)
            pendingDeleteUris = emptyList()
        }
    }

    fun triggerDelete(uris: List<Uri>) {
        if (uris.isEmpty()) return
        pendingDeleteUris = uris
        
        try {
            uris.forEach { uri ->
                context.contentResolver.delete(uri, null, null)
            }
            viewModel.removeDeletedFilesFromUI(uris)
            selectedUris.removeAll(uris)
            pendingDeleteUris = emptyList()
        } catch (e: Exception) {
            uris.forEach { uri ->
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (_: Exception) {}
            }
            viewModel.removeDeletedFilesFromUI(uris)
            selectedUris.removeAll(uris)
        }
    }

    FileScannerContent(
        navController = navController,
        scanType = scanType,
        isScanning = isScanning,
        allFiles = allFiles,
        duplicateGroups = duplicateGroups,
        selectedUris = selectedUris,
        onScanClick = {
            if (isScanning) viewModel.cancelScanning()
            else {
                selectedUris.clear()
                viewModel.startScanning(extensions)
            }
        },
        onToggleSelect = { uri ->
            if (selectedUris.contains(uri)) selectedUris.remove(uri)
            else selectedUris.add(uri)
        },
        onDeleteSelected = { triggerDelete(selectedUris.toList()) }
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
    selectedUris: List<Uri>,
    onScanClick: () -> Unit,
    onToggleSelect: (Uri) -> Unit,
    onDeleteSelected: () -> Unit
) {
    val title = if (scanType == "pdf") stringResource(R.string.pdf_scanner_title) else stringResource(R.string.apk_scanner_title)
    val icon = if (scanType == "pdf") Icons.Default.Description else Icons.Default.SdCard

    Scaffold(
        topBar = {
            DeDupTopBar(
                title = title,
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (selectedUris.isNotEmpty()) {
                        IconButton(onClick = onDeleteSelected) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_selected_btn, selectedUris.size, ""), tint = MaterialTheme.colorScheme.error)
                        }
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
                    Text(stringResource(R.string.no_files_found, scanType.uppercase()))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (duplicateGroups.isEmpty() && !isScanning) {
                        item {
                            Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Text(stringResource(R.string.no_duplicate_files_found, scanType.uppercase()))
                                    Text(
                                        stringResource(R.string.total_files_scanned, allFiles.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        duplicateGroups.forEach { group ->
                            item {
                                DuplicateGroupItem(
                                    group = group,
                                    icon = icon,
                                    selectedUris = selectedUris,
                                    onToggleSelect = onToggleSelect
                                )
                            }
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isScanning) stringResource(R.string.searching_files) else title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (!isScanning) {
                Text(
                    text = stringResource(R.string.duplicate_groups_found, groupCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Button(
            onClick = onScanClick,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            modifier = Modifier.height(36.dp)
        ) {
            Icon(
                imageVector = if (isScanning) Icons.Default.Stop else Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(if (isScanning) stringResource(R.string.stop_btn) else stringResource(R.string.scan_btn), style = MaterialTheme.typography.labelLarge)
        }
    }
    HorizontalDivider()
}

@Composable
private fun DuplicateGroupItem(
    group: List<ScannedFile>,
    icon: ImageVector,
    selectedUris: List<Uri>,
    onToggleSelect: (Uri) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.group_name_label, group.first().name),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            group.forEachIndexed { index, file ->
                FileItem(
                    file = file,
                    icon = icon,
                    isSelected = selectedUris.contains(file.uri),
                    isKeep = index == 0,
                    onToggleSelect = { onToggleSelect(file.uri) }
                )
            }
        }
    }
}

@Composable
private fun FileItem(
    file: ScannedFile,
    icon: ImageVector,
    isSelected: Boolean,
    isKeep: Boolean,
    onToggleSelect: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleSelect() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggleSelect() },
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isKeep) Color(0xFF2E7D32) else MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = file.path,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isKeep) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = Color(0xFFE8F5E9),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            stringResource(R.string.keep),
                            color = Color(0xFF2E7D32),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
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
            selectedUris = emptyList(),
            onScanClick = {},
            onToggleSelect = {},
            onDeleteSelected = {}
        )
    }
}
