package com.rp.dedup.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
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

    SplashScreenContent(triggered = triggered)
}

@Composable
private fun SplashScreenContent(triggered: Boolean = true) {
    // --- Dynamic Theme Aware Colors ---
    val isDark = isSystemInDarkTheme()
    val bgStart       = if (isDark) Color(0xFF060D1F) else Color(0xFFF5F7FA)
    val bgEnd         = if (isDark) Color(0xFF0D2347) else Color(0xFFE8EDF5)
    val logoCardColor = if (isDark) Color(0xFF101C33) else Color.White
    val watermarkColor = if (isDark) Color.White else Color(0xFF0A1628)
    // titleColor: white on midnight navy, near-black on light — both have strong contrast
    val titleColor    = if (isDark) Color.White       else Color(0xFF0A1628)
    // taglineColor: explicit rather than onSurfaceVariant which bleeds on both backgrounds
    val taglineColor  = if (isDark) Color.White       else Color(0xFF455A6B)
    // accentColor: stays blue in both modes — drives progress bar and pulse rings only
    val accentColor   = if (isDark) Color(0xFF5FA3FF) else PrimaryBlue

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = Brush.verticalGradient(colors = listOf(bgStart, bgEnd)))
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
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .graphicsLayer {
                            scaleX = ring1Scale; scaleY = ring1Scale
                            alpha = ring1Alpha * logoAlpha
                        }
                        .background(accentColor.copy(alpha = 0.18f), RoundedCornerShape(32.dp))
                )
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .graphicsLayer {
                            scaleX = ring2Scale; scaleY = ring2Scale
                            alpha = ring2Alpha * logoAlpha
                        }
                        .background(accentColor.copy(alpha = 0.18f), RoundedCornerShape(32.dp))
                )
                Surface(
                    modifier = Modifier
                        .size(140.dp)
                        .graphicsLayer {
                            scaleX = logoScale; scaleY = logoScale
                            alpha = logoAlpha
                        },
                    shape = RoundedCornerShape(32.dp),
                    color = logoCardColor,
                    shadowElevation = 16.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
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
                        Image(
                            painter = painterResource(R.drawable.ic_dedup_logo),
                            contentDescription = null,
                            modifier = Modifier.size(96.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "DeDup",
                modifier = Modifier.graphicsLayer {
                    translationY = nameTranslationY.dp.toPx()
                    alpha = nameAlpha
                },
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                    letterSpacing = (-1).sp
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.tagline),
                modifier = Modifier.graphicsLayer { alpha = nameAlpha * if (isDark) 0.55f else 0.8f },
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = taglineColor,
                    letterSpacing = 0.3.sp,
                    fontWeight = FontWeight.Normal
                )
            )
        }

        LinearProgressIndicator(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer { alpha = bottomAlpha },
            color = accentColor,
            trackColor = accentColor.copy(alpha = 0.12f)
        )
    }
}

@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
private fun SplashScreenPreview() {
    DeDupTheme {
        SplashScreenContent()
    }
}
