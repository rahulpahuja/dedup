package com.rp.dedup.screens

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import com.rp.dedup.core.notifications.ToastManager
import com.rp.dedup.core.ui.DeDupTopBar
import com.rp.dedup.core.viewmodels.SettingsViewModel
import com.rp.dedup.core.viewmodels.ThemeViewModel
import com.rp.dedup.screens.settings.components.SettingsCard
import com.rp.dedup.screens.settings.components.SettingsRow
import com.rp.dedup.screens.settings.components.SettingsSectionHeader
import com.rp.dedup.screens.settings.components.SettingsSwitchRow
import com.rp.dedup.screens.settings.components.dialogs.*
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
    val analyticsManager = remember { AnalyticsManager.getInstance(context) }

    val themeViewModel: ThemeViewModel = viewModel(factory = ThemeViewModel.Factory(context))
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(DataStoreManager(context.applicationContext)))

    val currentThemeMode    by themeViewModel.themeMode.collectAsState()
    val currentPalette      by themeViewModel.appPalette.collectAsState()
    val similarityThreshold by settingsViewModel.similarityThreshold.collectAsState()
    val excludedFolders     by settingsViewModel.excludedFolders.collectAsState()
    val autoScanOnStartup   by settingsViewModel.autoScanOnStartup.collectAsState()
    val selectedLanguage    by settingsViewModel.selectedLanguage.collectAsState()
    val selectedCurrency    by settingsViewModel.selectedCurrency.collectAsState()

    LaunchedEffect(Unit) { analyticsManager.logScreenView("Settings") }

    var showThemeDialog           by rememberSaveable { mutableStateOf(false) }
    var showThresholdDialog       by rememberSaveable { mutableStateOf(false) }
    var showLanguageDialog        by rememberSaveable { mutableStateOf(false) }
    var showCurrencyDialog        by rememberSaveable { mutableStateOf(false) }
    var showExcludedFoldersDialog by rememberSaveable { mutableStateOf(false) }
    var showFeedbackDialog        by rememberSaveable { mutableStateOf(false) }
    var showFeatureRequestDialog  by rememberSaveable { mutableStateOf(false) }
    var showLogoutDialog          by rememberSaveable { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { settingsViewModel.addExcludedFolder(it.path ?: it.toString()) }
    }

    Scaffold(
        topBar = {
            DeDupTopBar(title = "DeDup", navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel))
                }
            })
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
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                color = MaterialTheme.colorScheme.onBackground
            )

            SettingsSectionHeader(stringResource(R.string.appearance_section))
            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.Palette, iconColor = UIConstants.ColorIconPalette,
                    title = stringResource(R.string.theme_setting),
                    trailing = { ThemeBadge(currentThemeMode, currentPalette) },
                    onClick = { showThemeDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsRow(
                    icon = Icons.Default.Language, iconColor = MaterialTheme.colorScheme.secondary,
                    title = stringResource(R.string.language_setting),
                    trailing = {
                        Text(LocaleManager.getSupportedLanguages().find { it.code == selectedLanguage }?.name ?: selectedLanguage,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    onClick = { showLanguageDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsRow(
                    icon = Icons.Default.CurrencyExchange, iconColor = UIConstants.ColorSavingsGreen,
                    title = "Storage Cost Currency",
                    trailing = {
                        val displayCode = selectedCurrency.ifEmpty {
                            try { Currency.getInstance(Locale.getDefault()).currencyCode } catch (_: Exception) { "USD" }
                        }
                        Text(displayCode, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    onClick = { showCurrencyDialog = true }
                )
            }

            Spacer(Modifier.height(8.dp))
            SettingsSectionHeader(stringResource(R.string.scanning_section))
            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.Tune, iconColor = MaterialTheme.colorScheme.primary,
                    title = stringResource(R.string.similarity_sensitivity),
                    trailing = {
                        Text(stringResource(R.string.bits, similarityThreshold),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    onClick = { showThresholdDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsRow(
                    icon = Icons.Default.FolderOff, iconColor = MaterialTheme.colorScheme.error,
                    title = stringResource(R.string.excluded_folders),
                    trailing = {
                        Text(stringResource(R.string.excluded_count, excludedFolders.size),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    onClick = { showExcludedFoldersDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsSwitchRow(
                    icon = Icons.Default.PlayArrow, iconColor = UIConstants.ColorSavingsGreen,
                    title = stringResource(R.string.auto_scan_on_startup),
                    checked = autoScanOnStartup,
                    onCheckedChange = { settingsViewModel.setAutoScanOnStartup(it) }
                )
            }

            SettingsSectionHeader(stringResource(R.string.support_feedback_section))
            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.ChatBubbleOutline, iconColor = MaterialTheme.colorScheme.primary,
                    title = stringResource(R.string.share_feedback),
                    onClick = { showFeedbackDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsRow(
                    icon = Icons.Default.AddCircleOutline, iconColor = MaterialTheme.colorScheme.secondary,
                    title = stringResource(R.string.request_feature),
                    onClick = { showFeatureRequestDialog = true }
                )
            }

            Spacer(Modifier.height(8.dp))
            SettingsSectionHeader(stringResource(R.string.about_section))
            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.Info, iconColor = UIConstants.ColorIconInfo,
                    title = stringResource(R.string.about_dedup),
                    trailing = {
                        Text(UIConstants.APP_VERSION, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    onClick = { navController.navigate(Screen.About.route) }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsRow(
                    icon = Icons.Default.Security, iconColor = MaterialTheme.colorScheme.primary,
                    title = stringResource(R.string.privacy_policy),
                    onClick = { analyticsManager.logPrivacyPolicyViewed(); navController.navigate(Screen.PrivacyPolicy.route) }
                )
            }

            Spacer(Modifier.height(8.dp))
            SettingsSectionHeader(stringResource(R.string.account_section))
            SettingsCard {
                SettingsRow(
                    icon = Icons.AutoMirrored.Filled.Logout, iconColor = MaterialTheme.colorScheme.error,
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
            icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.logout_confirm_title)) },
            text  = { Text(stringResource(R.string.logout_confirm_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        analyticsManager.logLogout()
                        scope.launch {
                            FirebaseAuthManager(toastManager).signOutWithCredentialClear(context)
                            navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.logout)) }
            },
            dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showThemeDialog) {
        ThemePickerDialog(
            currentMode = currentThemeMode, currentPalette = currentPalette,
            onDismiss = { showThemeDialog = false },
            onSelectMode = { analyticsManager.logSettingChanged("THEME", it.name); themeViewModel.setThemeMode(it) },
            onSelectPalette = { analyticsManager.logSettingChanged("THEME_PALETTE", it.name); themeViewModel.setPalette(it) }
        )
    }

    if (showThresholdDialog) {
        ThresholdPickerDialog(
            currentValue = similarityThreshold,
            onDismiss = { showThresholdDialog = false },
            onSelect = { analyticsManager.logSettingChanged("SIMILARITY_THRESHOLD", it.toString()); settingsViewModel.setSimilarityThreshold(it); showThresholdDialog = false }
        )
    }

    if (showLanguageDialog) {
        LanguagePickerDialog(
            currentCode = selectedLanguage,
            onDismiss = { showLanguageDialog = false },
            onSelect = { code ->
                scope.launch { analyticsManager.logSettingChanged("LANGUAGE", code); settingsViewModel.setLanguage(code); LocaleManager.applyLocale(code) }
                showLanguageDialog = false
            }
        )
    }

    if (showCurrencyDialog) {
        CurrencyPickerDialog(
            currentCode = selectedCurrency,
            onDismiss = { showCurrencyDialog = false },
            onSelect = { analyticsManager.logSettingChanged("CURRENCY", it); settingsViewModel.setCurrency(it); showCurrencyDialog = false }
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
            title = "Share Feedback", placeholder = "Tell us what you think…",
            onDismiss = { showFeedbackDialog = false },
            onSubmit = { content ->
                analyticsManager.logFeedbackSubmitted("FEEDBACK")
                scope.launch {
                    val result = dbManager.submitFeedback("FEEDBACK", content)
                    if (result.isSuccess) toastManager.showShort("Feedback submitted. Thank you!")
                    else toastManager.showLong("Failed to submit: ${result.exceptionOrNull()?.message}")
                }
                showFeedbackDialog = false
            }
        )
    }

    if (showFeatureRequestDialog) {
        FeedbackDialog(
            title = "Request a Feature", placeholder = "What would you like to see in DeDup?",
            onDismiss = { showFeatureRequestDialog = false },
            onSubmit = { content ->
                analyticsManager.logFeedbackSubmitted("FEATURE_REQUEST")
                analyticsManager.logFeatureRequested()
                scope.launch {
                    val result = dbManager.submitFeedback("FEATURE_REQUEST", content)
                    if (result.isSuccess) toastManager.showShort("Feature request submitted. Thank you!")
                    else toastManager.showLong("Failed to submit: ${result.exceptionOrNull()?.message}")
                }
                showFeatureRequestDialog = false
            }
        )
    }
}

@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
private fun SettingsScreenPreview() {
    DeDupTheme { SettingsScreen(navController = rememberNavController()) }
}
