package com.rp.dedup.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.rp.dedup.core.model.AppPalette

// ── Ocean (default blue) ───────────────────────────────────────────────────
private val OceanDark = darkColorScheme(
    primary            = PrimaryBlue,
    secondary          = LightCyan,
    tertiary           = Pink80,
    background         = Color(0xFF121212),
    surface            = Color(0xFF1E1E1E),
    surfaceVariant     = Color(0xFF2C2C2C),
    onPrimary          = Color.White,
    onSecondary        = Color.Black,
    onBackground       = Color.White,
    onSurface          = Color.White,
    primaryContainer   = Color(0xFF003580),
    onPrimaryContainer = Color(0xFFD8E6FF)
)

private val OceanLight = lightColorScheme(
    primary            = PrimaryBlue,
    secondary          = DarkCyan,
    tertiary           = Pink40,
    background         = Color.White,
    surface            = Color.White,
    surfaceVariant     = CardBackground,
    onPrimary          = Color.White,
    onSecondary        = Color.White,
    onBackground       = TextDark,
    onSurface          = TextDark,
    primaryContainer   = Color(0xFFD8E6FF),
    onPrimaryContainer = Color(0xFF001945)
)

// ── Midnight (Indigo / Deep Purple) ───────────────────────────────────────
private val MidnightDark = darkColorScheme(
    primary            = MidnightPrimary,
    secondary          = MidnightSecondary,
    tertiary           = MidnightTertiary,
    background         = MidnightDarkBg,
    surface            = MidnightDarkSurface,
    surfaceVariant     = MidnightDarkVariant,
    onPrimary          = Color.White,
    onSecondary        = Color.Black,
    onBackground       = Color.White,
    onSurface          = Color.White,
    primaryContainer   = Color(0xFF3D2080),
    onPrimaryContainer = Color(0xFFE8DEFF)
)

private val MidnightLight = lightColorScheme(
    primary            = MidnightPrimaryLight,
    secondary          = MidnightPrimary,
    tertiary           = RosePrimary,
    background         = MidnightLightBg,
    surface            = Color.White,
    surfaceVariant     = MidnightLightVariant,
    onPrimary          = Color.White,
    onSecondary        = Color.White,
    onBackground       = Color(0xFF1C1433),
    onSurface          = Color(0xFF1C1433),
    primaryContainer   = MidnightLightVariant,
    onPrimaryContainer = Color(0xFF1C1433)
)

// ── Forest (Deep Green) ───────────────────────────────────────────────────
private val ForestDark = darkColorScheme(
    primary            = ForestPrimaryDark,
    secondary          = Color(0xFF81C784),
    tertiary           = Color(0xFFAED581),
    background         = ForestDarkBg,
    surface            = ForestDarkSurface,
    surfaceVariant     = ForestDarkVariant,
    onPrimary          = Color.Black,
    onSecondary        = Color.Black,
    onBackground       = Color.White,
    onSurface          = Color.White,
    primaryContainer   = Color(0xFF1A3D1A),
    onPrimaryContainer = Color(0xFFB9F0B9)
)

private val ForestLight = lightColorScheme(
    primary            = ForestPrimary,
    secondary          = ForestSecondary,
    tertiary           = ForestTertiary,
    background         = ForestLightBg,
    surface            = Color.White,
    surfaceVariant     = ForestLightVariant,
    onPrimary          = Color.White,
    onSecondary        = Color.White,
    onBackground       = Color(0xFF1B2B1B),
    onSurface          = Color(0xFF1B2B1B),
    primaryContainer   = ForestLightVariant,
    onPrimaryContainer = Color(0xFF1B2B1B)
)

// ── Sunset (Deep Orange / Amber) ──────────────────────────────────────────
private val SunsetDark = darkColorScheme(
    primary            = SunsetPrimaryDark,
    secondary          = Color(0xFFFF8F00),
    tertiary           = Color(0xFFFFD600),
    background         = SunsetDarkBg,
    surface            = SunsetDarkSurface,
    surfaceVariant     = SunsetDarkVariant,
    onPrimary          = Color.Black,
    onSecondary        = Color.Black,
    onBackground       = Color.White,
    onSurface          = Color.White,
    primaryContainer   = Color(0xFF4A1800),
    onPrimaryContainer = Color(0xFFFFDBCC)
)

