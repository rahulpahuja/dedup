package com.rp.dedup.core.widget

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.rp.dedup.MainActivity
import com.rp.dedup.R
import com.rp.dedup.Screen
import com.rp.dedup.UIConstants
import com.rp.dedup.core.model.WidgetStorageStats

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
                .background(ColorProvider(day = Color(0xFF0D1B3E), night = Color(0xFF0D1B3E))) // Dark deep blue/purple base
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Logo and App Title
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_dedup_logo),
                    contentDescription = null,
                    modifier = GlanceModifier.size(24.dp)
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                Text(
                    text = UIConstants.APP_NAME,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(day = Color(0xFF9C27B0), night = Color(0xFFCE93D8)) // Purple title
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(10.dp))

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "Used Space",
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = ColorProvider(day = Color(0xB3FFFFFF), night = Color(0xB3FFFFFF)) // White alpha
                        )
                    )
                    Text(
                        text = stats.usedLabel,
                        style = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorProvider(day = Color.White, night = Color.White)
                        )
                    )
                }
                Text(
                    text = "${stats.usedPercent}%",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(day = Color(0xFF9C27B0), night = Color(0xFFCE93D8)) // Purple
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            LinearProgressIndicator(
                progress = stats.usedFraction,
                modifier = GlanceModifier.fillMaxWidth(),
                color = ColorProvider(day = Color(0xFF9C27B0), night = Color(0xFFCE93D8)), // Purple progress
                backgroundColor = ColorProvider(day = Color(0x339C27B0), night = Color(0x33CE93D8))
            )

            Spacer(modifier = GlanceModifier.height(8.dp))

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${stats.freeLabel} free",
                    style = TextStyle(
                        fontSize = 10.sp,
                        color = ColorProvider(day = Color(0xB3FFFFFF), night = Color(0xB3FFFFFF))
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
                
                // Deep link button to AI Cleanup
                Text(
                    text = "AI CLEANUP",
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(day = Color.White, night = Color.White)
                    ),
                    modifier = GlanceModifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .background(ColorProvider(day = Color(0xFF673AB7), night = Color(0xFF9575CD))) // Deep Purple Button
                        .clickable(actionStartActivity<MainActivity>(
                            actionParametersOf(
                                androidx.glance.action.ActionParameters.Key<String>("target_route") to Screen.SmartJunk.route
                            )
                        ))
                )
            }
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
        } catch (_: Exception) {
            WidgetStorageStats("Error", "Error", 0, 0f)
        }
    }
}

class StorageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StorageWidget()
}
