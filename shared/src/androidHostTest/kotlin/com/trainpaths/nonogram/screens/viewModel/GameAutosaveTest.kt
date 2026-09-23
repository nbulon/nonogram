package com.trainpaths.nonogram.screens.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russhwolf.settings.MapSettings
import com.trainpaths.nonogram.AppSDK
import com.trainpaths.nonogram.TestDatabaseFactory
import com.trainpaths.nonogram.auth.AuthRepository
import com.trainpaths.nonogram.classes.Difficulty
import com.trainpaths.nonogram.classes.Nonogram
import com.trainpaths.nonogram.classes.PublishStatus
import com.trainpaths.nonogram.classes.TileEdit
import com.trainpaths.nonogram.classes.TileState
import com.trainpaths.nonogram.sync.ModerationGate
import com.trainpaths.nonogram.sync.SyncService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Leaving the app used to lose the board, so the board is written out every few changes. The count
 * covers anything that moves a tile — strokes, taps, undo and redo — and an autosave stays local:
 * the next `syncAll` pushes the local-newer row.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameAutosaveTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var sdk: AppSDK
    private lateinit var authRepository: AuthRepository
    private lateinit var service: RecordingSyncService

    private val uid = "player-1"
    private val puzzleId = 7L

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        sdk = AppSDK(TestDatabaseFactory())
        authRepository = AuthRepository(sdk, MapSettings())
        service = RecordingSyncService()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun loadedViewModel(): GameViewModel {
        authRepository.initialize()
        authRepository.linkFirebaseUser(uid, "Player")
        sdk.upsertNonogramFromRemote(
            Nonogram(
                id = puzzleId, difficulty = Difficulty.EASY,
                solution = List(5) { listOf(0, 0, 0, 0, 1) },
                name = "Comet", authorUid = "", updatedAt = 100,
                publishStatus = PublishStatus.APPROVED,
            )
        )
        return GameViewModel(sdk, authRepository, service).also {
            it.loadNonogram(puzzleId)
            it.awaitIdle()
        }
    }

    /** Fills the [index]th tile the way the gesture layer does, then commits it as one stroke. */
    private fun GameViewModel.stroke(index: Int) {
        val width = assertNotNull(nonogram).width
        val row = index / width
        val col = index % width
        val tile = tiles[row][col]
        val before = tile.state
        tile.state = TileState.FILLED
        recordEdits(listOf(TileEdit(row, col, before = before, after = TileState.FILLED)))
    }

    /** One short of an autosave. */
    private fun GameViewModel.strokeAlmostToTheInterval() =
        repeat(AUTOSAVE_STROKE_INTERVAL - 1) { stroke(it) }

    /** Draws every solution cell but the last uncommitted, then commits the last as the winning stroke. */
    private fun GameViewModel.solveWithLastStroke() {
        val solution = assertNotNull(nonogram).solution
        val cells = solution.indices.flatMap { row ->
            solution[row].indices.filter { solution[row][it] == 1 }.map { col -> row * nonogram!!.width + col }
        }
        for (index in cells.dropLast(1)) {
            tiles[index / nonogram!!.width][index % nonogram!!.width].state = TileState.FILLED
        }
        stroke(cells.last())
    }

    @Test
    fun theBoardIsWrittenOutOnceTheStrokesAddUp() = runTest(dispatcher) {
        val viewModel = loadedViewModel()

        viewModel.strokeAlmostToTheInterval()
        viewModel.awaitIdle()
        assertNull(sdk.getSingleProgress(uid, puzzleId))

        viewModel.stroke(AUTOSAVE_STROKE_INTERVAL - 1)
        viewModel.awaitIdle()

        assertNotNull(sdk.getSingleProgress(uid, puzzleId)?.boardState)
    }

    @Test
    fun undoCountsAsAChange() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        viewModel.strokeAlmostToTheInterval()
        viewModel.awaitIdle()

        viewModel.history.undo()
        viewModel.awaitIdle()

        assertNotNull(sdk.getSingleProgress(uid, puzzleId))
    }

    @Test
    fun aStrokeThatChangedNothingDoesNot() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        viewModel.strokeAlmostToTheInterval()

        repeat(3) { viewModel.recordEdits(emptyList()) }
        viewModel.awaitIdle()

        assertNull(sdk.getSingleProgress(uid, puzzleId))
    }

    @Test
    fun anAutosaveStaysLocalButLeavingPushes() = runTest(dispatcher) {
        val viewModel = loadedViewModel()

        repeat(AUTOSAVE_STROKE_INTERVAL) { viewModel.stroke(it) }
        viewModel.awaitIdle()
        assertEquals(0, service.pushes)

        viewModel.saveCurrentProgress()
        viewModel.awaitIdle()

        assertEquals(1, service.pushes)
    }

    @Test
    fun theWinningStrokeLeavesNothingForTheFlushToWrite() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        viewModel.solveWithLastStroke()
        assertTrue(viewModel.solved)

        viewModel.saveCurrentProgress(win = true)
        viewModel.awaitIdle()

        // Leaving the win card destroys the game destination, and that flushes.
        viewModel.flushProgress()
        viewModel.awaitIdle()

        val row = assertNotNull(sdk.getSingleProgress(uid, puzzleId))
        assertNull(row.boardState)
    }

    @Test
    fun aFlushWithNothingChangedWritesNothing() = runTest(dispatcher) {
        val viewModel = loadedViewModel()

        viewModel.flushProgress()
        viewModel.awaitIdle()

        assertNull(sdk.getSingleProgress(uid, puzzleId))
    }

    @Test
    fun aFlushAfterAStrokeWrites() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        viewModel.stroke(0)

        viewModel.flushProgress()
        viewModel.awaitIdle()

        assertNotNull(sdk.getSingleProgress(uid, puzzleId)?.boardState)
    }

    @Test
    fun solvedFollowsUndoAndRedo() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        viewModel.solveWithLastStroke()

        viewModel.history.undo()
        assertFalse(viewModel.solved)

        viewModel.history.redo()
        assertTrue(viewModel.solved)
    }
}

