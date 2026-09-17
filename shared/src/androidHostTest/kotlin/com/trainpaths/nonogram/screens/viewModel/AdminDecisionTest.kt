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
import com.trainpaths.nonogram.sync.ModerationGate
import com.trainpaths.nonogram.sync.SyncService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * A decision is mirrored onto the reviewer's own copy of the puzzle, with the same stamp the
 * document got, so the reviewer-author sees the verdict without waiting for a sync.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdminDecisionTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var sdk: AppSDK
    private lateinit var authRepository: AuthRepository
    private lateinit var service: ReviewingSyncService

    private val uid = "admin-1"

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        sdk = AppSDK(TestDatabaseFactory())
        authRepository = AuthRepository(sdk, MapSettings())
        service = ReviewingSyncService()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun pending(id: Long, authorUid: String = uid) = Nonogram(
        id = id, difficulty = Difficulty.EASY, solution = listOf(listOf(1, 0), listOf(0, 1)),
        name = "Comet", authorUid = authorUid, updatedAt = 100, publishStatus = PublishStatus.PENDING,
    )

    private suspend fun signedInViewModel(): AdminViewModel {
        authRepository.initialize()
        authRepository.linkFirebaseUser(uid, "Admin")
        return AdminViewModel(sdk, authRepository, service).also { it.awaitIdle() }
    }

    @Test
    fun accept_writesTheVerdictOntoTheLocalCopy() = runTest(dispatcher) {
        service.queue = listOf(pending(42))
        sdk.upsertNonogramFromRemote(pending(42))
        val viewModel = signedInViewModel()
        viewModel.selectDifficulty(Difficulty.HARD)

        viewModel.accept()
        viewModel.awaitIdle()

        val decided = assertNotNull(service.decided)
        val local = assertNotNull(sdk.getNonogramById(42))
        assertEquals(PublishStatus.APPROVED, local.publishStatus)
        assertEquals(Difficulty.HARD, local.difficulty)
        assertEquals(decided.updatedAt, local.updatedAt)
    }

    @Test
    fun deny_keepsTheAuthorsDifficultyAndName() = runTest(dispatcher) {
        service.queue = listOf(pending(42))
        sdk.upsertNonogramFromRemote(pending(42))
        val viewModel = signedInViewModel()
        viewModel.selectDifficulty(Difficulty.HARD)
        viewModel.updateName("Renamed")

        viewModel.deny()
        viewModel.awaitIdle()

        val local = assertNotNull(sdk.getNonogramById(42))
        assertEquals(PublishStatus.DENIED, local.publishStatus)
        assertEquals(Difficulty.EASY, local.difficulty)
        assertEquals("Comet", local.name)
    }

    @Test
    fun accept_leavesAPuzzleThisDeviceNeverHeldAlone() = runTest(dispatcher) {
        service.queue = listOf(pending(42, authorUid = "someone-else"))
        val viewModel = signedInViewModel()

        viewModel.accept()
        viewModel.awaitIdle()

        assertNotNull(service.decided)
        assertNull(sdk.getNonogramById(42))
    }
}

private suspend fun ViewModel.awaitIdle() {
    viewModelScope.coroutineContext.job.children.forEach { it.join() }
}

/** Serves one review batch and records the decision; nothing else is reachable from the admin flow. */
private class ReviewingSyncService : SyncService {

    var queue: List<Nonogram> = emptyList()
    var decided: Nonogram? = null

    override suspend fun pullPendingReviews(firebaseUid: String, limit: Int): List<Nonogram> = queue

    override suspend fun decideReview(
        firebaseUid: String,
        nonogram: Nonogram,
        approve: Boolean,
        difficulty: Difficulty,
    ): Boolean {
        decided = nonogram
        queue = queue.filterNot { it.id == nonogram.id }
        return true
    }

    override suspend fun pushProgress(firebaseUid: String, nonogramId: Long, boardState: String?, updatedAt: Long) =
        unused()

    override suspend fun pullAndMergeAllProgress(firebaseUid: String) = unused()
    override suspend fun pushNonogram(firebaseUid: String, nonogram: Nonogram, writePublishStatus: Boolean) = unused()
    override suspend fun pullPublicNonogramsSince(firebaseUid: String?, since: Long): Long = unused()
    override suspend fun pullOwnedNonograms(firebaseUid: String, since: Long): Long = unused()
    override suspend fun requestPublish(firebaseUid: String, nonogram: Nonogram): Boolean = unused()
    override suspend fun deleteNonogram(firebaseUid: String, nonogramId: Long): Boolean = unused()
    override suspend fun fetchModerationGate(firebaseUid: String): ModerationGate = unused()
    override suspend fun isAdmin(firebaseUid: String): Boolean = unused()

    private fun unused(): Nothing = error("not part of the admin flow")
}
