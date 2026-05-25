package com.rp.dedup.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.navigation.NavHostController
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.services.drive.model.File
import com.rp.dedup.BuildConfig
import com.rp.dedup.core.drive.GoogleDriveManager
import kotlinx.coroutines.launch

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

    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Re-trigger the connect logic or just clear error and let user click again
            errorMessage = "Permission granted. Please tap Connect again to scan."
        }else{
            Toast.makeText(context, "Error Signing into your drive", Toast.LENGTH_SHORT).show()
        }
    }

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    GoogleDriveScannerContent(
        isScanning = isScanning,
        duplicateGroups = duplicateGroups,
        errorMessage = errorMessage,
        onBack = { navController.popBackStack() },
        onRetry = { errorMessage = null },
        onConnect = {
            scope.launch {
                try {
                    val result = credentialManager.getCredential(context, request)
                    val credential = GoogleAccountCredential.usingOAuth2(context, GoogleDriveManager.SCOPES)
                    
                    // Extract email if possible to provide a better user experience
                    val googleIdTokenCredential = (result.credential as? CustomCredential)?.let { 
                        try {
                            com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.createFrom(it.data)
                        } catch (e: Exception) { null }
                    }
                    googleIdTokenCredential?.id?.let { email ->
                        credential.selectedAccountName = email
                    }

                    isScanning = true
                    duplicateGroups = driveManager.scanForDuplicates(credential)
                } catch (e: UserRecoverableAuthIOException) {
                    authLauncher.launch(e.intent)
                } catch (e: Exception) {
                    errorMessage = "Sign-in failed: ${e.message}"
                } finally {
                    isScanning = false
                }
            }
        },
        onDelete = {
            errorMessage = "Deletion logic requires persistent credential"
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (errorMessage != null) {
                Text(errorMessage, color = Color.Red, modifier = Modifier.padding(8.dp))
                Button(onClick = onRetry) {
                    Text("Retry")
                }
            } else if (duplicateGroups.isEmpty() && !isScanning) {
                EmptyDriveState(onConnect = onConnect)
            } else if (isScanning) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.padding(32.dp))
                        Text("Scanning your Drive for duplicates...")
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(duplicateGroups) { group ->
                        DuplicateGroupItem(group, onDelete = onDelete)
                    }
                }
            }
        }
    }
}

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
            color = Color.Gray,
            modifier = Modifier.padding(horizontal = 32.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onConnect,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Connect Google Drive")
        }
    }
}

@Composable
private fun DuplicateGroupItem(group: List<File>, onDelete: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = group.first().name ?: "Unknown File",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "\${group.size} copies found",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            group.forEachIndexed { index, file ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Copy \${index + 1}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            file.id ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                    if (index > 0) { // Keep the first one, offer deletion for others
                        IconButton(onClick = { onDelete(file.id) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        Text(
                            "KEEPING",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.DarkGray,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GoogleDriveScannerEmptyPreview() {
    MaterialTheme {
        GoogleDriveScannerContent(
            isScanning = false,
            duplicateGroups = emptyList(),
            errorMessage = null,
            onBack = {},
            onRetry = {},
            onConnect = {},
            onDelete = {}
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
            onBack = {},
            onRetry = {},
            onConnect = {},
            onDelete = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GoogleDriveScannerResultsPreview() {
    val mockFile = File().apply {
        name = "Holiday_Photo.jpg"
        id = "file_id_1"
    }
    val mockGroup = listOf(mockFile, mockFile.clone().apply { id = "file_id_2" })
    
    MaterialTheme {
        GoogleDriveScannerContent(
            isScanning = false,
            duplicateGroups = listOf(mockGroup),
            errorMessage = null,
            onBack = {},
            onRetry = {},
            onConnect = {},
            onDelete = {}
        )
    }
}
