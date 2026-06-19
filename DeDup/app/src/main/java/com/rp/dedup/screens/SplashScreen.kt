package com.rp.dedup.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.rp.dedup.R
import com.rp.dedup.Screen
import com.rp.dedup.core.firebase.auth.FirebaseAuthManager
import com.rp.dedup.core.notifications.ToastManager
import com.rp.dedup.ui.theme.DeDupTheme
import com.rp.dedup.ui.theme.PrimaryBlue
import kotlinx.coroutines.delay

/**
 * @param hasPendingDeepLink When true the splash timer completes without navigating —
 *   MainActivity's LaunchedEffect will handle navigation to the deep-link route.
 */
@Composable
fun SplashScreen(navController: NavHostController, hasPendingDeepLink: Boolean = false) {
    val context = LocalContext.current
    val authManager = remember(context) { FirebaseAuthManager(ToastManager(context.applicationContext)) }
    DisposableEffect(authManager) { onDispose { authManager.close() } }
    var triggered by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        triggered = true
        delay(3200)

        // If a deep link is pending, DON'T navigate. MainActivity will handle it.
        if (hasPendingDeepLink) return@LaunchedEffect

        // Persist login: if user is already authenticated, skip LoginScreen
        val nextRoute = if (authManager.isUserLoggedIn) {
            Screen.Dashboard.route
        } else {
            Screen.Login.route
        }

        navController.navigate(nextRoute) {
            popUpTo(Screen.Splash.route) { inclusive = true }
        }
    }

    // --- Dynamic Theme Aware Colors ---
    // We use isSystemInDarkTheme() here to ensure the splash background 
    // immediately matches the MaterialTheme wrapper provided in MainActivity.
    val isDark = isSystemInDarkTheme()
    val bgStart = if (isDark) Color(0xFF060D1F) else Color(0xFFF0F4F8)
    val bgEnd = if (isDark) Color(0xFF0D2347) else Color(0xFFDDE6EF)
    val logoCardColor = if (isDark) Color(0xFF1E1E1E) else Color.White
    val watermarkColor = if (isDark) Color.White else Color.Black
    val nameColor = if (isDark) Color(0xFF5FA3FF) else PrimaryBlue

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
                    colors = listOf(bgStart, bgEnd)
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
                color = watermarkColor,
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
                    color = logoCardColor,
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
                            modifier = Modifier.size(96.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // App name — slides up
            Text(
                text = "DeDup",
                modifier = Modifier.graphicsLayer {
                    translationY = nameTranslationY.dp.toPx()
                    alpha = nameAlpha
                },
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = nameColor,
                    letterSpacing = (-1).sp
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Tagline — fades in with the app name
            Text(
                text = stringResource(R.string.tagline),
                modifier = Modifier.graphicsLayer { alpha = nameAlpha * 0.75f },
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = if (triggered) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else Color.Gray,
                    letterSpacing = 0.3.sp,
                    fontWeight = FontWeight.Normal
                )
            )
        }

        // Bottom loading indicator — fades in last
        LinearProgressIndicator(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 0.dp)
                .graphicsLayer { alpha = bottomAlpha },
            color = nameColor,
            trackColor = nameColor.copy(alpha = 0.12f)
        )
    }
}

@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
fun SplashScreenPreview() {
    DeDupTheme {
        SplashScreen(rememberNavController())
    }
}
