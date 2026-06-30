package com.rp.dedup.screens.settings.components.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.rp.dedup.R

@Composable
fun ThresholdPickerDialog(currentValue: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Image Similarity", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text(
                    "Lower bits mean more strict (only very similar images). Higher bits mean more relaxed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
                var sliderValue by remember { mutableStateOf(currentValue.toFloat()) }
                Slider(value = sliderValue, onValueChange = { sliderValue = it }, valueRange = 0f..20f, steps = 19)
                Text(
                    text = stringResource(R.string.bits, sliderValue.toInt()),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Button(onClick = { onSelect(sliderValue.toInt()) }) { Text("Save") }
                }
            }
        }
    }
}
