package com.rp.dedup.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.services.drive.model.File
import com.rp.dedup.BuildConfig
import com.rp.dedup.core.drive.GoogleDriveManager
import kotlinx.coroutines.launch

// ── File category helpers ──────────────────────────────────────────────────

private enum class FileCategory(
    val displayName: String,
    val icon: ImageVector,
    val tint: Color
) {
    IMAGE("Image", Icons.Default.Image, Color(0xFF4CAF50)),
    VIDEO("Video", Icons.Default.VideoLibrary, Color(0xFF9C27B0)),
    AUDIO("Audio", Icons.Default.MusicNote, Color(0xFFFF9800)),
    PDF("PDF", Icons.Default.PictureAsPdf, Color(0xFFF44336)),
    DOCUMENT("Document", Icons.Default.Description, Color(0xFF2196F3)),
    SPREADSHEET("Spreadsheet", Icons.Default.GridOn, Color(0xFF43A047)),
    PRESENTATION("Presentation", Icons.Default.Slideshow, Color(0xFFFF5722)),
    ARCHIVE("Archive", Icons.Default.FolderZip, Color(0xFF795548)),
    OTHER("File", Icons.AutoMirrored.Filled.InsertDriveFile, Color(0xFF607D8B))
}

private fun mimeTypeToCategory(mimeType: String?): FileCategory = when {
    mimeType == null -> FileCategory.OTHER
    mimeType.startsWith("image/") -> FileCategory.IMAGE
    mimeType.startsWith("video/") -> FileCategory.VIDEO
    mimeType.startsWith("audio/") -> FileCategory.AUDIO
    mimeType == "application/pdf" -> FileCategory.PDF
    mimeType.contains("word") || mimeType == "application/vnd.google-apps.document" -> FileCategory.DOCUMENT
    mimeType.contains("spreadsheet") || mimeType.contains("excel") -> FileCategory.SPREADSHEET
    mimeType.contains("presentation") || mimeType.contains("powerpoint") -> FileCategory.PRESENTATION
    mimeType.contains("zip") || mimeType.contains("tar") || mimeType.contains("compress") -> FileCategory.ARCHIVE
    else -> FileCategory.OTHER
}

private fun formatSize(bytes: Long?): String {
    if (bytes == null || bytes <= 0L) return ""
    return when {
        bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024L -> "%.0f KB".format(bytes / 1_024.0)
        else -> "$bytes B"
    }
}

// ── Stateful screen ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleDriveScannerScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val driveManager = remember { GoogleDriveManager(context) }
    val credentialManager = remember { CredentialManager.create(context) }

    var isScanning by remember { mutableStateOf(false) }
    var duplicateGroups by remember { mutableStateOf<List<List<File>>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var driveCredential by remember { mutableStateOf<GoogleAccountCredential?>(null) }

    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            errorMessage = "Permission granted. Please tap Connect again to scan."
        } else {
            Toast.makeText(context, "Error Signing into your drive", Toast.LENGTH_SHORT).show()
        }
    }

    val request = remember {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .build()
        GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
    }

    val onConnectAction: () -> Unit = {
        scope.launch {
            isScanning = true
            errorMessage = null
            try {
                val result = credentialManager.getCredential(context, request)
                val credential = GoogleAccountCredential.usingOAuth2(context, GoogleDriveManager.SCOPES)
                (result.credential as? CustomCredential)?.let {
                    try { GoogleIdTokenCredential.createFrom(it.data).id } catch (_: Exception) { null }
                }?.let { credential.selectedAccountName = it }
                driveCredential = credential
                duplicateGroups = driveManager.scanForDuplicates(credential)
            } catch (e: UserRecoverableAuthIOException) {
                authLauncher.launch(e.intent)
            } catch (e: Exception) {
                errorMessage = "Sign-in failed: ${e.message}"
            } finally {
                isScanning = false
            }
        }
    }

    GoogleDriveScannerContent(
        isScanning = isScanning,
        duplicateGroups = duplicateGroups,
        errorMessage = errorMessage,
        onBack = { navController.popBackStack() },
        onRetry = {
            errorMessage = null
            onConnectAction()
        },
        onConnect = onConnectAction,
        onDelete = { fileId ->
            val credential = driveCredential
            if (credential != null) {
                scope.launch {
                    val success = driveManager.deleteFile(credential, fileId)
                    if (success) {
                        duplicateGroups = duplicateGroups.mapNotNull { group ->
                            group.filter { it.id != fileId }.takeIf { it.size > 1 }
                        }
                    } else {
                        errorMessage = "Failed to delete file."
                    }
                }
            }
        }
    )
}

