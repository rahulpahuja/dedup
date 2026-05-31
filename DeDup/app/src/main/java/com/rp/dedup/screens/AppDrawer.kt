package com.rp.dedup.screens

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.rp.dedup.Screen
import com.rp.dedup.UIConstants
import com.rp.dedup.core.model.ThemeMode
import com.rp.dedup.core.viewmodels.ThemeViewModel
import com.rp.dedup.core.viewmodels.UserProfileViewModel
import com.rp.dedup.core.caching.DataStoreManager
import com.rp.dedup.core.firebase.auth.FirebaseAuthManager
import com.rp.dedup.core.notifications.ToastManager
import com.rp.dedup.ui.theme.DeDupTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun AppDrawerContent(
    navController: NavHostController,
    drawerState: DrawerState,
    scope: CoroutineScope,
    profileViewModel: UserProfileViewModel
) {
    // In Previews, we avoid instantiating ViewModels that require Application context
    if (LocalInspectionMode.current) {
        AppDrawerContentUI(
            navController = navController,
            drawerState = drawerState,
            scope = scope,
            userName = "User",
            userEmail = "user@example.com",
            userImageUrl = "",
            currentThemeMode = ThemeMode.AUTO,
            onProfileUpdate = { _, _ -> },
            onThemeModeChange = {}
        )
        return
    }

    val context = LocalContext.current
    
    // ThemeViewModel requires DataStoreManager, so we use a factory
    val themeViewModel: ThemeViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ThemeViewModel(DataStoreManager(context.applicationContext)) as T
            }
        }
    )

    val currentThemeMode by themeViewModel.themeMode.collectAsState()

    AppDrawerContentUI(
        navController = navController,
        drawerState = drawerState,
        scope = scope,
        userName = profileViewModel.name,
        userEmail = profileViewModel.email,
        userImageUrl = profileViewModel.profileImageUrl,
        currentThemeMode = currentThemeMode,
        onProfileUpdate = { name, email -> profileViewModel.update(name, email) },
        onThemeModeChange = { themeViewModel.setThemeMode(it) }
    )
}

