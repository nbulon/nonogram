package com.trainpaths.nonogram.dialogs

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.trainpaths.nonogram.classes.BoardShortcut
import com.trainpaths.nonogram.classes.BoardTrigger
import com.trainpaths.nonogram.icons.lockClosed
import com.trainpaths.nonogram.icons.mouseLeft
import com.trainpaths.nonogram.icons.mouseRight
import com.trainpaths.nonogram.icons.redo
import com.trainpaths.nonogram.icons.searchCheck
import com.trainpaths.nonogram.icons.tileErase
import com.trainpaths.nonogram.icons.tileToggle
import com.trainpaths.nonogram.icons.undo

private val ICON_SIZE = 24.dp

/** Every [BoardShortcut] — mouse buttons and keys — for the platforms that have them. */
@Composable
fun KeybindsDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Keybinds") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BoardShortcut.entries.forEach { shortcut ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Trigger(shortcut.trigger)
                        SmallIcon(shortcut.icon)
                        Text(shortcut.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text("Close") }
        },
    )
}

@Composable
private fun Trigger(trigger: BoardTrigger) {
    when (trigger) {
        is BoardTrigger.KeyPress -> KeyCap(trigger.label)
        BoardTrigger.MouseButton.PRIMARY -> SmallIcon(mouseLeft)
        BoardTrigger.MouseButton.SECONDARY -> SmallIcon(mouseRight)
    }
}

private val BoardShortcut.icon: ImageVector
    get() = when (this) {
        BoardShortcut.DRAW -> tileToggle
        BoardShortcut.ERASE -> tileErase
        BoardShortcut.LOCK -> lockClosed
        BoardShortcut.CHECK -> searchCheck
        BoardShortcut.UNDO -> undo
        BoardShortcut.REDO -> redo
    }

@Composable
private fun SmallIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(ICON_SIZE))
}

@Composable
private fun KeyCap(label: String) {
    Box(
        modifier = Modifier
            .size(ICON_SIZE)
            .border(1.dp, MaterialTheme.colorScheme.onSurface, MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
