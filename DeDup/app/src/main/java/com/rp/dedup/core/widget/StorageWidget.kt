package com.rp.dedup.core.widget

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height as composeHeight
import androidx.compose.foundation.layout.padding as composePadding
import androidx.compose.foundation.layout.fillMaxWidth as composeFillMaxWidth
import androidx.compose.foundation.layout.size as composeSize
import androidx.compose.foundation.layout.width as composeWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator as M3LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text as M3Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment as ComposeAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight as ComposeFontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.rp.dedup.MainActivity
import com.rp.dedup.R
import com.rp.dedup.UIConstants

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
                        color = GlanceTheme.colors.primary
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(12.dp))

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "Used Space",
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = GlanceTheme.colors.onSurfaceVariant
                        )
                    )
                    Text(
                        text = stats.usedLabel,
                        style = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlanceTheme.colors.onSurface
                        )
                    )
                }
                Text(
                    text = "${stats.usedPercent}%",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.primary
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

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
                    fontSize = 10.sp,
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
        } catch (_: Exception) {
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

// ── Widget Preview (Standard Compose simulation) ──────────────────────────────

@Preview(showBackground = true, widthDp = 180, heightDp = 150)
@Composable
fun StorageWidgetPreview() {
    val stats = WidgetStorageStats(
        usedLabel = "42.5 GB",
        freeLabel = "21.5 GB",
        usedPercent = 66,
        usedFraction = 0.66f
    )

    MaterialTheme {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.composePadding(8.dp)
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .composePadding(12.dp)
                    .composeFillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = ComposeAlignment.CenterHorizontally
            ) {
                // Header Simulation
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.composeFillMaxWidth(),
                    verticalAlignment = ComposeAlignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_dedup_logo),
                        contentDescription = null,
                        modifier = Modifier.composeSize(20.dp)
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.composeWidth(8.dp))
                    M3Text(
                        text = UIConstants.APP_NAME,
                        fontSize = 13.sp,
                        fontWeight = ComposeFontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                androidx.compose.foundation.layout.Spacer(modifier = Modifier.composeHeight(12.dp))

                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.composeFillMaxWidth(),
                    verticalAlignment = ComposeAlignment.CenterVertically
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        M3Text(
                            text = "Used Space",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        M3Text(
                            text = stats.usedLabel,
                            fontSize = 14.sp,
                            fontWeight = ComposeFontWeight.Bold
                        )
                    }
                    M3Text(
                        text = "${stats.usedPercent}%",
                        fontSize = 16.sp,
                        fontWeight = ComposeFontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                androidx.compose.foundation.layout.Spacer(
                    modifier = Modifier.composeHeight(8.dp)
                )

                M3LinearProgressIndicator(
                    progress = { stats.usedFraction },
                    modifier = Modifier.composeFillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round
                )

                androidx.compose.foundation.layout.Spacer(
                    modifier = Modifier.composeHeight(8.dp)
                )

                M3Text(
                    text = "${stats.freeLabel} free",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
