package com.rp.dedup.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.rp.dedup.Screen
import com.rp.dedup.ThemeMode
import com.rp.dedup.ThemeViewModel
import com.rp.dedup.UserProfileViewModel
import com.rp.dedup.core.caching.DataStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun AppDrawerContent(
    navController: NavHostController,
    drawerState: DrawerState,
    scope: CoroutineScope
) {
    val context = LocalContext.current
    val profileViewModel: UserProfileViewModel = viewModel()
    
    // ThemeViewModel requires DataStoreManager, so we use a factory
    val themeViewModel: ThemeViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ThemeViewModel(DataStoreManager(context.applicationContext)) as T
            }
        }
    )
    
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    var showEditDialog by remember { mutableStateOf(false) }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            launchSingleTop = true
        }
        scope.launch { drawerState.close() }
    }

    ModalDrawerSheet {
        ProfileHeader(
            name = profileViewModel.name,
            email = profileViewModel.email,
            onEditClick = { showEditDialog = true }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Spacer(Modifier.height(4.dp))

        DrawerNavItem(
            icon = Icons.Default.GridView,
            label = "Dashboard",
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
            label = "Image Results",
            selected = currentRoute == Screen.ResultsContacts.route
                    || currentRoute == Screen.ResultsMedia.route,
            onClick = { navigateTo(Screen.ResultsContacts.route) }
        )
        DrawerNavItem(
            icon = Icons.Default.Videocam,
            label = "Video Scanner",
            selected = currentRoute == Screen.VideoScanner.route,
            onClick = { navigateTo(Screen.VideoScanner.route) }
        )
        DrawerNavItem(
            icon = Icons.Default.History,
            label = "Activity Log",
            selected = currentRoute == Screen.Activity.route,
            onClick = { navigateTo(Screen.Activity.route) }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Collect theme mode state
        val currentThemeMode by themeViewModel.themeMode.collectAsState()

        ThemeSection(
            currentMode = currentThemeMode,
            onModeChange = { themeViewModel.setThemeMode(it) }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        DrawerNavItem(
            icon = Icons.Default.Settings,
            label = "Settings",
            selected = false,
            onClick = { scope.launch { drawerState.close() } }
        )
        DrawerNavItem(
            icon = Icons.Default.Info,
            label = "About",
            selected = false,
            onClick = { scope.launch { drawerState.close() } }
        )
    }

    if (showEditDialog) {
        ProfileEditDialog(
            currentName = profileViewModel.name,
            currentEmail = profileViewModel.email,
            onDismiss = { showEditDialog = false },
            onSave = { name, email ->
                profileViewModel.update(name, email)
                showEditDialog = false
            }
        )
    }
}

@Composable
private fun ProfileHeader(
    name: String,
    email: String,
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
                Text(
                    text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                )
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