/**
 * Waits for the ViewModel's coroutines to finish. `advanceUntilIdle` is not enough: `AppSDK` hops to
 * `Dispatchers.IO`, a real thread the test scheduler knows nothing about.
 */
private suspend fun ViewModel.awaitIdle() {
    viewModelScope.coroutineContext.job.children.forEach { it.join() }
}

/** Counts what reached Firestore; everything else is inert. */
private class RecordingSyncService : SyncService {
    var pushes = 0
        private set

    override suspend fun pushProgress(firebaseUid: String, nonogramId: Long, boardState: String?, updatedAt: Long) {
        pushes++
    }

    override suspend fun pullAndMergeAllProgress(firebaseUid: String) = Unit
    override suspend fun pushNonogram(firebaseUid: String, nonogram: Nonogram, writePublishStatus: Boolean) = Unit
    override suspend fun pullPublicNonogramsSince(firebaseUid: String?, since: Long): Long = since
    override suspend fun pullOwnedNonograms(firebaseUid: String, since: Long): Long = since
    override suspend fun requestPublish(firebaseUid: String, nonogram: Nonogram): Boolean = false
    override suspend fun deleteNonogram(firebaseUid: String, nonogramId: Long): Boolean = false
    override suspend fun fetchModerationGate(firebaseUid: String) = ModerationGate()
    override suspend fun isAdmin(firebaseUid: String): Boolean = false
    override suspend fun pullPendingReviews(firebaseUid: String, limit: Int): List<Nonogram> = emptyList()
    override suspend fun decideReview(
        firebaseUid: String,
        nonogram: Nonogram,
        approve: Boolean,
        difficulty: Difficulty,
    ): Boolean = false
}
