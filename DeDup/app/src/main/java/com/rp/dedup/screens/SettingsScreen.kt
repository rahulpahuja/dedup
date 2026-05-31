package com.rp.dedup.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.rp.dedup.Screen
import com.rp.dedup.UIConstants
import com.rp.dedup.core.analytics.AnalyticsManager
import com.rp.dedup.core.caching.DataStoreManager
import com.rp.dedup.core.model.ThemeMode
import com.rp.dedup.core.firebase.auth.FirebaseAuthManager
import com.rp.dedup.core.firebase.db.FirebaseDbManager
import com.rp.dedup.core.notifications.ToastManager
import com.rp.dedup.core.viewmodels.ThemeViewModel
import com.rp.dedup.ui.theme.DeDupTheme
import kotlinx.coroutines.launch
import java.util.Date
import android.provider.DocumentsContract
import android.net.Uri
import android.os.Environment
import com.rp.dedup.core.ui.DeDupTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toastManager = remember { ToastManager(context) }
    val dbManager = remember { FirebaseDbManager() }
    val analyticsManager = remember { AnalyticsManager(context) }

    val themeViewModel: ThemeViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ThemeViewModel(DataStoreManager(context.applicationContext)) as T
            }
        }
    )

    val settingsViewModel: com.rp.dedup.core.viewmodels.SettingsViewModel = viewModel(
        factory = com.rp.dedup.core.viewmodels.SettingsViewModel.Factory(DataStoreManager(context.applicationContext))
    )

    val currentThemeMode by themeViewModel.themeMode.collectAsState()
    val similarityThreshold by settingsViewModel.similarityThreshold.collectAsState()
    val excludedFolders by settingsViewModel.excludedFolders.collectAsState()
    val autoScanOnStartup by settingsViewModel.autoScanOnStartup.collectAsState()
    
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }
    var showThresholdDialog by rememberSaveable { mutableStateOf(false) }
    var showExcludedFoldersDialog by rememberSaveable { mutableStateOf(false) }
    var showFeedbackDialog by rememberSaveable { mutableStateOf(false) }
    var showFeatureRequestDialog by rememberSaveable { mutableStateOf(false) }
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            // Convert URI to path if possible, or just use URI string as unique identifier
            // For MediaStore exclusion, we usually need the absolute path or a unique prefix
            val path = it.path ?: it.toString()
            settingsViewModel.addExcludedFolder(path)
        }
    }

    Scaffold(
        topBar = {
            DeDupTopBar(
                title = "Settings",
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
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            SettingsSectionHeader("Appearance")

            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.Palette,
                    iconColor = UIConstants.ColorIconPalette,
                    title = "Theme",
                    trailing = { ThemeBadge(currentThemeMode) },
                    onClick = { showThemeDialog = true }
                )
            }

            Spacer(Modifier.height(8.dp))

            SettingsSectionHeader("Scanning")

            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.Tune,
                    iconColor = MaterialTheme.colorScheme.primary,
                    title = "Similarity Sensitivity",
                    trailing = {
                        Text(
                            "$similarityThreshold bits",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = { showThresholdDialog = true }
                )
                
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                
                SettingsRow(
                    icon = Icons.Default.FolderOff,
                    iconColor = MaterialTheme.colorScheme.error,
                    title = "Excluded Folders",
                    trailing = {
                        Text(
                            "${excludedFolders.size} excluded",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = { showExcludedFoldersDialog = true }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsSwitchRow(
                    icon = Icons.Default.PlayArrow,
                    iconColor = UIConstants.ColorSavingsGreen,
                    title = "Auto Scan on Startup",
                    checked = autoScanOnStartup,
                    onCheckedChange = { settingsViewModel.setAutoScanOnStartup(it) }
                )
            }

            SettingsSectionHeader("Support & Feedback")

            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.ChatBubbleOutline,
                    iconColor = MaterialTheme.colorScheme.primary,
                    title = "Share Feedback",
                    onClick = { showFeedbackDialog = true }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsRow(
                    icon = Icons.Default.AddCircleOutline,
                    iconColor = MaterialTheme.colorScheme.secondary,
                    title = "Request a Feature",
                    onClick = { showFeatureRequestDialog = true }
                )
            }

            Spacer(Modifier.height(8.dp))

            SettingsSectionHeader("About")

            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.Info,
                    iconColor = UIConstants.ColorIconInfo,
                    title = "About DeDup",
                    trailing = {
                        Text(
                            UIConstants.APP_VERSION,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = { navController.navigate(Screen.About.route) }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsRow(
                    icon = Icons.Default.Security,
                    iconColor = MaterialTheme.colorScheme.primary,
                    title = "Privacy Policy",
                    onClick = { 
                        analyticsManager.logPrivacyPolicyViewed()
                        navController.navigate(Screen.PrivacyPolicy.route) 
                    }
                )
            }

            Spacer(Modifier.height(8.dp))

            SettingsSectionHeader("Account")

            SettingsCard {
                SettingsRow(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    iconColor = MaterialTheme.colorScheme.error,
                    title = "Logout",
                    onClick = { showLogoutDialog = true }
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = {
                Icon(
                    Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Log out?") },
            text = { Text("You'll need to sign in again to access your account.") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        analyticsManager.logLogout()
                        scope.launch {
                            val authManager = FirebaseAuthManager(toastManager)
                            authManager.signOutWithCredentialClear(context)
                            navController.navigate(Screen.Login.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Log out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showThemeDialog) {
        ThemePickerDialog(
            currentMode = currentThemeMode,
            onDismiss = { showThemeDialog = false },
            onSelect = { mode ->
                themeViewModel.setThemeMode(mode)
                showThemeDialog = false
            }
        )
    }

    if (showThresholdDialog) {
        ThresholdPickerDialog(
            currentValue = similarityThreshold,
            onDismiss = { showThresholdDialog = false },
            onSelect = { value ->
                settingsViewModel.setSimilarityThreshold(value)
                showThresholdDialog = false
            }
        )
    }

    if (showExcludedFoldersDialog) {
        ExcludedFoldersDialog(
            folders = excludedFolders,
            onDismiss = { showExcludedFoldersDialog = false },
            onAdd = { folderPickerLauncher.launch(null) },
            onRemove = { settingsViewModel.removeExcludedFolder(it) }
        )
    }

    if (showFeedbackDialog) {
        FeedbackDialog(
            title = "Share Feedback",
            placeholder = "Tell us what you think...",
            onDismiss = { showFeedbackDialog = false },
            onSubmit = { content ->
                analyticsManager.logFeedbackSubmitted("FEEDBACK")
                scope.launch {
                    val result = dbManager.submitFeedback("FEEDBACK", content)
                    if (result.isSuccess) {
                        toastManager.showShort("Feedback submitted. Thank you!")
                    } else {
                        toastManager.showLong("Failed to submit: ${result.exceptionOrNull()?.message}")
                    }
                }
                showFeedbackDialog = false
            }
        )
    }

    if (showFeatureRequestDialog) {
        FeedbackDialog(
            title = "Request a Feature",
            placeholder = "What would you like to see in DeDup?",
            onDismiss = { showFeatureRequestDialog = false },
            onSubmit = { content ->
                analyticsManager.logFeedbackSubmitted("FEATURE_REQUEST")
                analyticsManager.logFeatureRequested()
                scope.launch {
                    val result = dbManager.submitFeedback("FEATURE_REQUEST", content)
                    if (result.isSuccess) {
                        toastManager.showShort("Feature request submitted. Thank you!")
                    } else {
                        toastManager.showLong("Failed to submit: ${result.exceptionOrNull()?.message}")
                    }
                }
                showFeatureRequestDialog = false
            }
        )
    }
}

@Composable
private fun ExcludedFoldersDialog(
    folders: List<String>,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Excluded Folders",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = onAdd) {
                        Icon(Icons.Default.Add, contentDescription = "Add Folder")
                    }
                }

                Text(
                    "Files in these folders will be skipped during scanning.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

                if (folders.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No folders excluded",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        folders.forEach { path ->
                            ExcludedFolderItem(path = path, onRemove = { onRemove(path) })
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun ExcludedFolderItem(path: String, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = path,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun FeedbackDialog(
    title: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var text by rememberSaveable { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(placeholder) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    trailingIcon = {
                        IconButton(onClick = { /* TODO: Trigger Speech-to-Text */ }) {
                            Icon(Icons.Default.Mic, contentDescription = "Voice Input")
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = { onSubmit(text) },
                        enabled = text.isNotBlank()
                    ) {
                        Text("Submit")
                    }
                }
            }
        }
    }
}

@Composable
private fun ThresholdPickerDialog(
    currentValue: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "Image Similarity",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    "Lower bits mean more strict (only very similar images). Higher bits mean more relaxed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(20.dp))

                var sliderValue by remember { mutableStateOf(currentValue.toFloat()) }
                
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = 0f..20f,
                    steps = 19
                )
                
                Text(
                    text = "${sliderValue.toInt()} bits difference allowed",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    style = MaterialTheme.typography.labelLarge
                )

                Spacer(Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { onSelect(sliderValue.toInt()) }) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    iconColor: androidx.compose.ui.graphics.Color,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = iconColor.copy(alpha = 0.15f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface
        )

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconColor: androidx.compose.ui.graphics.Color,
    title: String,
    trailing: @Composable () -> Unit = {},
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = iconColor.copy(alpha = 0.15f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface
        )

        trailing()

        Spacer(Modifier.width(10.dp))

        Icon(
            Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun ThemeBadge(mode: ThemeMode) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    ) {
        Text(
            text = mode.name.lowercase().replaceFirstChar { it.uppercaseChar() },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium.copy(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun ThemePickerDialog(
    currentMode: ThemeMode,
    onDismiss: () -> Unit,
    onSelect: (ThemeMode) -> Unit
) {
    val themeOptions = listOf(
        Triple(ThemeMode.LIGHT, Icons.Default.LightMode,          UIConstants.ColorThemeLight),
        Triple(ThemeMode.DARK,  Icons.Default.DarkMode,           UIConstants.ColorThemeDark),
        Triple(ThemeMode.AUTO,  Icons.Default.SettingsBrightness, UIConstants.ColorThemeAuto)
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "Choose Theme",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Controls the app's color scheme",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(20.dp))

                themeOptions.forEach { (mode, icon, tint) ->
                    val selected = currentMode == mode
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onSelect(mode) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = tint.copy(alpha = 0.18f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = mode.name.lowercase().replaceFirstChar { it.uppercaseChar() },
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                color = if (selected) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (selected) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        "Cancel",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
private fun SettingsScreenPreview() {
    DeDupTheme {
        SettingsScreen(navController = rememberNavController())
    }
}
