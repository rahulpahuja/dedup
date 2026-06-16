package com.rp.dedup.screens

import android.content.Intent
import android.provider.ContactsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.lazy.rememberLazyListState
import com.rp.dedup.BuildConfig
import com.rp.dedup.core.PaginationBar
import com.rp.dedup.LocalUserProfileViewModel
import com.rp.dedup.Screen
import com.rp.dedup.UIConstants
import com.rp.dedup.R
import com.rp.dedup.core.model.ContactDataEntry
import com.rp.dedup.core.model.MergePreviewGroup
import com.rp.dedup.core.model.ScannedContact
import com.rp.dedup.core.viewmodels.ContactScannerViewModel
import com.rp.dedup.core.ui.DeDupTopBar
import com.rp.dedup.ui.theme.DarkCyan
import com.rp.dedup.ui.theme.DeDupTheme
import com.rp.dedup.ui.theme.LightCyan
import com.rp.dedup.ui.theme.SelectionBarBackground

private fun String.initials(): String =
    split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeduplicationScreen(navController: NavHostController) {
    val context = LocalContext.current
    val analytics = remember { com.rp.dedup.core.analytics.AnalyticsManager(context) }

    val viewModel: ContactScannerViewModel = viewModel(
        factory = ContactScannerViewModel.factory(context)
    )
    val duplicateGroups by viewModel.duplicateGroups.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    LaunchedEffect(Unit) {
        analytics.logScreenView("ContactDedup")
        if (duplicateGroups.isEmpty() && !isScanning) viewModel.startScanning()
    }

    val profileViewModel = LocalUserProfileViewModel.current
    val isGuest = profileViewModel.isGuest

    // SnapshotStateMap gives Compose fine-grained, direct observability — reads of
    // selectedIds[id] inside composables automatically trigger recomposition when that
    // entry changes, unlike Set<String> which requires passing a new value reference.
    val selectedIds = remember { mutableStateMapOf<String, Boolean>() }
    val isPreparingMerge by viewModel.isPreparingMerge.collectAsState()
    val mergePreview by viewModel.mergePreview.collectAsState()
    var showGuestSignInDialog by remember { mutableStateOf(false) }

    // Primary contact IDs (index 0 of each group) must never be deleted.
    val primaryIds = remember(duplicateGroups) {
        duplicateGroups.mapNotNull { it.firstOrNull()?.id }.toSet()
    }

    // Derived state ensures the bottomBar (inside Scaffold's SubcomposeLayout slot)
    // always reacts to selectedIds changes — raw map reads inside subcompose lambdas
    // can be silently skipped if the slot's scope isn't re-entered by Compose.
    val deletableSelectedCount by remember {
        derivedStateOf { selectedIds.count { (k, v) -> v && k !in primaryIds } }
    }

    // Pagination State
    val pageSize = 5
    var currentPage by remember { mutableStateOf(1) }
    val totalPages = remember(duplicateGroups.size) {
        maxOf(1, (duplicateGroups.size + pageSize - 1) / pageSize)
    }
    LaunchedEffect(isScanning) { if (isScanning) currentPage = 1 }

    val listState = rememberLazyListState()
    LaunchedEffect(currentPage) { listState.scrollToItem(0) }

    val pageStart = (currentPage - 1) * pageSize
    val pageGroups = remember(currentPage, duplicateGroups) {
        duplicateGroups.subList(
            pageStart.coerceAtMost(duplicateGroups.size),
            (pageStart + pageSize).coerceAtMost(duplicateGroups.size)
        )
    }

    // Show the merge-selection dialog once the ViewModel has fetched all data rows.
    mergePreview?.let { preview ->
        MergeSelectionDialog(
            groups = preview,
            onDismiss = { viewModel.dismissMergePreview() },
            onConfirm = { keptDataIds ->
                analytics.logContactMerge(preview.size)
                viewModel.executeConfirmedMerge(preview, keptDataIds) { selectedIds.clear() }
            }
        )
    }

    if (showGuestSignInDialog) {
        GuestSignInDialog(
            onDismiss = { showGuestSignInDialog = false },
            onSignIn = {
                showGuestSignInDialog = false
                navController.navigate(Screen.Login.route) {
                    popUpTo(Screen.Dashboard.route) { inclusive = false }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            DeDupTopBar(
                title = stringResource(R.string.app_name),
                navigationIcon = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.menu))
                    }
                },
                actions = {
                    if (!(BuildConfig.IS_PROD && !BuildConfig.DEBUG)) {
                        IconButton(onClick = { navController.navigate(UIConstants.ROUTE_CONTACT_TEST) }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Test Lab",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Column {
                if (totalPages > 1) {
                    PaginationBar(
                        currentPage = currentPage,
                        totalPages = totalPages,
                        onPageChange = { currentPage = it }
                    )
                }
                if (deletableSelectedCount > 0) {
                    SelectionBar(
                        selectedCount = deletableSelectedCount,
                        isLoading = isPreparingMerge,
                        onMerge = {
                            if (isGuest) showGuestSignInDialog = true
                            else viewModel.prepareMergePreview(
                                selectedIds.filter { it.value }.keys.toList()
                            )
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp)
        ) {
            item {
                // Privacy Mode banner (Android 17+)
                if (android.os.Build.VERSION.SDK_INT >= 37) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Privacy Mode Active", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    "Using Android 17 Standardized Picker to protect your contact data.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = {
                                context.startActivity(Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI))
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Open Picker")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.contact_deduplication),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        lineHeight = 40.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            stringResource(R.string.groups_found_label, duplicateGroups.size),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.contact_dedup_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (!isScanning && duplicateGroups.isNotEmpty()) {
                        TextButton(onClick = { viewModel.startScanning() }) { Text("Rescan") }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            when {
                isScanning -> item { ContactsScanningIndicator() }
                duplicateGroups.isEmpty() -> item { ContactsEmptyState() }
                else -> {
                    item {
                        SectionHeader(
                            title = stringResource(R.string.identical_name),
                            subtitle = stringResource(R.string.identical_name_desc),
                            onSelectAll = {
                                duplicateGroups.forEach { group ->
                                    group.drop(1).forEach { contact ->
                                        selectedIds[contact.id] = true
                                    }
                                }
                            }
                        )
                    }
                    items(
                        items = pageGroups,
                        key = { group -> group.map { it.id }.sorted().joinToString(",") }
                    ) { group ->
                        group.forEachIndexed { index, contact ->
                            val isPrimary = index == 0
                            ContactCard(
                                id = contact.id,
                                initials = contact.name.initials().ifEmpty { "?" },
                                name = contact.name,
                                phone = contact.phoneNumbers.firstOrNull(),
                                badge = if (isPrimary) stringResource(R.string.primary_record) else stringResource(R.string.duplicate),
                                badgeColor = if (isPrimary) LightCyan else MaterialTheme.colorScheme.surface,
                                badgeTextColor = if (isPrimary) DarkCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                                isSelected = selectedIds[contact.id] == true,
                                isSelectable = !isPrimary,
                                onToggleSelect = { id ->
                                    if (!isPrimary) {
                                        if (selectedIds[id] == true) selectedIds.remove(id)
                                        else selectedIds[id] = true
                                    }
                                }
                            )
                            if (index < group.lastIndex) Spacer(modifier = Modifier.height(8.dp))
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ContactsScanningIndicator() {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("Scanning contacts…", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ContactsEmptyState() {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.CheckCircle, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text("No duplicate contacts found!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Your address book looks clean.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String, onSelectAll: () -> Unit = {}) {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(4.dp).height(48.dp).background(MaterialTheme.colorScheme.primary))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onSelectAll) {
            Text(stringResource(R.string.select_all), style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.primary))
        }
    }
}

@Composable
fun ContactCard(
    id: String,
    initials: String? = null,
    name: String,
    phone: String? = null,
    email: String? = null,
    info: String? = null,
    infoIcon: ImageVector? = null,
    emailIcon: ImageVector? = null,
    badge: String? = null,
    badgeColor: Color = Color.Transparent,
    badgeTextColor: Color = Color.Black,
    topBadge: String? = null,
    topBadgeColor: Color = Color.Transparent,
    topBadgeTextColor: Color = Color.Black,
    isSelected: Boolean = false,
    isSelectable: Boolean = true,
    isEmailItalic: Boolean = false,
    isInfoItalic: Boolean = false,
    onToggleSelect: (String) -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isSelectable) { onToggleSelect(id) },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            Column {
                if (topBadge != null) {
                    Surface(color = topBadgeColor, shape = RoundedCornerShape(4.dp)) {
                        Text(
                            topBadge,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = topBadgeTextColor, fontSize = 10.sp),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (initials != null) {
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(48.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(initials, style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold))
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface))
                        if (phone != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Phone, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(4.dp))
                                Text(phone, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                            }
                        }
                        if (email != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(emailIcon ?: Icons.Default.Email, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(4.dp))
                                Text(email, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = if (isEmailItalic) FontStyle.Italic else FontStyle.Normal))
                            }
                        }
                        if (info != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (infoIcon != null) {
                                    Icon(infoIcon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(info, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = if (isInfoItalic) FontStyle.Italic else FontStyle.Normal))
                            }
                        }
                        if (badge != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(color = badgeColor, shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    badge,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = badgeTextColor, fontSize = 10.sp),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
            if (isSelectable) {
                // Icon has no touch target — taps pass through to Card's clickable,
                // avoiding the 48dp minimum-touch-target absorption that Checkbox adds.
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.TopEnd).size(24.dp)
                )
            }
        }
    }
}

@Composable
fun SelectionBar(selectedCount: Int = 0, isLoading: Boolean = false, onMerge: () -> Unit = {}) {
    Surface(color = SelectionBarBackground, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.selection_label), style = MaterialTheme.typography.labelSmall.copy(color = Color.LightGray))
                Text(stringResource(R.string.contacts_selected_count, selectedCount), style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
            }
            Button(
                onClick = onMerge,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.Add, contentDescription = null)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.merge_selected))
            }
        }
    }
}



// ── Merge selection dialog ────────────────────────────────────────────────────

@Composable
fun MergeSelectionDialog(
    groups: List<MergePreviewGroup>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    // Pre-select every data ID (keep everything by default).
    val keptDataIds = remember(groups) {
        mutableStateMapOf<String, Boolean>().also { map ->
            groups.forEach { g ->
                (g.phoneEntries + g.emailEntries).forEach { entry -> map[entry.dataId] = true }
            }
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = androidx.compose.ui.Modifier.fillMaxWidth()
        ) {
            Column {
                // Header
                Column(
                    modifier = androidx.compose.ui.Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp)
                ) {
                    Text(
                        "Review & Merge",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(androidx.compose.ui.Modifier.height(4.dp))
                    Text(
                        "Toggle off any numbers or emails you don't want to keep.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider()

                // Scrollable content
                Column(
                    modifier = androidx.compose.ui.Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groups.forEach { group ->
                        if (groups.size > 1) {
                            Text(
                                "Merging into: ${group.primaryName}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = androidx.compose.ui.Modifier.padding(top = 8.dp)
                            )
                        }

                        if (group.phoneEntries.isNotEmpty()) {
                            DataSectionHeader(Icons.Default.Phone, "Phone numbers")
                            group.phoneEntries.forEach { entry ->
                                DataEntryRow(
                                    entry = entry,
                                    isKept = keptDataIds[entry.dataId] == true,
                                    onToggle = { keptDataIds[entry.dataId] = it }
                                )
                            }
                        }

                        if (group.emailEntries.isNotEmpty()) {
                            DataSectionHeader(Icons.Default.Email, "Email addresses")
                            group.emailEntries.forEach { entry ->
                                DataEntryRow(
                                    entry = entry,
                                    isKept = keptDataIds[entry.dataId] == true,
                                    onToggle = { keptDataIds[entry.dataId] = it }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Buttons
                Row(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = {
                            onConfirm(keptDataIds.filter { it.value }.keys.toSet())
                        }
                    ) { Text("Merge") }
                }
            }
        }
    }
}

@Composable
private fun DataSectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = androidx.compose.ui.Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DataEntryRow(
    entry: ContactDataEntry,
    isKept: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val typeLabel = phoneTypeLabel(entry.type)
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isKept)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = androidx.compose.ui.Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = androidx.compose.ui.Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = androidx.compose.ui.Modifier.weight(1f)) {
                Text(
                    entry.value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isKept) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isKept) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (typeLabel != null) {
                        Text(
                            typeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (entry.isFromPrimary)
                            LightCyan
                        else
                            MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            if (entry.isFromPrimary) "Primary" else entry.contactName,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (entry.isFromPrimary) DarkCyan
                                else MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Switch(
                checked = isKept,
                onCheckedChange = onToggle,
                modifier = androidx.compose.ui.Modifier.size(40.dp, 24.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

private fun phoneTypeLabel(type: Int): String? = when (type) {
    ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE   -> "Mobile"
    ContactsContract.CommonDataKinds.Phone.TYPE_HOME     -> "Home"
    ContactsContract.CommonDataKinds.Phone.TYPE_WORK     -> "Work"
    ContactsContract.CommonDataKinds.Phone.TYPE_MAIN     -> "Main"
    ContactsContract.CommonDataKinds.Phone.TYPE_OTHER    -> "Other"
    ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME -> "Fax (Home)"
    ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK -> "Fax (Work)"
    ContactsContract.CommonDataKinds.Email.TYPE_HOME     -> "Home"
    ContactsContract.CommonDataKinds.Email.TYPE_WORK     -> "Work"
    ContactsContract.CommonDataKinds.Email.TYPE_OTHER    -> "Other"
    else -> null
}

@Preview(showBackground = true)
@Composable
fun DeduplicationScreenPreview() {
    DeDupTheme {
        DeduplicationScreen(rememberNavController())
    }
}
