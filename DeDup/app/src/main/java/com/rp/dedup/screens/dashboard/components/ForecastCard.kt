package com.rp.dedup.screens.dashboard.components

import android.content.res.Configuration
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rp.dedup.R
import com.rp.dedup.core.model.ForecastConfidence
import com.rp.dedup.core.model.StorageForecast
import com.rp.dedup.ui.theme.DeDupTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ForecastCard(
    forecast: StorageForecast?,
    modifier: Modifier = Modifier
) {
    if (forecast == null) return

    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = remember(locale) { SimpleDateFormat("MMM dd, yyyy", locale) }
    
    val color = when {
        forecast.daysRemaining < 7 -> MaterialTheme.colorScheme.error
        forecast.daysRemaining < 30 -> Color(0xFFF57C00) // Orange
        else -> MaterialTheme.colorScheme.primary
    }

    val icon = when {
        forecast.daysRemaining < 7 -> Icons.Default.Warning
        else -> Icons.Default.Timeline
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Storage Forecast",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = if (forecast.daysRemaining > 0) "~${forecast.daysRemaining} days left" else "Storage nearly full",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = color
                    )
                    Text(
                        text = "Est. full by ${dateFormatter.format(forecast.estimatedFullDate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = color.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = "${Formatter.formatShortFileSize(context, forecast.dailyUsageVelocity)} / day",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = color
                    )
                }
            }

            if (forecast.confidence == ForecastConfidence.LOW) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Collecting more data for higher accuracy",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

private fun sampleForecast(daysRemaining: Int, confidence: ForecastConfidence) = StorageForecast(
    daysRemaining = daysRemaining,
    estimatedFullDate = Date(System.currentTimeMillis() + daysRemaining * 24L * 60 * 60 * 1000),
    dailyUsageVelocity = 180L * 1024 * 1024,
    confidence = confidence
)

@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
private fun ForecastCardHealthyPreview() {
    DeDupTheme {
        ForecastCard(forecast = sampleForecast(daysRemaining = 45, confidence = ForecastConfidence.HIGH))
    }
}

@Preview(showBackground = true, name = "Light Mode — Low Storage")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode — Low Storage")
@Composable
private fun ForecastCardLowStoragePreview() {
    DeDupTheme {
        ForecastCard(forecast = sampleForecast(daysRemaining = 3, confidence = ForecastConfidence.LOW))
    }
}
