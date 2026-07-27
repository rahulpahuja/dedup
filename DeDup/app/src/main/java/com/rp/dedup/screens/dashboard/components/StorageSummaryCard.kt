package com.rp.dedup.screens.dashboard.components

import android.text.format.Formatter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rp.dedup.R
import com.rp.dedup.UIConstants
import com.rp.dedup.core.model.StorageStats

@Composable
fun StorageSummaryCard(
    stats: StorageStats = StorageStats(),
    reclaimableBytes: Long = 0L,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val usedFractionAnimated by animateFloatAsState(
        targetValue = stats.usedFraction,
        animationSpec = tween(durationMillis = 900),
        label = "usedFraction"
    )
    val savingsFraction = if (stats.usedBytes > 0)
        (reclaimableBytes.toFloat() / stats.usedBytes).coerceIn(0f, 1f)
    else 0f
    val savingsFractionAnimated by animateFloatAsState(
        targetValue = savingsFraction,
        animationSpec = tween(durationMillis = 900, delayMillis = 200),
        label = "savingsFraction"
    )

    val usedLabel = if (stats.totalBytes > 0)
        "${Formatter.formatShortFileSize(context, stats.usedBytes)} / ${
            Formatter.formatShortFileSize(context, stats.totalBytes)
        }"
    else stringResource(R.string.calculating)

    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        stringResource(R.string.used_space),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        usedLabel,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Icon(
                    Icons.Default.Storage,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                stringResource(R.string.device_storage),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { usedFractionAnimated },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                strokeCap = StrokeCap.Round
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                if (stats.totalBytes > 0)
                    "${"%.0f".format(stats.usedFraction * 100)}% of storage used  •  " +
                    "${Formatter.formatShortFileSize(context, stats.freeBytes)} free"
                else stringResource(R.string.loading_storage_info),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f)
            )

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.dedup_savings),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                    )
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = UIConstants.ColorSavingsGreen.copy(alpha = 0.15f)
                ) {
                    Text(
                        if (reclaimableBytes > 0)
                            Formatter.formatShortFileSize(context, reclaimableBytes)
                        else "0 B",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = UIConstants.ColorSavingsGreen
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { savingsFractionAnimated },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = UIConstants.ColorSavingsGreen,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                strokeCap = StrokeCap.Round
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                if (reclaimableBytes > 0)
                    stringResource(R.string.storage_summary_reclaimable, Formatter.formatShortFileSize(context, reclaimableBytes))
                else stringResource(R.string.run_scan_prompt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f)
            )
        }
    }
}
