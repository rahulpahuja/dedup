package com.rp.dedup.screens.settings.components.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.rp.dedup.R

@Composable
fun FeedbackDialog(
    title: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var text by rememberSaveable { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(placeholder) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    trailingIcon = {
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.Mic, contentDescription = stringResource(R.string.voice_input))
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Button(onClick = { onSubmit(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.submit)) }
                }
            }
        }
    }
}
