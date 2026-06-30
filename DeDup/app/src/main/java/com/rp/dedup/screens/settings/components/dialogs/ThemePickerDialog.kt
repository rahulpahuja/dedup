package com.rp.dedup.screens.settings.components.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rp.dedup.core.model.AppPalette
import com.rp.dedup.core.model.ThemeMode

data class PaletteOption(
    val palette:   AppPalette,
    val name:      String,
    val primary:   Color,
    val secondary: Color,
    val accent:    Color
)

val PALETTE_OPTIONS = listOf(
    PaletteOption(AppPalette.OCEAN,      "Ocean",    Color(0xFF0056D2), Color(0xFF00838F), Color(0xFF80DEEA)),
    PaletteOption(AppPalette.MIDNIGHT,   "Midnight", Color(0xFF7C4DFF), Color(0xFF9C8FFF), Color(0xFFCF6679)),
    PaletteOption(AppPalette.FOREST,     "Forest",   Color(0xFF2E7D32), Color(0xFF388E3C), Color(0xFF81C784)),
    PaletteOption(AppPalette.SUNSET,     "Sunset",   Color(0xFFE65100), Color(0xFFEF6C00), Color(0xFFFF8F00)),
    PaletteOption(AppPalette.ROSE,       "Rose",     Color(0xFFAD1457), Color(0xFFC2185B), Color(0xFFFF80AB)),
    PaletteOption(AppPalette.MONOCHROME, "Mono",     Color(0xFF424242), Color(0xFF616161), Color(0xFF9E9E9E)),
)

@Composable
fun ThemeBadge(mode: ThemeMode, palette: AppPalette) {
    val opt = PALETTE_OPTIONS.find { it.palette == palette } ?: PALETTE_OPTIONS.first()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(opt.primary))
        Spacer(Modifier.width(6.dp))
        Surface(shape = RoundedCornerShape(8.dp), color = opt.primary.copy(alpha = 0.15f)) {
            Text(
                text = opt.name,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelMedium.copy(color = opt.primary, fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
fun ThemePickerDialog(
    currentMode:     ThemeMode,
    currentPalette:  AppPalette,
    onDismiss:       () -> Unit,
    onSelectMode:    (ThemeMode) -> Unit,
    onSelectPalette: (AppPalette) -> Unit
) {
    val modeOptions = listOf(
        Triple(ThemeMode.LIGHT, Icons.Default.LightMode,          "Light"),
        Triple(ThemeMode.DARK,  Icons.Default.DarkMode,           "Dark"),
        Triple(ThemeMode.AUTO,  Icons.Default.SettingsBrightness, "Auto")
    )

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Appearance",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface)
                Text("Personalize how DeDup looks and feels",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(Modifier.height(24.dp))

                Text("MODE", style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant))
                Spacer(Modifier.height(10.dp))

                Surface(shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                        modeOptions.forEachIndexed { index, (mode, icon, label) ->
                            val selected = currentMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f).fillMaxHeight()
                                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { onSelectMode(mode) }
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(icon, contentDescription = label,
                                        tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.height(4.dp))
                                    Text(label, style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant))
                                }
                            }
                            if (index < modeOptions.lastIndex) {
                                Box(modifier = Modifier.width(1.dp).fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.outlineVariant))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                Text("COLOR PALETTE", style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant))
                Spacer(Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(PALETTE_OPTIONS.take(3), PALETTE_OPTIONS.drop(3)).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { opt ->
                                PaletteSwatchCard(
                                    option = opt,
                                    selected = currentPalette == opt.palette,
                                    onClick = { onSelectPalette(opt.palette) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Done", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun PaletteSwatchCard(option: PaletteOption, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.aspectRatio(0.88f).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) option.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(if (selected) 2.dp else 1.dp,
            if (selected) option.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(option.primary, option.secondary, option.accent).forEach { color ->
                    Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(color))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(option.name, style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) option.primary else MaterialTheme.colorScheme.onSurfaceVariant))
            if (selected) {
                Spacer(Modifier.height(4.dp))
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = option.primary, modifier = Modifier.size(14.dp))
            }
        }
    }
}
