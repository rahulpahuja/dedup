package com.rp.dedup.screens.dashboard.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rp.dedup.R
import com.rp.dedup.ui.theme.DeDupTheme

@Composable
internal fun TutorialTooltip(title: String, body: String) {
    Column(modifier = Modifier.width(260.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.80f)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.tap_anywhere_continue),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF5FA3FF)
        )
    }
}

// Text color is hardcoded (white/blue) regardless of theme, since this only ever renders on
// the IntroShowcase coach-mark scrim (see DashboardScreen's `tutorialStyle`, backgroundColor
// 0xFF090F20) — the preview reproduces that same dark backdrop so the text stays legible.
@Preview(showBackground = true, backgroundColor = 0xFF090F20, name = "Light Mode")
@Preview(showBackground = true, backgroundColor = 0xFF090F20, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
private fun TutorialTooltipPreview() {
    DeDupTheme {
        Box(modifier = Modifier.padding(24.dp)) {
            TutorialTooltip(
                title = "Quick Scan",
                body = "Tap here to instantly scan your device for duplicate photos and videos."
            )
        }
    }
}
