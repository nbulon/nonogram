package com.trainpaths.nonogram.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun PlayConfirmDialog(
    difficulty: String,
    beatCount: Long,
    hasProgress: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasProgress) "Continue?" else "Play?") },
        text = { Text("Difficulty: ${difficulty.lowercase()}\nBeaten: ${beatCount}x") },

        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Yes") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("No") }
        }
    )
}
