package com.trainpaths.nonogram.dialogs

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun PlayConfirmDialog(
    difficulty: String,
    beatCount: Long,
    hasProgress: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onShow: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasProgress) "Continue?" else "Play?") },
        text = { Text("Difficulty: ${difficulty.lowercase()}\nBeaten: ${beatCount}x") },

        confirmButton = {
            Row(modifier = Modifier.fillMaxWidth()) {
                if (beatCount > 0) {
                    TextButton(onClick = onShow) { Text("Show") }
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("No") }
                TextButton(onClick = onConfirm) { Text("Yes") }
            }
        },
    )
}
