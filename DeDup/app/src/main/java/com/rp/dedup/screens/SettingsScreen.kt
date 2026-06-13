package com.rp.dedup.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.rp.dedup.R
import com.rp.dedup.Screen
import com.rp.dedup.UIConstants
import com.rp.dedup.core.analytics.AnalyticsManager
import com.rp.dedup.core.caching.DataStoreManager
import com.rp.dedup.core.firebase.auth.FirebaseAuthManager
import com.rp.dedup.core.firebase.db.FirebaseDbManager
import com.rp.dedup.core.i18n.LocaleManager
import androidx.compose.ui.draw.clip
import com.rp.dedup.core.model.AppPalette
import com.rp.dedup.core.model.ThemeMode
import com.rp.dedup.core.notifications.ToastManager
import com.rp.dedup.core.ui.DeDupTopBar
import com.rp.dedup.core.viewmodels.ThemeViewModel
import com.rp.dedup.ui.theme.DeDupTheme
import kotlinx.coroutines.launch
import java.util.Currency
import java.util.Locale

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
    val currentPalette by themeViewModel.appPalette.collectAsState()
    val similarityThreshold by settingsViewModel.similarityThreshold.collectAsState()
    val excludedFolders by settingsViewModel.excludedFolders.collectAsState()
    val autoScanOnStartup by settingsViewModel.autoScanOnStartup.collectAsState()
    val selectedLanguage by settingsViewModel.selectedLanguage.collectAsState()
    val selectedCurrency by settingsViewModel.selectedCurrency.collectAsState()

    LaunchedEffect(Unit) {
        analyticsManager.logScreenView("Settings")
    }
    
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }
    var showThresholdDialog by rememberSaveable { mutableStateOf(false) }
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var showCurrencyDialog by rememberSaveable { mutableStateOf(false) }
    var showExcludedFoldersDialog by rememberSaveable { mutableStateOf(false) }
    var showFeedbackDialog by rememberSaveable { mutableStateOf(false) }
    var showFeatureRequestDialog by rememberSaveable { mutableStateOf(false) }
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val path = it.path ?: it.toString()
            settingsViewModel.addExcludedFolder(path)
        }
    }

    Scaffold(
        topBar = {
            DeDupTopBar(
                title = stringResource(R.string.settings_title),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel))
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

            SettingsSectionHeader(stringResource(R.string.appearance_section))

            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.Palette,
                    iconColor = UIConstants.ColorIconPalette,
                    title = stringResource(R.string.theme_setting),
                    trailing = { ThemeBadge(currentThemeMode, currentPalette) },
                    onClick = { showThemeDialog = true }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsRow(
                    icon = Icons.Default.Language,
                    iconColor = MaterialTheme.colorScheme.secondary,
                    title = stringResource(R.string.language_setting),
                    trailing = {
                        Text(
                            text = LocaleManager.getSupportedLanguages()
                                .find { it.code == selectedLanguage }?.name ?: selectedLanguage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = { showLanguageDialog = true }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsRow(
                    icon = Icons.Default.CurrencyExchange,
                    iconColor = UIConstants.ColorSavingsGreen,
                    title = "Storage Cost Currency",
                    trailing = {
                        val displayCode = selectedCurrency.ifEmpty {
                            try { Currency.getInstance(Locale.getDefault()).currencyCode }
                            catch (_: Exception) { "USD" }
                        }
                        Text(
                            text = displayCode,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = { showCurrencyDialog = true }
                )
            }

            Spacer(Modifier.height(8.dp))

            SettingsSectionHeader(stringResource(R.string.scanning_section))

            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.Tune,
                    iconColor = MaterialTheme.colorScheme.primary,
                    title = stringResource(R.string.similarity_sensitivity),
                    trailing = {
                        Text(
                            stringResource(R.string.bits, similarityThreshold),
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
                    title = stringResource(R.string.excluded_folders),
                    trailing = {
                        Text(
                            stringResource(R.string.excluded_count, excludedFolders.size),
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
                    title = stringResource(R.string.auto_scan_on_startup),
                    checked = autoScanOnStartup,
                    onCheckedChange = { settingsViewModel.setAutoScanOnStartup(it) }
                )
            }

            SettingsSectionHeader(stringResource(R.string.support_feedback_section))

            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.ChatBubbleOutline,
                    iconColor = MaterialTheme.colorScheme.primary,
                    title = stringResource(R.string.share_feedback),
                    onClick = { showFeedbackDialog = true }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsRow(
                    icon = Icons.Default.AddCircleOutline,
                    iconColor = MaterialTheme.colorScheme.secondary,
                    title = stringResource(R.string.request_feature),
                    onClick = { showFeatureRequestDialog = true }
                )
            }

            Spacer(Modifier.height(8.dp))

            SettingsSectionHeader(stringResource(R.string.about_section))

            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.Info,
                    iconColor = UIConstants.ColorIconInfo,
                    title = stringResource(R.string.about_dedup),
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
                    title = stringResource(R.string.privacy_policy),
                    onClick = { 
                        analyticsManager.logPrivacyPolicyViewed()
                        navController.navigate(Screen.PrivacyPolicy.route) 
                    }
                )
            }

            Spacer(Modifier.height(8.dp))

            SettingsSectionHeader(stringResource(R.string.account_section))

            SettingsCard {
                SettingsRow(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    iconColor = MaterialTheme.colorScheme.error,
                    title = stringResource(R.string.logout),
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
            title = { Text(stringResource(R.string.logout_confirm_title)) },
            text = { Text(stringResource(R.string.logout_confirm_message)) },
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
                    Text(stringResource(R.string.logout))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showThemeDialog) {
        ThemePickerDialog(
            currentMode    = currentThemeMode,
            currentPalette = currentPalette,
            onDismiss      = { showThemeDialog = false },
            onSelectMode   = { mode ->
                analyticsManager.logSettingChanged("THEME", mode.name)
                themeViewModel.setThemeMode(mode)
            },
            onSelectPalette = { palette ->
                analyticsManager.logSettingChanged("THEME_PALETTE", palette.name)
                themeViewModel.setPalette(palette)
            }
        )
    }

    if (showThresholdDialog) {
        ThresholdPickerDialog(
            currentValue = similarityThreshold,
            onDismiss = { showThresholdDialog = false },
            onSelect = { value ->
                analyticsManager.logSettingChanged("SIMILARITY_THRESHOLD", value.toString())
                settingsViewModel.setSimilarityThreshold(value)
                showThresholdDialog = false
            }
        )
    }

    if (showLanguageDialog) {
        LanguagePickerDialog(
            currentCode = selectedLanguage,
            onDismiss = { showLanguageDialog = false },
            onSelect = { code ->
                scope.launch {
                    analyticsManager.logSettingChanged("LANGUAGE", code)
                    settingsViewModel.setLanguage(code)
                    LocaleManager.applyLocale(code)
                }
                showLanguageDialog = false
            }
        )
    }

    if (showCurrencyDialog) {
        CurrencyPickerDialog(
            currentCode = selectedCurrency,
            onDismiss = { showCurrencyDialog = false },
            onSelect = { code ->
                analyticsManager.logSettingChanged("CURRENCY", code)
                settingsViewModel.setCurrency(code)
                showCurrencyDialog = false
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
                        stringResource(R.string.excluded_folders),
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
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
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
                    text = stringResource(R.string.bits, sliderValue.toInt()),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    style = MaterialTheme.typography.labelLarge
                )

                Spacer(Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Button(onClick = { onSelect(sliderValue.toInt()) }) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun LanguagePickerDialog(
    currentCode: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val languages = LocaleManager.getSupportedLanguages()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    stringResource(R.string.choose_language),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )

                Spacer(Modifier.height(16.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    languages.forEach { language ->
                        val selected = currentCode == language.code
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(language.code) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else Color.Transparent,
                            border = BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = language.name,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
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
                }

                Spacer(Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
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

// ── Palette metadata (UI layer — colours intentionally hardcoded here) ────────
private data class PaletteOption(
    val palette:   AppPalette,
    val name:      String,
    val primary:   Color,
    val secondary: Color,
    val accent:    Color
)

private val PALETTE_OPTIONS = listOf(
    PaletteOption(AppPalette.OCEAN,      "Ocean",    Color(0xFF0056D2), Color(0xFF00838F), Color(0xFF80DEEA)),
    PaletteOption(AppPalette.MIDNIGHT,   "Midnight", Color(0xFF7C4DFF), Color(0xFF9C8FFF), Color(0xFFCF6679)),
    PaletteOption(AppPalette.FOREST,     "Forest",   Color(0xFF2E7D32), Color(0xFF388E3C), Color(0xFF81C784)),
    PaletteOption(AppPalette.SUNSET,     "Sunset",   Color(0xFFE65100), Color(0xFFEF6C00), Color(0xFFFF8F00)),
    PaletteOption(AppPalette.ROSE,       "Rose",     Color(0xFFAD1457), Color(0xFFC2185B), Color(0xFFFF80AB)),
    PaletteOption(AppPalette.MONOCHROME, "Mono",     Color(0xFF424242), Color(0xFF616161), Color(0xFF9E9E9E)),
)

@Composable
private fun ThemeBadge(mode: ThemeMode, palette: AppPalette) {
    val opt = PALETTE_OPTIONS.find { it.palette == palette } ?: PALETTE_OPTIONS.first()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(opt.primary)
        )
        Spacer(Modifier.width(6.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = opt.primary.copy(alpha = 0.15f)
        ) {
            Text(
                text = opt.name,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelMedium.copy(
                    color      = opt.primary,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

@Composable
private fun ThemePickerDialog(
    currentMode:     ThemeMode,
    currentPalette:  AppPalette,
    onDismiss:       () -> Unit,
    onSelectMode:    (ThemeMode) -> Unit,
    onSelectPalette: (AppPalette) -> Unit
) {
    val modeOptions = listOf(
        Triple(ThemeMode.LIGHT, Icons.Default.LightMode,          "Light"),
        Triple(ThemeMode.DARK,  Icons.Default.DarkMode,           "Dark"),
        Triple(ThemeMode.AUTO,  Icons.Default.SettingsBrightness, "Auto")
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape          = RoundedCornerShape(28.dp),
            color          = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border         = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {

                // ── Header ──────────────────────────────────────────────────
                Text(
                    "Appearance",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Personalize how DeDup looks and feels",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(24.dp))

                // ── Brightness mode ──────────────────────────────────────────
                Text(
                    "MODE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color         = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Spacer(Modifier.height(10.dp))

                Surface(
                    shape    = RoundedCornerShape(14.dp),
                    border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                        modeOptions.forEachIndexed { index, (mode, icon, label) ->
                            val selected = currentMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else Color.Transparent
                                    )
                                    .clickable { onSelectMode(mode) }
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        icon,
                                        contentDescription = label,
                                        tint = if (selected) Color.White
                                               else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selected) Color.White
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }
                            if (index < modeOptions.lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .fillMaxHeight()
                                        .background(MaterialTheme.colorScheme.outlineVariant)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ── Color palette grid ───────────────────────────────────────
                Text(
                    "COLOR PALETTE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color         = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Spacer(Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(PALETTE_OPTIONS.take(3), PALETTE_OPTIONS.drop(3)).forEach { row ->
                        Row(
                            modifier             = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            row.forEach { opt ->
                                PaletteSwatchCard(
                                    option   = opt,
                                    selected = currentPalette == opt.palette,
                                    onClick  = { onSelectPalette(opt.palette) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            "Done",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PaletteSwatchCard(
    option:   PaletteOption,
    selected: Boolean,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .aspectRatio(0.88f)
            .clickable(onClick = onClick),
        shape  = RoundedCornerShape(16.dp),
        color  = if (selected) option.primary.copy(alpha = 0.10f)
                 else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) option.primary
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier             = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment  = Alignment.CenterHorizontally,
            verticalArrangement  = Arrangement.Center
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(option.primary, option.secondary, option.accent).forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                option.name,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color      = if (selected) option.primary
                                 else MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            if (selected) {
                Spacer(Modifier.height(4.dp))
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint               = option.primary,
                    modifier           = Modifier.size(14.dp)
                )
            }
        }
    }
}

private data class CurrencyInfo(val code: String, val name: String, val flag: String)

private val ALL_CURRENCIES = listOf(
    // Americas
    CurrencyInfo("USD", "US Dollar",          "🇺🇸"),
    CurrencyInfo("CAD", "Canadian Dollar",    "🇨🇦"),
    CurrencyInfo("MXN", "Mexican Peso",       "🇲🇽"),
    CurrencyInfo("BRL", "Brazilian Real",     "🇧🇷"),
    CurrencyInfo("ARS", "Argentine Peso",     "🇦🇷"),
    CurrencyInfo("CLP", "Chilean Peso",       "🇨🇱"),
    CurrencyInfo("COP", "Colombian Peso",     "🇨🇴"),
    CurrencyInfo("PEN", "Peruvian Sol",       "🇵🇪"),
    // Europe
    CurrencyInfo("EUR", "Euro",               "🇪🇺"),
    CurrencyInfo("GBP", "British Pound",      "🇬🇧"),
    CurrencyInfo("CHF", "Swiss Franc",        "🇨🇭"),
    CurrencyInfo("SEK", "Swedish Krona",      "🇸🇪"),
    CurrencyInfo("NOK", "Norwegian Krone",    "🇳🇴"),
    CurrencyInfo("DKK", "Danish Krone",       "🇩🇰"),
    CurrencyInfo("PLN", "Polish Złoty",       "🇵🇱"),
    CurrencyInfo("CZK", "Czech Koruna",       "🇨🇿"),
    CurrencyInfo("HUF", "Hungarian Forint",   "🇭🇺"),
    CurrencyInfo("RON", "Romanian Leu",       "🇷🇴"),
    CurrencyInfo("BGN", "Bulgarian Lev",      "🇧🇬"),
    CurrencyInfo("TRY", "Turkish Lira",       "🇹🇷"),
    CurrencyInfo("RUB", "Russian Ruble",      "🇷🇺"),
    CurrencyInfo("UAH", "Ukrainian Hryvnia",  "🇺🇦"),
    // Asia-Pacific
    CurrencyInfo("INR", "Indian Rupee",       "🇮🇳"),
    CurrencyInfo("JPY", "Japanese Yen",       "🇯🇵"),
    CurrencyInfo("CNY", "Chinese Yuan",       "🇨🇳"),
    CurrencyInfo("KRW", "South Korean Won",   "🇰🇷"),
    CurrencyInfo("AUD", "Australian Dollar",  "🇦🇺"),
    CurrencyInfo("NZD", "New Zealand Dollar", "🇳🇿"),
    CurrencyInfo("SGD", "Singapore Dollar",   "🇸🇬"),
    CurrencyInfo("HKD", "Hong Kong Dollar",   "🇭🇰"),
    CurrencyInfo("TWD", "Taiwan Dollar",      "🇹🇼"),
    CurrencyInfo("MYR", "Malaysian Ringgit",  "🇲🇾"),
    CurrencyInfo("THB", "Thai Baht",          "🇹🇭"),
    CurrencyInfo("IDR", "Indonesian Rupiah",  "🇮🇩"),
    CurrencyInfo("PHP", "Philippine Peso",    "🇵🇭"),
    CurrencyInfo("VND", "Vietnamese Dong",    "🇻🇳"),
    CurrencyInfo("PKR", "Pakistani Rupee",    "🇵🇰"),
    CurrencyInfo("BDT", "Bangladeshi Taka",   "🇧🇩"),
    // Middle East & Africa
    CurrencyInfo("SAR", "Saudi Riyal",        "🇸🇦"),
    CurrencyInfo("AED", "UAE Dirham",         "🇦🇪"),
    CurrencyInfo("ILS", "Israeli Shekel",     "🇮🇱"),
    CurrencyInfo("EGP", "Egyptian Pound",     "🇪🇬"),
    CurrencyInfo("NGN", "Nigerian Naira",     "🇳🇬"),
    CurrencyInfo("KES", "Kenyan Shilling",    "🇰🇪"),
    CurrencyInfo("ZAR", "South African Rand", "🇿🇦"),
)

@Composable
private fun CurrencyPickerDialog(
    currentCode: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(query) {
        if (query.isBlank()) ALL_CURRENCIES
        else ALL_CURRENCIES.filter {
            it.code.contains(query, ignoreCase = true) ||
            it.name.contains(query, ignoreCase = true)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "Storage Cost Currency",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    "Sets the currency used in the savings calculator on the dashboard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search currency…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // "Use device locale" option
                    val useLocale = currentCode.isEmpty()
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect("") },
                        shape = RoundedCornerShape(12.dp),
                        color = if (useLocale) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else Color.Transparent,
                        border = BorderStroke(
                            1.dp,
                            if (useLocale) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🌐", fontSize = 20.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Auto (device locale)",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (useLocale) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                                Text(
                                    try { Currency.getInstance(Locale.getDefault()).currencyCode }
                                    catch (_: Exception) { "USD" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (useLocale) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    filtered.forEach { info ->
                        val selected = currentCode == info.code
                        val symbol = try { Currency.getInstance(info.code).symbol } catch (_: Exception) { info.code }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(info.code) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    else Color.Transparent,
                            border = BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(info.flag, fontSize = 20.sp)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        info.name,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    )
                                    Text(
                                        "${info.code} · $symbol",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (selected) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.cancel))
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
