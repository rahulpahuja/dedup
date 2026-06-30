package com.rp.dedup.screens.dashboard.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rp.dedup.core.model.StorageHealthScore

@Composable
fun StorageHealthScoreCard(
    score: StorageHealthScore,
    modifier: Modifier = Modifier
) {
    val animatedScore = remember { Animatable(0f) }
    LaunchedEffect(score.overallScore) {
        animatedScore.animateTo(score.overallScore.toFloat(), tween(1000))
    }

    val scoreColor = when {
        score.overallScore >= 85 -> Color(0xFF4CAF50)
        score.overallScore >= 70 -> Color(0xFF8BC34A)
        score.overallScore >= 50 -> Color(0xFFFFC107)
        score.overallScore >= 30 -> Color(0xFFFF9800)
        else                     -> Color(0xFFF44336)
    }

    Surface(
        modifier      = modifier.fillMaxWidth(),
        shape         = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier            = Modifier.padding(20.dp),
            verticalAlignment   = Alignment.CenterVertically
        ) {
            // Score arc
            Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke  = 9.dp.toPx()
                    val padding = stroke / 2f
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    val topLeft = Offset(padding, padding)

                    // Track
                    drawArc(
                        color      = scoreColor.copy(alpha = 0.15f),
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter  = false,
                        topLeft    = topLeft,
                        size       = arcSize,
                        style      = Stroke(stroke, cap = StrokeCap.Round)
                    )
                    // Fill
                    drawArc(
                        color      = scoreColor,
                        startAngle = 135f,
                        sweepAngle = 270f * (animatedScore.value / 100f),
                        useCenter  = false,
                        topLeft    = topLeft,
                        size       = arcSize,
                        style      = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text  = animatedScore.value.toInt().toString(),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize   = 24.sp,
                            color      = scoreColor
                        )
                    )
                    Text(
                        text  = "/100",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FavoriteBorder, null,
                        tint = scoreColor, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Storage Health",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    score.label,
                    style = MaterialTheme.typography.bodyMedium.copy(color = scoreColor, fontWeight = FontWeight.SemiBold)
                )

                score.previousScore?.let { prev ->
                    val delta = score.overallScore - prev
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when {
                                delta > 0  -> Icons.Default.TrendingUp
                                delta < 0  -> Icons.Default.TrendingDown
                                else       -> Icons.Default.TrendingFlat
                            },
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = when {
                                delta > 0  -> Color(0xFF4CAF50)
                                delta < 0  -> Color(0xFFF44336)
                                else       -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text  = if (delta == 0) "No change" else "${if (delta > 0) "+" else ""}$delta since last time",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Mini breakdown bars
                ScoreBreakdownBar("Free Space", score.freeSpaceScore, 30, Color(0xFF4285F4))
                ScoreBreakdownBar("Clean Scans", score.reclaimableScore, 25, Color(0xFF34A853))
                ScoreBreakdownBar("Recency", score.scanRecencyScore, 25, Color(0xFFFBBC05))
                ScoreBreakdownBar("Cache", score.cacheSizeScore, 20, Color(0xFFEA4335))
            }
        }
    }
}

@Composable
private fun ScoreBreakdownBar(label: String, value: Int, max: Int, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier          = Modifier.padding(vertical = 1.dp)
    ) {
        Text(
            text      = label,
            style     = MaterialTheme.typography.labelSmall,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier  = Modifier.width(70.dp)
        )
        LinearProgressIndicator(
            progress     = { value.toFloat() / max },
            modifier     = Modifier.weight(1f).height(4.dp),
            color        = color,
            trackColor   = color.copy(alpha = 0.2f)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text  = "$value",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
