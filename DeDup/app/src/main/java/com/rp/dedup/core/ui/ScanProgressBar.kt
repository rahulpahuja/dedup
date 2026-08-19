package com.rp.dedup.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rp.dedup.R
import kotlin.math.roundToInt

/**
 * Progress indicator for image/video scans. Switches from indeterminate to a
 * determinate percentage the moment the total item count is known (right after
 * the initial MediaStore query completes), so the user isn't left guessing how
 * long a scan will take.
 */
@Composable
fun ScanProgressBar(
    scannedCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier
) {
    val fraction = if (totalCount > 0) (scannedCount.toFloat() / totalCount).coerceIn(0f, 1f) else 0f
    val percent = (fraction * 100).roundToInt()

    Column(modifier = modifier.fillMaxWidth()) {
        if (totalCount > 0) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.scan_progress_percent, percent),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.scan_progress_count, scannedCount, totalCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}
