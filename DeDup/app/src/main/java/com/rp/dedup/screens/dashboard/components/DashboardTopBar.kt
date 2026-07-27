package com.rp.dedup.screens.dashboard.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.rp.dedup.R

@Composable
fun DashboardTopBar(
    userName: String,
    userImageUrl: String,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    searchButtonModifier: Modifier = Modifier
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.menu),
                    tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                stringResource(R.string.app_name),
                modifier = Modifier.weight(1f).padding(start = 4.dp),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            )
            IconButton(onClick = onSearchClick, modifier = searchButtonModifier) {
                AiSearchIcon()
            }
            IconButton(onClick = {}) {
                Surface(
                    shape = CircleShape,
                    modifier = Modifier.size(30.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (userImageUrl.isNotEmpty()) {
                            AsyncImage(
                                model = userImageUrl,
                                contentDescription = stringResource(R.string.profile),
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = userName.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