private val SunsetLight = lightColorScheme(
    primary            = SunsetPrimary,
    secondary          = SunsetSecondary,
    tertiary           = SunsetTertiary,
    background         = SunsetLightBg,
    surface            = Color.White,
    surfaceVariant     = SunsetLightVariant,
    onPrimary          = Color.White,
    onSecondary        = Color.White,
    onBackground       = Color(0xFF2A1000),
    onSurface          = Color(0xFF2A1000),
    primaryContainer   = SunsetLightVariant,
    onPrimaryContainer = Color(0xFF2A1000)
)

// ── Rose (Deep Pink / Mauve) ──────────────────────────────────────────────
private val RoseDark = darkColorScheme(
    primary            = RosePrimaryDark,
    secondary          = Color(0xFFEC407A),
    tertiary           = Color(0xFFFF80AB),
    background         = RoseDarkBg,
    surface            = RoseDarkSurface,
    surfaceVariant     = RoseDarkVariant,
    onPrimary          = Color.Black,
    onSecondary        = Color.Black,
    onBackground       = Color.White,
    onSurface          = Color.White,
    primaryContainer   = Color(0xFF4A0030),
    onPrimaryContainer = Color(0xFFFFD8EC)
)

private val RoseLight = lightColorScheme(
    primary            = RosePrimary,
    secondary          = RoseSecondary,
    tertiary           = RoseTertiary,
    background         = RoseLightBg,
    surface            = Color.White,
    surfaceVariant     = RoseLightVariant,
    onPrimary          = Color.White,
    onSecondary        = Color.White,
    onBackground       = Color(0xFF2A0018),
    onSurface          = Color(0xFF2A0018),
    primaryContainer   = RoseLightVariant,
    onPrimaryContainer = Color(0xFF2A0018)
)

// ── Monochrome ────────────────────────────────────────────────────────────
private val MonochromeDark = darkColorScheme(
    primary            = MonoPrimaryDark,
    secondary          = MonoSecondary,
    tertiary           = MonoTertiary,
    background         = MonoDarkBg,
    surface            = MonoDarkSurface,
    surfaceVariant     = MonoDarkVariant,
    onPrimary          = Color.Black,
    onSecondary        = Color.White,
    onBackground       = Color.White,
    onSurface          = Color.White,
    primaryContainer   = Color(0xFF2E2E2E),
    onPrimaryContainer = Color(0xFFEEEEEE)
)

private val MonochromeLight = lightColorScheme(
    primary            = MonoPrimary,
    secondary          = MonoSecondary,
    tertiary           = MonoTertiary,
    background         = MonoLightBg,
    surface            = Color.White,
    surfaceVariant     = MonoLightVariant,
    onPrimary          = Color.White,
    onSecondary        = Color.White,
    onBackground       = Color(0xFF1A1A1A),
    onSurface          = Color(0xFF1A1A1A),
    primaryContainer   = MonoLightVariant,
    onPrimaryContainer = Color(0xFF1A1A1A)
)

internal fun paletteColorScheme(palette: AppPalette, dark: Boolean): ColorScheme = when (palette) {
    AppPalette.OCEAN      -> if (dark) OceanDark       else OceanLight
    AppPalette.MIDNIGHT   -> if (dark) MidnightDark    else MidnightLight
    AppPalette.FOREST     -> if (dark) ForestDark      else ForestLight
    AppPalette.SUNSET     -> if (dark) SunsetDark      else SunsetLight
    AppPalette.ROSE       -> if (dark) RoseDark        else RoseLight
    AppPalette.MONOCHROME -> if (dark) MonochromeDark  else MonochromeLight
}

@Composable
fun DeDupTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: AppPalette = AppPalette.OCEAN,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> paletteColorScheme(palette, darkTheme)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}