// ── Stateless content ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GoogleDriveScannerContent(
    isScanning: Boolean,
    duplicateGroups: List<List<File>>,
    errorMessage: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onConnect: () -> Unit,
    onDelete: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Google Drive Cleanup") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                errorMessage != null -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(8.dp)
                    )
                    Button(onClick = onRetry) { Text("Retry") }
                }
                isScanning -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.padding(32.dp))
                            Text("Scanning your Drive for duplicates...")
                        }
                    }
                }
                duplicateGroups.isEmpty() -> EmptyDriveState(onConnect = onConnect)
                else -> {
                    // Group by category, sorted by enum ordinal so order is deterministic
                    val byCategory = remember(duplicateGroups) {
                        duplicateGroups
                            .groupBy { mimeTypeToCategory(it.first().mimeType) }
                            .entries
                            .sortedBy { it.key.ordinal }
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        byCategory.forEach { (category, groups) ->
                            stickyHeader(key = "header_${category.name}") {
                                CategorySectionHeader(category = category, groupCount = groups.size)
                            }
                            items(
                                items = groups,
                                key = { it.first().md5Checksum ?: it.first().id ?: it.hashCode() }
                            ) { group ->
                                DuplicateGroupItem(group = group, onDelete = onDelete)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Section header ─────────────────────────────────────────────────────────

@Composable
private fun CategorySectionHeader(category: FileCategory, groupCount: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(category.tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    tint = category.tint,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "${category.displayName}s",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = category.tint
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$groupCount duplicate group${if (groupCount == 1) "" else "s"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Empty state ────────────────────────────────────────────────────────────

@Composable
private fun EmptyDriveState(onConnect: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Cloud,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Clean up your Cloud",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Find and remove identical files stored in your Google Drive.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onConnect,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Connect Google Drive")
        }
    }
}

// ── Duplicate group card ───────────────────────────────────────────────────

@Composable
private fun DuplicateGroupItem(group: List<File>, onDelete: (String) -> Unit) {
    val category = remember(group.first().mimeType) { mimeTypeToCategory(group.first().mimeType) }
    var fileToDelete by remember { mutableStateOf<File?>(null) }

    fileToDelete?.let { file ->
        DeleteConfirmDialog(
            file = file,
            category = category,
            onConfirm = {
                file.id?.let { onDelete(it) }
                fileToDelete = null
            },
            onDismiss = { fileToDelete = null }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: thumbnail / icon  +  name + category badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilePreviewThumbnail(
                    thumbnailUrl = group.first().thumbnailLink,
                    category = category,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.first().name ?: "Unknown File",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryBadge(category)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${group.size} copies",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(6.dp))

            // Per-copy rows
            group.forEachIndexed { index, file ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Copy ${index + 1}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        val sizeLabel = formatSize(file.size.toLong())
                        if (sizeLabel.isNotEmpty()) {
                            Text(
                                sizeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (index == 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "Keep",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        IconButton(onClick = { fileToDelete = file }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete copy ${index + 1}",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Thumbnail / icon preview ───────────────────────────────────────────────

@Composable
private fun FilePreviewThumbnail(
    thumbnailUrl: String?,
    category: FileCategory,
    modifier: Modifier = Modifier
) {
    var loadFailed by remember { mutableStateOf(false) }
    val showThumbnail = category == FileCategory.IMAGE && thumbnailUrl != null && !loadFailed

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(category.tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        if (showThumbnail) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { loadFailed = true }
            )
        } else {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = category.tint,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

// ── Category badge chip ────────────────────────────────────────────────────

@Composable
private fun CategoryBadge(category: FileCategory) {
    Surface(
        color = category.tint.copy(alpha = 0.12f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = category.tint,
                modifier = Modifier.size(10.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = category.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = category.tint
            )
        }
    }
}

// ── Delete confirmation dialog ─────────────────────────────────────────────

@Composable
private fun DeleteConfirmDialog(
    file: File,
    category: FileCategory,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(category.tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    tint = category.tint,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = { Text("Delete this copy?") },
        text = {
            Column {
                Text(
                    text = file.name ?: "Unknown File",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val sizeLabel = formatSize(file.size.toLong())
                if (sizeLabel.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = sizeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "This file will be moved to Trash in your Google Drive.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ── Previews ───────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
fun GoogleDriveScannerEmptyPreview() {
    MaterialTheme {
        GoogleDriveScannerContent(
            isScanning = false,
            duplicateGroups = emptyList(),
            errorMessage = null,
            onBack = {}, onRetry = {}, onConnect = {}, onDelete = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GoogleDriveScannerScanningPreview() {
    MaterialTheme {
        GoogleDriveScannerContent(
            isScanning = true,
            duplicateGroups = emptyList(),
            errorMessage = null,
            onBack = {}, onRetry = {}, onConnect = {}, onDelete = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GoogleDriveScannerResultsPreview() {
    fun mockFile(name: String, id: String, mime: String, checksum: String) = File().apply {
        this.name = name; this.id = id; this.mimeType = mime; this.md5Checksum = checksum
    }
    val groups = listOf(
        listOf(mockFile("Holiday.jpg", "1", "image/jpeg", "a1"), mockFile("Holiday.jpg", "2", "image/jpeg", "a1")),
        listOf(mockFile("Report.docx", "3", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "b1"),
               mockFile("Report.docx", "4", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "b1")),
        listOf(mockFile("Music.mp3", "5", "audio/mpeg", "c1"), mockFile("Music.mp3", "6", "audio/mpeg", "c1"))
    )
    MaterialTheme {
        GoogleDriveScannerContent(
            isScanning = false,
            duplicateGroups = groups,
            errorMessage = null,
            onBack = {}, onRetry = {}, onConnect = {}, onDelete = {}
        )
    }
}
