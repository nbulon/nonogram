package com.trainpaths.nonogram.screens.viewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.trainpaths.nonogram.AppSDK
import com.trainpaths.nonogram.auth.AuthRepository
import com.trainpaths.nonogram.classes.BoardHistory
import com.trainpaths.nonogram.classes.ClueProgress
import com.trainpaths.nonogram.classes.Nonogram
import com.trainpaths.nonogram.classes.Tile
import com.trainpaths.nonogram.classes.TileEdit
import com.trainpaths.nonogram.classes.TileState
import com.trainpaths.nonogram.classes.progressIntToTileState
import com.trainpaths.nonogram.classes.toProgressInts
import com.trainpaths.nonogram.classes.toSolutionInts
import com.trainpaths.nonogram.classes.toSolutionOrNull
import com.trainpaths.nonogram.settings.SettingsRepository
import com.trainpaths.nonogram.sync.SyncService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Board changes between auto saves. */
internal const val AUTOSAVE_STROKE_INTERVAL = 10

class GameViewModel(
    private val sdk: AppSDK,
    private val authRepository: AuthRepository,
    private val syncService: SyncService,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    var nonogram: Nonogram? by mutableStateOf(null)
        private set
    var tiles: List<List<Tile>> by mutableStateOf(emptyList())
        private set
    var clueProgress: ClueProgress? by mutableStateOf(null)
        private set

    /** Whether the last committed change left the board matching the solution; the screen plays the win from it. */
    var solved: Boolean by mutableStateOf(false)
        private set

    /** [BoardHistory.onApply] fires on undo/redo, which move the board as much as a stroke does. */
    val history = BoardHistory(onApply = { afterBoardChange() })

    private var changesSinceSave = 0
    private var saveJob: Job? = null

    val currentNonogramId: Long?
        get() = nonogram?.id

    val currentProgress: List<List<Int>>
        get() = tiles.toProgressInts()

    fun loadNonogram(id: Long) {
        nonogram = null
        tiles = emptyList()
        clueProgress = null
        changesSinceSave = 0
        solved = false
        launchGuarded(onError = { println("Game: loading nonogram $id failed: ${it.message}") }) {
            val loaded: Nonogram? = sdk.getNonogramById(id)
            if (loaded != null) {
                val userUid = authRepository.currentUserUid.value
                val existingProgress: List<List<Int>>? = if (userUid != null) {
                    sdk.getSingleProgress(userUid, id)
                        ?.boardState
                        ?.toSolutionOrNull()
                        ?.takeIf { it.size == loaded.height && it.all { row -> row.size == loaded.width } }
                } else null

                nonogram = loaded
                tiles = existingProgress?.map { row ->
                    row.map { value -> Tile().apply { state = progressIntToTileState(value) } }
                }
                    ?: List(loaded.height) { List(loaded.width) { Tile() } }
                clueProgress = ClueProgress(tiles, loaded.rowClues, loaded.colClues)
                history.reset(tiles)
            }
        }
    }

    /** One completed stroke or tap from the board: journalled, and counted toward the next autosave. */
    fun recordEdits(edits: List<TileEdit>) {
        if (edits.isEmpty()) return
        // Recorded with the stroke that caused them, so one undo takes both back.
        val crosses = clueProgress?.takeIf { settingsRepository.autoCrossLines.value }?.autoCross(edits).orEmpty()
        history.record(edits + crosses)
        afterBoardChange()
    }

    private fun afterBoardChange() {
        solved = nonogram?.solution == tiles.toSolutionInts()
        if (solved) return
        if (++changesSinceSave < AUTOSAVE_STROKE_INTERVAL) return
        saveCurrentProgress(pushRemote = false)
    }

    /** Saves locally only if something changed since the last save. */
    fun flushProgress() {
        if (changesSinceSave == 0) return
        saveCurrentProgress(pushRemote = false)
    }

    /** Writes the board out, and resets the autosave counter. [pushRemote] is off for autosaves */
    fun saveCurrentProgress(win: Boolean = false, pushRemote: Boolean = true) {
        changesSinceSave = 0
        val userUid = authRepository.currentUserUid.value.orMissing() ?: return
        val nonogramId = nonogram?.id ?: return
        val board = currentProgress
        val previous = saveJob
        saveJob = launchGuarded(
            Dispatchers.Default,
            onError = { println("Game: saving progress for $nonogramId failed: ${it.message}") },
        ) {
            withContext(NonCancellable) {
                previous?.join()
                if (win) {
                    sdk.saveProgressAfterWin(userUid, nonogramId)
                } else {
                    sdk.saveProgress(userUid, nonogramId, board)
                }
            }

            if (!pushRemote) return@launchGuarded
            val firebaseUid = authRepository.currentFirebaseUid.orMissing() ?: return@launchGuarded
            val progress = sdk.getSingleProgress(userUid, nonogramId) ?: return@launchGuarded
            syncService.pushProgress(firebaseUid, nonogramId, progress.boardState, progress.updatedAt)
        }
    }

    /**
     * Marks every tile that contradicts the solution.
     * Assigning a new value to a red tile clears the red
     */
    fun checkBoard() {
        val solution = nonogram?.solution ?: return
        for ((rowIndex, row) in tiles.withIndex()) {
            val solutionRow = solution.getOrNull(rowIndex)
            for ((colIndex, tile) in row.withIndex()) {
                val expected = solutionRow?.getOrNull(colIndex)
                tile.wrong = when (tile.state) {
                    TileState.FILLED -> expected == 0
                    TileState.CROSSED -> expected == 1
                    TileState.NONE -> false
                }
            }
        }
    }

    fun resetBoard() {
        val edits = mutableListOf<TileEdit>()
        for ((rowIndex, row) in tiles.withIndex()) {
            for ((colIndex, tile) in row.withIndex()) {
                if (tile.state != TileState.NONE) {
                    edits.add(TileEdit(rowIndex, colIndex, before = tile.state, after = TileState.NONE))
                    tile.state = TileState.NONE
                }
            }
        }
        history.record(edits)
        changesSinceSave = 0
        solved = false
    }
}
