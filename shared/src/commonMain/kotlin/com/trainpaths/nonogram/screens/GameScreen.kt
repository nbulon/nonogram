package com.trainpaths.nonogram.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.trainpaths.nonogram.navigation.AppBarMode
import com.trainpaths.nonogram.navigation.BottomToolBar
import com.trainpaths.nonogram.navigation.TopAppBar
import com.trainpaths.nonogram.screens.viewModel.GameViewModel
import com.trainpaths.nonogram.classes.Board
import com.trainpaths.nonogram.classes.BoardTransformState
import com.trainpaths.nonogram.classes.DrawMode
import com.trainpaths.nonogram.classes.boardShortcuts
import com.trainpaths.nonogram.classes.toSolutionInts
import com.trainpaths.nonogram.tutorial.TutorialStep
import com.trainpaths.nonogram.tutorial.tutorialAnchor

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    onBack: () -> Unit,
    onWin: () -> Unit,
    onSwapMode: () -> Unit,
) {
    var isLocked by remember { mutableStateOf(true) }
    var drawMode by remember { mutableStateOf(DrawMode.FILL) }

    val nonogram = viewModel.nonogram
    val tiles = viewModel.tiles
    // Hoisted so Check can fit the board again alongside marking mistakes.
    val boardState = remember(nonogram?.width, nonogram?.height) { BoardTransformState() }
    val onLockToggle = { isLocked = !isLocked }
    val onCheck = {
        viewModel.checkBoard()
        boardState.reset()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .boardShortcuts(onLockToggle = onLockToggle, onCheck = onCheck, history = viewModel.history),
    ) {
        TopAppBar(
            onBack = onBack,
            showSettings = true,
            mode = AppBarMode.PUZZLE,
            onSwapMode = onSwapMode,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .tutorialAnchor(TutorialStep.BOARD_AREA),
            contentAlignment = Alignment.Center,
        ) {
            if (nonogram == null) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Board(
                    nonogram = nonogram,
                    tiles = tiles,
                    isLocked = isLocked,
                    modifier = Modifier.fillMaxSize(),
                    drawMode = drawMode,
                    strikeSolvedClues = true,
                    state = boardState,
                    onTilesChanged = { if (tiles.toSolutionInts() == nonogram.solution) onWin() },
                    onEdits = viewModel.history::record,
                )
            }
        }

        BottomToolBar(
            isLocked = isLocked,
            onLockToggle = onLockToggle,
            drawMode = drawMode,
            onDrawModeSelect = { drawMode = it },
            history = viewModel.history,
            onCheck = onCheck,
            checkTutorialStep = TutorialStep.BOARD_CHECK,
        )
    }
}
