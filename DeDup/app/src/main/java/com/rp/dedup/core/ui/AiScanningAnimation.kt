package com.rp.dedup.core.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.rp.dedup.UIConstants

@Composable
fun AiScanningAnimation(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "AI_Infinite")
    
    // 1. Animation State Reads (Deferred to Graphics Layer)
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CorePulse"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing)
        ),
        label = "OrbitRotation"
    )

    // 2. Pre-calculated Colors (Avoid theme lookups in Draw phase)
    val coreColor = UIConstants.ColorIconPalette
    val orbitColor1 = UIConstants.ColorImages
    val orbitColor2 = UIConstants.ColorApks
    val glowColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)

    Box(
        modifier = modifier.size(240.dp),
        contentAlignment = Alignment.Center
    ) {
        // 3. Background Orbitals (Optimized Draw Cache)
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    // Pre-calculate sizes that don't change per frame
                    val center = Offset(size.width / 2, size.height / 2)
                    val baseRadius = size.minDimension / 4
                    val outerOrbitSize = Size(baseRadius * 4, baseRadius * 4)
                    val innerOrbitSize = Size(baseRadius * 3, baseRadius * 3)
                    val sweepOrbitSize = Size(baseRadius * 4.5f, baseRadius * 4.5f)
                    
                    val outerTopLeft = Offset(center.x - baseRadius * 2, center.y - baseRadius * 2)
                    val innerTopLeft = Offset(center.x - baseRadius * 1.5f, center.y - baseRadius * 1.5f)
                    val sweepTopLeft = Offset(center.x - baseRadius * 2.25f, center.y - baseRadius * 2.25f)

                    val outerStroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    val innerStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    val sweepStroke = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)

                    val glowBrush = Brush.radialGradient(
                        colors = listOf(glowColor, Color.Transparent),
                        center = center,
                        radius = baseRadius * 3
                    )

                    // Reads `progress` here (outside onDrawWithContent) so this Shader is only
                    // rebuilt when progress actually changes (a handful of times per scan),
                    // not on every rotation/pulse redraw frame (~60/sec) — building a new
                    // SweepGradientShader per frame was the source of the reported jank.
                    val sweepBrush = Brush.sweepGradient(
                        0f to Color.Transparent,
                        progress to orbitColor1,
                        center = center
                    )

                    onDrawWithContent {
                        // Background Glow
                        drawCircle(brush = glowBrush, radius = baseRadius * 3, center = center)

                        // Outer Orbital
                        rotate(rotation) {
                            drawArc(
                                color = orbitColor1.copy(alpha = 0.5f),
                                startAngle = 0f,
                                sweepAngle = 280f,
                                useCenter = false,
                                style = outerStroke,
                                size = outerOrbitSize,
                                topLeft = outerTopLeft
                            )
                        }

                        // Inner Orbital
                        rotate(-rotation * 1.4f) {
                            drawArc(
                                color = orbitColor2.copy(alpha = 0.7f),
                                startAngle = 180f,
                                sweepAngle = 200f,
                                useCenter = false,
                                style = innerStroke,
                                size = innerOrbitSize,
                                topLeft = innerTopLeft
                            )
                        }

                        // Progress Sweep
                        drawArc(
                            brush = sweepBrush,
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            style = sweepStroke,
                            size = sweepOrbitSize,
                            topLeft = sweepTopLeft
                        )

                        // Pulsing Connection Lines
                        if (progress > 0) {
                            val lineCount = 8
                            val lineRotation = rotation % (360f / lineCount)
                            for (i in 0 until lineCount) {
                                rotate(lineRotation + (i * (360f / lineCount))) {
                                    drawLine(
                                        color = coreColor.copy(alpha = (pulseScale - 0.5f).coerceIn(0f, 1f)),
                                        start = center.copy(x = center.x + baseRadius),
                                        end = center.copy(x = center.x + baseRadius * 1.8f),
                                        strokeWidth = 2.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }
                    }
                }
        )

        // 4. Central Brain (Optimized Graphics Layer)
        // Using graphicsLayer prevents RECOMPOSITION on the entire AiScanningAnimation
        // and its children. Scaling happens in the hardware GPU phase.
        Box(
            modifier = Modifier
                .size(64.dp)
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                },
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
