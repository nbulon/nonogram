package com.trainpaths.nonogram.screens.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russhwolf.settings.MapSettings
import com.trainpaths.nonogram.AppSDK
import com.trainpaths.nonogram.TestDatabaseFactory
import com.trainpaths.nonogram.auth.AuthRepository
import com.trainpaths.nonogram.classes.Difficulty
import com.trainpaths.nonogram.classes.Nonogram
import com.trainpaths.nonogram.settings.SettingsRepository
import com.trainpaths.nonogram.sync.SyncService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The refresh path must always let go of its spinner: a remote call that never settles is bounded
 * by [SYNC_TIMEOUT], and a failing one still clears `isRefreshing`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RefreshTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var sdk: AppSDK
    private lateinit var authRepository: AuthRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        sdk = AppSDK(TestDatabaseFactory())
        authRepository = AuthRepository(sdk, MapSettings())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun syncAllNow_givesUpOnARemoteCallThatNeverSettles() = runTest(dispatcher) {
        authRepository.initialize()
        val viewModel = AuthViewModel(authRepository, HangingSyncService())

        viewModel.syncAllNow()

        assertEquals(SYNC_TIMEOUT.inWholeMilliseconds, currentTime)
    }

    @Test
    fun refresh_clearsTheIndicatorWhenTheSyncFails() = runTest(dispatcher) {
        authRepository.initialize()
        val viewModel = MenuViewModel(sdk, authRepository, SettingsRepository(MapSettings()))
        viewModel.awaitIdle()

        viewModel.refresh { error("no network") }
        assertTrue(viewModel.isRefreshing)
        viewModel.awaitIdle()

        assertFalse(viewModel.isRefreshing)
    }

    @Test
    fun refresh_ignoresASecondPullWhileOneIsRunning() = runTest(dispatcher) {
        authRepository.initialize()
        val viewModel = MenuViewModel(sdk, authRepository, SettingsRepository(MapSettings()))
        viewModel.awaitIdle()
        var runs = 0

        viewModel.refresh { runs++ }
        viewModel.refresh { runs++ }
        viewModel.awaitIdle()

        assertEquals(1, runs)
        assertFalse(viewModel.isRefreshing)
    }
}

/**
 * Waits for the ViewModel's coroutines to finish. `advanceUntilIdle` is not enough: `AppSDK` hops to
 * `Dispatchers.IO`, a real thread the test scheduler knows nothing about.
 */
private suspend fun ViewModel.awaitIdle() {
    viewModelScope.coroutineContext.job.children.forEach { it.join() }
}

/** Every remote call hangs, the way a Firestore promise that never settles does. */
private class HangingSyncService : SyncService {
    override suspend fun pushProgress(firebaseUid: String, nonogramId: Long, boardState: String?, updatedAt: Long) =
        hang()

    override suspend fun hasRemoteProgress(firebaseUid: String): Boolean = hang()
    override suspend fun uploadAllLocalProgress(firebaseUid: String) = hang()
    override suspend fun pullAllProgress(firebaseUid: String) = hang()
    override suspend fun pullAndMergeAllProgress(firebaseUid: String) = hang()
    override suspend fun pushNonogram(firebaseUid: String, nonogram: Nonogram, writePublishStatus: Boolean) = hang()
    override suspend fun uploadAllLocalNonograms(firebaseUid: String) = hang()
    override suspend fun pullPublicNonogramsSince(firebaseUid: String?, since: Long): Long = hang()
    override suspend fun pullOwnedNonograms(firebaseUid: String, since: Long): Long = hang()
    override suspend fun requestPublish(firebaseUid: String, nonogram: Nonogram): Boolean = hang()
    override suspend fun deleteNonogram(firebaseUid: String, nonogramId: Long): Boolean = hang()
    override suspend fun fetchModerationGate(firebaseUid: String) = hang()
    override suspend fun isAdmin(firebaseUid: String): Boolean = hang()
    override suspend fun pullPendingReviews(firebaseUid: String, limit: Int): List<Nonogram> = hang()
    override suspend fun decideReview(
        firebaseUid: String,
        nonogram: Nonogram,
        approve: Boolean,
        difficulty: Difficulty,
    ): Boolean = hang()

    private suspend fun hang(): Nothing = awaitCancellation()
}
