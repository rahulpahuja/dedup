package com.rp.dedup.core.widget

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.rp.dedup.MainActivity

class StorageWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val stats = getStorageStats(context)
        
        provideContent {
            GlanceTheme {
                StorageWidgetContent(stats)
            }
        }
    }

    @Composable
    private fun StorageWidgetContent(stats: WidgetStorageStats) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "Used Space",
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = GlanceTheme.colors.onSurfaceVariant
                        )
                    )
                    Text(
                        text = stats.usedLabel,
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlanceTheme.colors.onSurface
                        )
                    )
                }
                Text(
                    text = "${stats.usedPercent}%",
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.primary
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Using official LinearProgressIndicator from Glance 1.1.0+
            LinearProgressIndicator(
                progress = stats.usedFraction,
                modifier = GlanceModifier.fillMaxWidth(),
                color = GlanceTheme.colors.primary,
                backgroundColor = GlanceTheme.colors.surfaceVariant
            )

            Spacer(modifier = GlanceModifier.height(8.dp))

            Text(
                text = "${stats.freeLabel} free",
                style = TextStyle(
                    fontSize = 11.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
        }
    }

    private fun getStorageStats(context: Context): WidgetStorageStats {
        return try {
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            val blockSize = stat.blockSizeLong
            val total = stat.blockCountLong * blockSize
            val free = stat.availableBlocksLong * blockSize
            val used = total - free
            
            WidgetStorageStats(
                usedLabel = Formatter.formatShortFileSize(context, used),
                freeLabel = Formatter.formatShortFileSize(context, free),
                usedPercent = if (total > 0) (used * 100 / total).toInt() else 0,
                usedFraction = if (total > 0) used.toFloat() / total else 0f
            )
        } catch (e: Exception) {
            WidgetStorageStats("Error", "Error", 0, 0f)
        }
    }
}

data class WidgetStorageStats(
    val usedLabel: String,
    val freeLabel: String,
    val usedPercent: Int,
    val usedFraction: Float
)

class StorageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StorageWidget()
}