@Composable
private fun AppDrawerContentUI(
    navController: NavHostController,
    drawerState: DrawerState,
    scope: CoroutineScope,
    userName: String,
    userEmail: String,
    userImageUrl: String,
    currentThemeMode: ThemeMode,
    onProfileUpdate: (String, String) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    var showEditDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            launchSingleTop = true
        }
        scope.launch { drawerState.close() }
    }

    ModalDrawerSheet {
        ProfileHeader(
            name = userName,
            email = userEmail,
            imageUrl = userImageUrl,
            onEditClick = { showEditDialog = true }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Spacer(Modifier.height(4.dp))

        DrawerNavItem(
            icon = Icons.Default.GridView,
            label = UIConstants.getScreenName(UIConstants.ROUTE_DASHBOARD),
            selected = currentRoute == Screen.Dashboard.route,
            onClick = { navigateTo(Screen.Dashboard.route) }
        )
        DrawerNavItem(
            icon = Icons.Default.Search,
            label = "Scan Files",
            selected = currentRoute == Screen.Cleanup.route,
            onClick = { navigateTo(Screen.Cleanup.route) }
        )
        DrawerNavItem(
            icon = Icons.AutoMirrored.Filled.List,
            label = UIConstants.getScreenName(UIConstants.ROUTE_IMAGE_SCANNER),
            selected = currentRoute == Screen.ImageScanner.route
                    || currentRoute == Screen.ResultsMedia.route,
            onClick = { navigateTo(Screen.ImageScanner.route) }
        )
        DrawerNavItem(
            icon = Icons.Default.Videocam,
            label = UIConstants.getScreenName(UIConstants.ROUTE_VIDEO_SCANNER),
            selected = currentRoute == Screen.VideoScanner.route,
            onClick = { navigateTo(Screen.VideoScanner.route) }
        )
        DrawerNavItem(
            icon = Icons.Default.History,
            label = UIConstants.getScreenName(UIConstants.ROUTE_ACTIVITY),
            selected = currentRoute == Screen.Activity.route,
            onClick = { navigateTo(Screen.Activity.route) }
        )
        DrawerNavItem(
            icon = Icons.Default.ManageSearch,
            label = UIConstants.getScreenName(UIConstants.ROUTE_SCAN_HISTORY),
            selected = currentRoute == Screen.ScanHistory.route,
            onClick = { navigateTo(Screen.ScanHistory.route) }
        )
        DrawerNavItem(
            icon = Icons.Default.FolderOpen,
            label = UIConstants.getScreenName(UIConstants.ROUTE_FILE_BROWSER),
            selected = currentRoute == Screen.FileBrowser.route,
            onClick = { navigateTo(Screen.FileBrowser.route) }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        ThemeSection(
            currentMode = currentThemeMode,
            onModeChange = onThemeModeChange
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        DrawerNavItem(
            icon = Icons.Default.Settings,
            label = UIConstants.getScreenName(UIConstants.ROUTE_SETTINGS),
            selected = currentRoute == Screen.Settings.route,
            onClick = { navigateTo(Screen.Settings.route) }
        )
        DrawerNavItem(
            icon = Icons.Default.Info,
            label = UIConstants.getScreenName(UIConstants.ROUTE_ABOUT),
            selected = currentRoute == Screen.About.route,
            onClick = { navigateTo(Screen.About.route) }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        DrawerNavItem(
            icon = Icons.Default.Logout,
            label = "Logout",
            selected = false,
            onClick = { showLogoutDialog = true }
        )
    }

    if (showLogoutDialog) {
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = {
                Icon(
                    Icons.Default.Logout,
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
                        scope.launch {
                            val toastManager = ToastManager(context)
                            val authManager = FirebaseAuthManager(toastManager)
                            authManager.signOutWithCredentialClear(context)
                            navController.navigate(Screen.Login.route) {
                                popUpTo(0) { inclusive = true }
                            }
                            drawerState.close()
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

    if (showEditDialog) {
        ProfileEditDialog(
            currentName = userName,
            currentEmail = userEmail,
            onDismiss = { showEditDialog = false },
            onSave = { name, email ->
                onProfileUpdate(name, email)
                showEditDialog = false
            }
        )
    }
}

@Composable
private fun ProfileHeader(
    name: String,
    email: String,
    imageUrl: String,
    onEditClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 24.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(52.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "Profile Picture",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            if (email.isNotEmpty()) {
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onEditClick) {
            Icon(
                Icons.Default.Edit,
                contentDescription = "Edit profile",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DrawerNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 1.dp)
    )
}

@Composable
private fun ThemeSection(
    currentMode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit
) {
    Text(
        "THEME",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, top = 8.dp, bottom = 4.dp)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ThemeMode.entries.forEach { mode ->
            FilterChip(
                selected = currentMode == mode,
                onClick = { onModeChange(mode) },
                label = {
                    Text(
                        mode.name.lowercase().replaceFirstChar { it.uppercaseChar() },
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ProfileEditDialog(
    currentName: String,
    currentEmail: String,
    onDismiss: () -> Unit,
    onSave: (name: String, email: String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var email by remember { mutableStateOf(currentEmail) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, email) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
private fun AppDrawerContentPreview() {
    DeDupTheme {
        AppDrawerContentUI(
            navController = rememberNavController(),
            drawerState = rememberDrawerState(initialValue = DrawerValue.Open),
            scope = rememberCoroutineScope(),
            userName = "John Doe",
            userEmail = "john@example.com",
            userImageUrl = "",
            currentThemeMode = ThemeMode.AUTO,
            onProfileUpdate = { _, _ -> },
            onThemeModeChange = {}
        )
    }
}
