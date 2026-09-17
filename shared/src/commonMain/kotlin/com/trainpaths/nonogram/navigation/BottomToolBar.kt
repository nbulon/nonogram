package com.trainpaths.nonogram.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.trainpaths.nonogram.MAX_CONTENT_WIDTH
import com.trainpaths.nonogram.hasMouseAndKeyboard
import com.trainpaths.nonogram.classes.BoardHistory
import com.trainpaths.nonogram.classes.DrawMode
import com.trainpaths.nonogram.icons.lockClosed
import com.trainpaths.nonogram.icons.lockOpen
import com.trainpaths.nonogram.icons.redo
import com.trainpaths.nonogram.icons.save
import com.trainpaths.nonogram.icons.searchCheck
import com.trainpaths.nonogram.icons.tileCross
import com.trainpaths.nonogram.icons.tileErase
import com.trainpaths.nonogram.icons.tileFill
import com.trainpaths.nonogram.icons.undo
import com.trainpaths.nonogram.tutorial.TutorialStep
import com.trainpaths.nonogram.tutorial.tutorialAnchor

/** An icon-only button in a titled group: wide enough for the 28.dp icon and its highlight pill, no more. */
private val ICON_ITEM_WIDTH = 38.dp

/** A button that labels itself needs room for a word. Shrinks below this only to avoid overflowing. */
private val MAX_ITEM_WIDTH = 64.dp

/** Gap between groups. Items *inside* a group always sit flush, so this is the only thing that reads as grouping. */
private val MIN_GROUP_GAP = 14.dp
private val MAX_GROUP_GAP = 48.dp

/** Inside the clickable, so it grows the touch target rather than just insetting the icon. */
private val ITEM_VERTICAL_PADDING = 8.dp

@Composable
fun BottomToolBar(
    isLocked: Boolean,
    onLockToggle: () -> Unit,
    drawMode: DrawMode,
    onDrawModeSelect: (DrawMode) -> Unit,
    history: BoardHistory? = null,
    onSave: (() -> Unit)? = null,
    saveEnabled: Boolean = true,
    onCheck: (() -> Unit)? = null,
    checkEnabled: Boolean = true,
    checkTint: Color? = null,
    checkTutorialStep: TutorialStep? = null,
) {
    Box(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondary),
        contentAlignment = Alignment.BottomCenter,
    ) {
        BottomAppBar(
            modifier = Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxWidth(),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSecondary,
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            val showPencil = !hasMouseAndKeyboard
            val iconOnlyCount = (if (showPencil) DrawMode.entries.size else 0) +
                    (if (history != null) 2 else 0)
            val labelledCount = 1 + // lock
                    (if (onSave != null) 1 else 0) +
                    (if (onCheck != null) 1 else 0)

            val groupCount = 1 + (if (showPencil) 1 else 0) + (if (history != null) 1 else 0)
            val gapCount = groupCount - 1

            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val forLabelled = maxWidth - MIN_GROUP_GAP * gapCount - ICON_ITEM_WIDTH * iconOnlyCount
                val labelledWidth = (forLabelled / labelledCount).coerceAtMost(MAX_ITEM_WIDTH)
                val used = ICON_ITEM_WIDTH * iconOnlyCount + labelledWidth * labelledCount
                val groupGap = if (gapCount == 0) 0.dp
                else ((maxWidth - used) / gapCount).coerceIn(MIN_GROUP_GAP, MAX_GROUP_GAP)

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(groupGap, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showPencil) {
                        ToolGroup(title = "Pencil", tutorialStep = TutorialStep.BOARD_DRAW_MODE) {
                            DrawMode.entries.forEach { mode ->
                                BottomBarItem(
                                    label = null,
                                    imageVector = mode.icon,
                                    contentDescription = "Draw mode: ${mode.label}",
                                    onClick = { onDrawModeSelect(mode) },
                                    width = ICON_ITEM_WIDTH,
                                    selected = drawMode == mode,
                                )
                            }
                        }
                    }

                    if (history != null) {
                        ToolGroup(title = "History", tutorialStep = TutorialStep.BOARD_UNDO) {
                            BottomBarItem(
                                label = null,
                                imageVector = undo,
                                contentDescription = "Undo",
                                onClick = { history.undo() },
                                width = ICON_ITEM_WIDTH,
                                enabled = history.canUndo,
                            )
                            BottomBarItem(
                                label = null,
                                imageVector = redo,
                                contentDescription = "Redo",
                                onClick = { history.redo() },
                                width = ICON_ITEM_WIDTH,
                                enabled = history.canRedo,
                            )
                        }
                    }

                    ToolGroup {
                        BottomBarItem(
                            label = if (isLocked) "Lock" else "Unlock",
                            imageVector = if (isLocked) lockClosed else lockOpen,
                            contentDescription = if (isLocked) "Locked" else "Unlocked",
                            onClick = onLockToggle,
                            width = labelledWidth,
                            tutorialStep = TutorialStep.BOARD_LOCK,
                        )

                        if (onSave != null) {
                            BottomBarItem(
                                label = "Save",
                                imageVector = save,
                                contentDescription = "Save nonogram",
                                onClick = onSave,
                                width = labelledWidth,
                                enabled = saveEnabled,
                                tutorialStep = TutorialStep.GEN_SAVE,
                            )
                        }

                        if (onCheck != null) {
                            BottomBarItem(
                                label = "Check",
                                imageVector = searchCheck,
                                contentDescription = "Check board for mistakes",
                                onClick = onCheck,
                                width = labelledWidth,
                                enabled = checkEnabled,
                                tint = checkTint,
                                tutorialStep = checkTutorialStep,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val DrawMode.label: String
    get() = when (this) {
        DrawMode.FILL -> "Fill"
        DrawMode.CROSS -> "Cross"
        DrawMode.ERASE -> "Erase"
    }

private val DrawMode.icon: ImageVector
    get() = when (this) {
        DrawMode.FILL -> tileFill
        DrawMode.CROSS -> tileCross
        DrawMode.ERASE -> tileErase
    }

@Composable
private fun ToolGroup(
    title: String? = null,
    tutorialStep: TutorialStep? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.tutorialAnchor(tutorialStep),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            content()
        }
        if (title != null) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BottomBarItem(
    /** null when the enclosing [ToolGroup] carries a shared title instead. */
    label: String?,
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    width: Dp,
    enabled: Boolean = true,
    /** null for a plain action; true/false marks the item as one option of a selectable group. */
    selected: Boolean? = null,
    /** Replaces the normal content colour while enabled */
    tint: Color? = null,
    tutorialStep: TutorialStep? = null,
) {
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.50f)
        else -> tint ?: MaterialTheme.colorScheme.onSecondary
    }
    val highlight = if (selected == true) {
        Color.White.copy(alpha = 0.28f)
    } else {
        Color.Transparent
    }

    Column(
        modifier = Modifier
            .width(width)
            .tutorialAnchor(tutorialStep)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = ITEM_VERTICAL_PADDING)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                if (selected != null) this.selected = selected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .background(highlight, MaterialTheme.shapes.small)
                .padding(horizontal = 4.dp, vertical = 3.dp),
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(28.dp),
            )
        }
        if (label != null) {
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
