package com.rp.dedup.core

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
fun PermissionRequester(
    onPermissionGranted: () -> Unit,
    tiramisu13Permission: String = Manifest.permission.READ_MEDIA_IMAGES
) {
    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        tiramisu13Permission
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            onPermissionGranted()
        } else {
            // Handle permission denial (e.g., show a Toast)
        }
    }

    // Trigger the prompt when this composable enters the composition
    LaunchedEffect(Unit) {
        launcher.launch(permissionToRequest)
    }
}