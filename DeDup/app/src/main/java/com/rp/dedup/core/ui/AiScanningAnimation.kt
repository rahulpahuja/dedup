package com.rp.dedup.core.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.rp.dedup.UIConstants

@Composable
fun AiScanningAnimation(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "AI_Infinite")
    
    // Core pulse animation
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CorePulse"
    )

    // Orbital rotation
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing)
        ),
        label = "OrbitRotation"
    )

    // Color definitions from theme
    val coreColor = UIConstants.ColorIconPalette
    val orbitColor1 = UIConstants.ColorImages
    val orbitColor2 = UIConstants.ColorApks
    val glowColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)

    Box(
        modifier = modifier.size(240.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val baseRadius = size.minDimension / 4

            // 1. Background Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glowColor, Color.Transparent),
                    center = center,
                    radius = baseRadius * 3
                ),
                radius = baseRadius * 3,
                center = center
            )

            // 2. Outer Orbital Ring (Slow)
            rotate(rotation) {
                drawArc(
                    color = orbitColor1.copy(alpha = 0.6f),
                    startAngle = 0f,
                    sweepAngle = 280f,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                    size = Size(baseRadius * 4, baseRadius * 4),
                    topLeft = Offset(center.x - baseRadius * 2, center.y - baseRadius * 2)
                )
            }

            // 3. Inner Orbital Ring (Fast, Reverse)
            rotate(-rotation * 1.5f) {
                drawArc(
                    color = orbitColor2.copy(alpha = 0.8f),
                    startAngle = 180f,
                    sweepAngle = 200f,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                    size = Size(baseRadius * 3, baseRadius * 3),
                    topLeft = Offset(center.x - baseRadius * 1.5f, center.y - baseRadius * 1.5f)
                )
            }

            // 4. Progress Sweep (Follows scanner progress)
            drawArc(
                brush = Brush.sweepGradient(
                    0f to Color.Transparent,
                    progress to orbitColor1,
                    center = center
                ),
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round),
                size = Size(baseRadius * 4.5f, baseRadius * 4.5f),
                topLeft = Offset(center.x - baseRadius * 2.25f, center.y - baseRadius * 2.25f)
            )

            // 5. Pulsing Connection Lines (Scanning Effect)
            if (progress > 0) {
                val lineCount = 8
                val lineRotation = rotation % (360f / lineCount)
                for (i in 0 until lineCount) {
                    rotate(lineRotation + (i * (360f / lineCount))) {
                        drawLine(
                            color = coreColor.copy(alpha = pulseScale - 0.5f),
                            start = center.copy(x = center.x + baseRadius),
                            end = center.copy(x = center.x + baseRadius * 2),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }

        // 6. Central AI Brain Node
        Box(
            modifier = Modifier.size(64.dp * pulseScale),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                tint = coreColor
            )
        }
    }
}
