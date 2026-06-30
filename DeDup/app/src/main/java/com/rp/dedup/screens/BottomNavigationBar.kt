package com.rp.dedup.screens

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rp.dedup.R
import com.rp.dedup.Screen
import com.rp.dedup.ui.theme.DeDupTheme

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    val selectedIndex = when {
        currentRoute == Screen.Dashboard.route -> 0
        currentRoute == Screen.Cleanup.route -> 1
        currentRoute?.startsWith("file_scanner") == true || currentRoute == Screen.FileBrowser.route -> 2
        currentRoute == Screen.VideoScanner.route -> 3
        currentRoute == Screen.Settings.route || currentRoute == Screen.ScanHistory.route -> 4
        else -> 0
    }

    data class NavEntry(val icon: ImageVector, val labelRes: Int, val route: String)
    val items = listOf(
        NavEntry(Icons.Default.GridView,    R.string.nav_dash,     Screen.Dashboard.route),
        NavEntry(Icons.Default.Search,      R.string.nav_scan,     Screen.Cleanup.route),
        NavEntry(Icons.Default.Description, R.string.nav_files,    Screen.FileBrowser.route),
        NavEntry(Icons.Default.Videocam,    R.string.nav_video,    Screen.VideoScanner.route),
        NavEntry(Icons.Default.Settings,    R.string.nav_settings, Screen.Settings.route),
    )

    val isDark    = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primary   = MaterialTheme.colorScheme.primary
    val glassBase = if (isDark) Color(0xFF1C1C1E).copy(alpha = 0.78f) else Color(0xFFF5F5F7).copy(alpha = 0.82f)
    val topSheen  = if (isDark) Color.White.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.60f)
    val bottomTint   = if (isDark) Color.Black.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.04f)
    val borderBright = if (isDark) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.90f)
    val borderDim    = if (isDark) Color.White.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.14f)
    val shimmer      = if (isDark) Color.White.copy(alpha = 0.075f) else Color.White.copy(alpha = 0.45f)
    val shadowColor  = if (isDark) Color.Black.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.12f)

    val glass = rememberInfiniteTransition(label = "glass")
    val sweep by glass.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep"
    )
    val specular by glass.animateFloat(
        initialValue = 0.55f, targetValue = 1.00f,
        animationSpec = infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "specular"
    )

    val barHeight    = 72.dp
    val pillHeight   = 46.dp
    val pillWidth    = 52.dp
    val cornerRadius = 28.dp

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .navigationBarsPadding()
    ) {
        val itemWidth = maxWidth / items.size
        val pillOffsetX by animateDpAsState(
            targetValue = itemWidth * selectedIndex + (itemWidth - pillWidth) / 2,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "pill"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(36.dp, RoundedCornerShape(cornerRadius), clip = false,
                    ambientColor = shadowColor, spotColor = shadowColor)
                .clip(RoundedCornerShape(cornerRadius))
                .drawWithContent {
                    val w = size.width; val h = size.height
                    drawRect(glassBase)
                    drawRect(Brush.verticalGradient(listOf(topSheen, Color.Transparent, bottomTint)))
                    drawRect(Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = if (isDark) 0.04f else 0.18f), Color.Transparent),
                        center = Offset(w / 2f, h / 2f), radius = w * 0.55f
                    ))
                    drawContent()
                    val sw = w * 0.30f; val sx = -sw + (w + sw * 2f) * sweep
                    drawRect(Brush.linearGradient(
                        colors = listOf(Color.Transparent, shimmer, shimmer.copy(alpha = shimmer.alpha * 0.45f), Color.Transparent),
                        start = Offset(sx, 0f), end = Offset(sx + sw, h)
                    ))
                    val specAlpha = borderBright.alpha * specular
                    drawLine(
                        brush = Brush.horizontalGradient(listOf(
                            Color.Transparent,
                            borderBright.copy(alpha = specAlpha * 0.5f),
                            borderBright.copy(alpha = specAlpha),
                            borderBright.copy(alpha = specAlpha),
                            borderBright.copy(alpha = specAlpha * 0.5f),
                            Color.Transparent
                        )),
                        start = Offset(0f, 0.8f), end = Offset(w, 0.8f), strokeWidth = 1.5f
                    )
                    val bs = 1.4f
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(borderBright.copy(alpha = borderBright.alpha * specular), borderDim)),
                        topLeft = Offset(bs / 2f, bs / 2f),
                        size = Size(w - bs, h - bs),
                        cornerRadius = CornerRadius(cornerRadius.toPx()),
                        style = Stroke(width = bs)
                    )
                }
        ) {
            Box(
                modifier = Modifier
                    .padding(vertical = (barHeight - pillHeight) / 2)
                    .offset(x = pillOffsetX)
                    .width(pillWidth)
                    .height(pillHeight)
                    .clip(RoundedCornerShape(14.dp))
                    .drawWithContent {
                        val w = size.width; val h = size.height
                        val pillFill = if (isDark) Color(0xFF5FA3FF).copy(alpha = 0.28f) else primary.copy(alpha = 0.14f)
                        drawRect(pillFill)
                        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (isDark) 0.18f else 0.42f), Color.Transparent)))
                        drawContent()
                        val ps = 1.0f
                        drawRoundRect(
                            brush = Brush.verticalGradient(listOf(
                                Color.White.copy(alpha = if (isDark) 0.28f else 0.65f),
                                Color.White.copy(alpha = if (isDark) 0.06f else 0.12f)
                            )),
                            topLeft = Offset(ps / 2f, ps / 2f),
                            size = Size(w - ps, h - ps),
                            cornerRadius = CornerRadius(14.dp.toPx()),
                            style = Stroke(width = ps)
                        )
                    }
            )
            Row(
                modifier = Modifier.fillMaxWidth().height(barHeight),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, entry ->
                    GlassNavItem(
                        icon = entry.icon,
                        label = stringResource(entry.labelRes),
                        selected = selectedIndex == index,
                        onClick = { navController.navigate(entry.route) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val iconColor by animateColorAsState(
        targetValue = when {
            selected && isDark  -> Color.White
            selected && !isDark -> MaterialTheme.colorScheme.primary
            !selected && isDark -> Color.White.copy(alpha = 0.45f)
            else                -> Color(0xFF2D4A6B)
        },
        animationSpec = tween(200),
        label = "navColor"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "glow"
    )
    val scale by animateFloatAsState(
        targetValue = when { isPressed -> 0.78f; selected -> 1.14f; else -> 1.00f },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "scale"
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(36.dp).scale(scale).drawBehind {
                if (glowAlpha > 0f) {
                    drawCircle(brush = Brush.radialGradient(
                        colors = listOf(
                            iconColor.copy(alpha = 0.30f * glowAlpha),
                            iconColor.copy(alpha = 0.10f * glowAlpha),
                            Color.Transparent
                        ),
                        radius = size.minDimension * 0.8f
                    ))
                }
            }
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = iconColor, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = iconColor
            )
        )
    }
}

@Preview(showBackground = true, name = "Bottom Navigation - Light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Bottom Navigation - Dark")
@Composable
fun BottomNavigationBarPreview() {
    DeDupTheme { BottomNavigationBar(navController = rememberNavController()) }
}
