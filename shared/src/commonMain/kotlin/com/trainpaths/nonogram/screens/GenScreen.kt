package com.trainpaths.nonogram.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.trainpaths.nonogram.navigation.AppBarMode
import com.trainpaths.nonogram.navigation.BottomToolBar
import com.trainpaths.nonogram.navigation.TopAppBar
import com.trainpaths.nonogram.classes.Board
import com.trainpaths.nonogram.classes.BoardTransformState
import com.trainpaths.nonogram.classes.DrawMode
import com.trainpaths.nonogram.classes.boardShortcuts
import com.trainpaths.nonogram.dialogs.GenSaveConfirmDialog
import com.trainpaths.nonogram.dialogs.PublicEditConfirmDialog
import com.trainpaths.nonogram.screens.viewModel.GenViewModel
import com.trainpaths.nonogram.screens.viewModel.ValidationState
import com.trainpaths.nonogram.tutorial.TutorialStep

@Composable
fun GenScreen(
    genViewModel: GenViewModel,
    onConfig: () -> Unit,
    onExitToList: () -> Unit,
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    var pendingPublicSave by remember { mutableStateOf<(() -> Unit)?>(null) }
    var isLocked by remember { mutableStateOf(true) }
    var drawMode by remember { mutableStateOf(DrawMode.FILL) }
    val boardState = remember(genViewModel.width, genViewModel.height) { BoardTransformState() }

    fun requestSave(save: () -> Unit) {
        if (genViewModel.needsPublicEditConfirmation()) {
            pendingPublicSave = save
        } else {
            save()
        }
    }

    val attemptLeave = {
        if (!genViewModel.isSaving) {
            if (genViewModel.isDirty) showSaveDialog = true else onExitToList()
        }
    }

    val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(state = backState) { attemptLeave() }

    val onLockToggle = { isLocked = !isLocked }
    val checkEnabled = !genViewModel.isSaving && genViewModel.validationState != ValidationState.CHECKING
    val onCheck = {
        genViewModel.checkBoard()
        boardState.reset()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .boardShortcuts(
                onLockToggle = onLockToggle,
                onCheck = if (checkEnabled) onCheck else null,
                history = genViewModel.history,
            ),
    ) {
        TopAppBar(
            onBack = { if (!genViewModel.isSaving) requestSave { onConfig() } },
            showSettings = true,
            mode = AppBarMode.GENERATOR,
            onSwapMode = { attemptLeave() },
        )

        val nonogram = genViewModel.nonogram
        if (genViewModel.tiles.isNotEmpty()) {
            Board(
                nonogram = nonogram,
                tiles = genViewModel.tiles,
                isLocked = isLocked,
                modifier = Modifier.fillMaxWidth().weight(1f),
                isEditable = !genViewModel.isSaving,
                drawMode = drawMode,
                state = boardState,
                onTilesChanged = { genViewModel.updateNonogram() },
                onEdits = genViewModel.history::record,
            )
        } else {
            // Keep the bottom app bar anchored while the board is empty.
            Spacer(modifier = Modifier.weight(1f))
        }

        BottomToolBar(
            isLocked = isLocked,
            onLockToggle = onLockToggle,
            drawMode = drawMode,
            onDrawModeSelect = { drawMode = it },
            history = genViewModel.history,
            saveEnabled = genViewModel.canSave,
            onSave = { requestSave { genViewModel.onSave() } },
            onCheck = onCheck,
            checkEnabled = checkEnabled,
            checkTint = when (genViewModel.validationState) {
                ValidationState.VALID -> MaterialTheme.colorScheme.onTertiary
                ValidationState.INVALID -> MaterialTheme.colorScheme.tertiaryFixed
                else -> null
            },
            checkTutorialStep = TutorialStep.GEN_CHECK,
        )
    }

    if (showSaveDialog) {
        GenSaveConfirmDialog(
            onSave = {
                showSaveDialog = false
                requestSave { genViewModel.onSave { onExitToList() } }
            },
            onDiscard = {
                showSaveDialog = false
                onExitToList()
            },
            onCancel = { showSaveDialog = false },
        )
    }

    pendingPublicSave?.let { save ->
        PublicEditConfirmDialog(
            onConfirm = {
                pendingPublicSave = null
                save()
            },
            onCancel = { pendingPublicSave = null },
        )
    }
}
