package com.rp.dedup.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.rp.dedup.ui.theme.DarkCyan
import com.rp.dedup.ui.theme.DeDupTheme
import com.rp.dedup.ui.theme.LightCyan
import com.rp.dedup.ui.theme.SelectionBarBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeduplicationScreen(navController: NavHostController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "DeDuplicator",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { }) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { }) {
                        Surface(
                            shape = CircleShape,
                            modifier = Modifier.size(32.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = "Profile",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Column {
                SelectionBar()
                BottomNavigationBar()
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Contact\nDeduplication",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        lineHeight = 40.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "14 MERGEABLE\nGROUPS FOUND",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Review suggestions to\noptimize your address\nbook.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            // Identical Name Section
            item {
                SectionHeader("Identical Name", "Contacts sharing the exact same display name.")
                ContactCard(
                    initials = "JS",
                    name = "Johnathan Smith",
                    phone = "+1 (555) 123-4567",
                    email = "john.smith@gmail.com",
                    badge = "PRIMARY RECORD",
                    badgeColor = LightCyan,
                    badgeTextColor = DarkCyan,
                    isSelected = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                ContactCard(
                    initials = "JS",
                    name = "Johnathan Smith",
                    phone = "+1 (555) 123-4567",
                    email = "No email provided",
                    badge = "DUPLICATE",
                    badgeColor = MaterialTheme.colorScheme.surface,
                    badgeTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    isSelected = true,
                    isEmailItalic = true
                )
                Spacer(modifier = Modifier.height(32.dp))
            }

            // Same Number Section
            item {
                SectionHeader(
                    "Same Number",
                    "Different names associated with the same phone number."
                )
                ContactCard(
                    name = "Sarah Connor",
                    phone = "+1 (212) 999-0000",
                    info = "Last synced: 2 days ago",
                    isSelected = false
                )
                Spacer(modifier = Modifier.height(12.dp))
                ContactCard(
                    name = "S. Connor",
                    phone = "+1 (212) 999-0000",
                    info = "Imported from LinkedIn",
                    isSelected = false
                )
                Spacer(modifier = Modifier.height(12.dp))
                ContactCard(
                    name = "Sarah C.",
                    phone = "+1 (212) 999-0000",
                    info = "Manual entry",
                    isSelected = false
                )
                Spacer(modifier = Modifier.height(32.dp))
            }

            // Similar Info Section
            item {
                SectionHeader("Similar Info", "Fuzzy matching on emails and company data.")
                ContactCard(
                    name = "Alex Rivera",
                    info = "Design Architect at Studio-X",
                    email = "a.rivera@studio-x.io",
                    infoIcon = Icons.Default.Info,
                    emailIcon = Icons.Default.Email,
                    topBadge = "SAFE TO KEEP",
                    topBadgeColor = LightCyan,
                    topBadgeTextColor = DarkCyan,
                    isSelected = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                ContactCard(
                    name = "Alex Rivera",
                    info = "Company info missing",
                    infoIcon = Icons.Default.Info,
                    topBadge = "POTENTIAL MATCH",
                    topBadgeColor = Color.Transparent,
                    topBadgeTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    isSelected = true,
                    isInfoItalic = true
                )
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(48.dp)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = { }) {
            Text(
                "Select\nAll",
                style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
fun ContactCard(
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
    isEmailItalic: Boolean = false,
    isInfoItalic: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            Column {
                if (topBadge != null) {
                    Surface(
                        color = topBadgeColor,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            topBadge,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = topBadgeTextColor,
                                fontSize = 10.sp
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (initials != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    initials,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        if (phone != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Phone,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    phone,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                        if (email != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    emailIcon ?: Icons.Default.Email,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    email,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontStyle = if (isEmailItalic) FontStyle.Italic else FontStyle.Normal
                                    )
                                )
                            }
                        }
                        if (info != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (infoIcon != null) {
                                    Icon(
                                        infoIcon,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    info,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontStyle = if (isInfoItalic) FontStyle.Italic else FontStyle.Normal
                                    )
                                )
                            }
                        }
                        if (badge != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = badgeColor,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    badge,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = badgeTextColor,
                                        fontSize = 10.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
            Checkbox(
                checked = isSelected,
                onCheckedChange = { },
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}

@Composable
fun SelectionBar() {
    Surface(
        color = SelectionBarBackground,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "SELECTION",
                    style = MaterialTheme.typography.labelSmall.copy(color = Color.LightGray)
                )
                Text(
                    "7 Contacts",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Button(
                onClick = { },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null) // Fallback for Merge icon
                Spacer(modifier = Modifier.width(8.dp))
                Text("Merge Selected")
            }
        }
    }
}

@Composable
fun BottomNavigationBar() {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text("DASHBOARD") },
            selected = false,
            onClick = { }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Search, contentDescription = null) },
            label = { Text("SCAN") },
            selected = false,
            onClick = { }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
            label = { Text("RESULTS") },
            selected = true,
            onClick = { }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text("SETTINGS") },
            selected = false,
            onClick = { }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DeduplicationScreenPreview() {
    DeDupTheme {
        DeduplicationScreen(rememberNavController())
    }
}
