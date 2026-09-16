package com.trainpaths.nonogram.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun DeleteConfirmDialog(
    isPublic: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Delete nonogram?") },
        text = {
            Text(
                if (isPublic) {
                    "This nonogram is public. Deleting it removes it from the puzzle list.\nThis cannot be undone."
                } else {
                    "This cannot be undone."
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    )
}
