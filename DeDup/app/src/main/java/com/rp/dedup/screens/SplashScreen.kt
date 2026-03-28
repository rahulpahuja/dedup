package com.rp.dedup.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.rp.dedup.R
import com.rp.dedup.Screen
import com.rp.dedup.ui.theme.DeDupTheme
import com.rp.dedup.ui.theme.PrimaryBlue
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(navController: NavHostController) {
    var triggered by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        triggered = true
        delay(3200)
        navController.navigate(Screen.Dashboard.route) {
            popUpTo(Screen.Splash.route) { inclusive = true }
        }
    }

    // --- Entrance animations ---
    val logoScale by animateFloatAsState(
        targetValue = if (triggered) 1f else 0.15f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "logoScale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (triggered) 1f else 0f,
        animationSpec = tween(350),
        label = "logoAlpha"
    )
    val nameTranslationY by animateFloatAsState(
        targetValue = if (triggered) 0f else 60f,
        animationSpec = tween(550, delayMillis = 280, easing = LinearOutSlowInEasing),
        label = "nameY"
    )
    val nameAlpha by animateFloatAsState(
        targetValue = if (triggered) 1f else 0f,
        animationSpec = tween(550, delayMillis = 280),
        label = "nameAlpha"
    )
    val bottomAlpha by animateFloatAsState(
        targetValue = if (triggered) 1f else 0f,
        animationSpec = tween(600, delayMillis = 600),
        label = "bottomAlpha"
    )

    // --- Infinite animations ---
    val infinite = rememberInfiniteTransition(label = "splash")

    val beamRotation by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "beam"
    )

    // Two offset pulse rings
    val ring1Scale by infinite.animateFloat(
        initialValue = 1f, targetValue = 1.7f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "ring1Scale"
    )
    val ring1Alpha by infinite.animateFloat(
        initialValue = 0.45f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "ring1Alpha"
    )
    val ring2Scale by infinite.animateFloat(
        initialValue = 1f, targetValue = 1.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            initialStartOffset = StartOffset(800)
        ),
        label = "ring2Scale"
    )
    val ring2Alpha by infinite.animateFloat(
        initialValue = 0f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1600
                0f at 0
                0.45f at 100
                0f at 1600
            }
        ),
        label = "ring2Alpha"
    )

    // Staggered dot pulse
    val dot1 by infinite.animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(480), RepeatMode.Reverse),
        label = "d1"
    )
    val dot2 by infinite.animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(480), RepeatMode.Reverse,
            initialStartOffset = StartOffset(160)
        ),
        label = "d2"
    )
    val dot3 by infinite.animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(480), RepeatMode.Reverse,
            initialStartOffset = StartOffset(320)
        ),
        label = "d3"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFF0F4F8), Color(0xFFDDE6EF))
                )
            )
    ) {
        // Background watermark
        Text(
            text = "DATA",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.dp)
                .graphicsLayer { alpha = logoAlpha * 0.04f },
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 120.sp,
                fontWeight = FontWeight.Black,
                color = Color.Black,
                letterSpacing = 8.sp
            )
        )

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo + pulse rings
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(200.dp)
            ) {
                // Pulse ring 1
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .graphicsLayer {
                            scaleX = ring1Scale
                            scaleY = ring1Scale
                            alpha = ring1Alpha * logoAlpha
                        }
                        .background(PrimaryBlue.copy(alpha = 0.18f), RoundedCornerShape(32.dp))
                )
                // Pulse ring 2
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .graphicsLayer {
                            scaleX = ring2Scale
                            scaleY = ring2Scale
                            alpha = ring2Alpha * logoAlpha
                        }
                        .background(PrimaryBlue.copy(alpha = 0.18f), RoundedCornerShape(32.dp))
                )

                // Logo card
                Surface(
                    modifier = Modifier
                        .size(140.dp)
                        .graphicsLayer {
                            scaleX = logoScale
                            scaleY = logoScale
                            alpha = logoAlpha
                        },
                    shape = RoundedCornerShape(32.dp),
                    color = Color.White,
                    shadowElevation = 16.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // Rotating sweep beam
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .rotate(beamRotation)
                                .background(
                                    brush = Brush.sweepGradient(
                                        colorStops = arrayOf(
                                            0.0f to Color.Transparent,
                                            0.6f to Color.Transparent,
                                            0.8f to Color(0xFF80DEEA).copy(alpha = 0.25f),
                                            1.0f to Color.Transparent
                                        )
                                    )
                                )
                        )
                        // Custom logo
                        Image(
                            painter = painterResource(R.drawable.ic_dedup_logo),
                            contentDescription = null,
                            modifier = Modifier.size(80.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // App name — slides up
            Text(
                text = "Deduplicator",
                modifier = Modifier.graphicsLayer {
                    translationY = nameTranslationY.dp.toPx()
                    alpha = nameAlpha
                },
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue,
                    letterSpacing = (-1).sp
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Staggered loading dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.graphicsLayer { alpha = nameAlpha }
            ) {
                listOf(dot1, dot2, dot3).forEach { dotAlpha ->
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .graphicsLayer { alpha = dotAlpha }
                            .background(PrimaryBlue, CircleShape)
                    )
                }
            }
        }

        // Bottom section — fades in last
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp)
                .graphicsLayer { alpha = bottomAlpha },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "V.24",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 140.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black.copy(alpha = 0.03f)
                )
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "SURGICAL STORAGE MANAGEMENT.",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        letterSpacing = 2.sp
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(4) {
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .background(Color.LightGray, CircleShape)
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SplashScreenPreview() {
    DeDupTheme {
        SplashScreen(rememberNavController())
    }
}
