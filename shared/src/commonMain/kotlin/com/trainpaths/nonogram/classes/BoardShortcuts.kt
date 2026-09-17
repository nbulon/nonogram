package com.trainpaths.nonogram.classes

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import com.trainpaths.nonogram.classes.BoardTrigger.KeyPress
import com.trainpaths.nonogram.classes.BoardTrigger.MouseButton

/** What fires a [BoardShortcut]. Every trigger names itself for the tutorial copy. */
sealed interface BoardTrigger {
    val label: String

    data class KeyPress(val key: Key, override val label: String) : BoardTrigger

    enum class MouseButton(override val label: String) : BoardTrigger {
        PRIMARY("Left-click"),
        SECONDARY("Right-click"),
    }
}

/**
 * The board's mouse buttons and keyboard shortcuts — the single table behind the key handler, the
 * Settings keybinds dialog and the tutorial hints. The mouse rows only describe: the gesture layer
 * reads the button itself ([mouseDrawMode]).
 */
enum class BoardShortcut(val trigger: BoardTrigger, val label: String) {
    DRAW(MouseButton.PRIMARY, "Toggle fill / cross"),
    ERASE(MouseButton.SECONDARY, "Erase"),
    LOCK(KeyPress(Key.A, "A"), "Lock/unlock board"),
    CHECK(KeyPress(Key.S, "S"), "Check board"),
    UNDO(KeyPress(Key.D, "D"), "Undo"),
    REDO(KeyPress(Key.F, "F"), "Redo"),
}

/**
 * Drives the bottom bar's actions from [BoardShortcut] keys. Goes on the screen's root, which takes
 * focus on entry: key events bubble up from whichever descendant holds focus, so a bottom-bar
 * button clicked with the mouse does not swallow them, while a dialog — its own focus root — does.
 */
@Composable
fun Modifier.boardShortcuts(
    onLockToggle: () -> Unit,
    onCheck: (() -> Unit)?,
    history: BoardHistory?,
): Modifier {
    val focusRequester = remember { FocusRequester() }
    val currentOnLockToggle = rememberUpdatedState(onLockToggle)
    val currentOnCheck = rememberUpdatedState(onCheck)
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    return focusRequester(focusRequester)
        .focusable()
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
            if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return@onKeyEvent false
            when (BoardShortcut.entries.firstOrNull { (it.trigger as? KeyPress)?.key == event.key }) {
                BoardShortcut.LOCK -> currentOnLockToggle.value()
                BoardShortcut.CHECK -> currentOnCheck.value?.invoke()
                BoardShortcut.UNDO -> history?.undo()
                BoardShortcut.REDO -> history?.redo()
                BoardShortcut.DRAW, BoardShortcut.ERASE, null -> return@onKeyEvent false
            }
            true
        }
}
