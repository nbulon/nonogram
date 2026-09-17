package com.trainpaths.nonogram.screens.viewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.trainpaths.nonogram.AppSDK
import com.trainpaths.nonogram.auth.AuthRepository
import com.trainpaths.nonogram.classes.Difficulty
import com.trainpaths.nonogram.classes.Nonogram
import com.trainpaths.nonogram.classes.PublishStatus
import com.trainpaths.nonogram.classes.nameControl
import com.trainpaths.nonogram.classes.normalizeNonogramName
import com.trainpaths.nonogram.classes.sanitizeNameInput
import com.trainpaths.nonogram.sync.SyncService
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock

private const val REVIEW_BATCH_SIZE = 20
private const val SIGN_IN_REQUIRED_TO_REVIEW = "Sign in again to review requests."
private val DEFAULT_REVIEW_DIFFICULTY = Difficulty.MEDIUM

class AdminViewModel(
    private val sdk: AppSDK,
    private val authRepository: AuthRepository,
    private val syncService: SyncService,
) : ViewModel() {

    private var queue by mutableStateOf<List<Nonogram>>(emptyList())

    val current: Nonogram? get() = queue.firstOrNull()

    var isLoading by mutableStateOf(true)
        private set

    var isDeciding by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    /** The difficulty an approval is filed at; authors never rate their own puzzles. */
    var selectedDifficulty by mutableStateOf(DEFAULT_REVIEW_DIFFICULTY)
        private set

    /** The name an approval is filed under: the author's, unless the reviewer edits it. */
    var name by mutableStateOf("")
        private set

    val nameProblem: String? get() = nameControl(normalizeNonogramName(name))

    init {
        refresh()
    }

    fun selectDifficulty(difficulty: Difficulty) {
        selectedDifficulty = difficulty
    }

    fun updateName(value: String) {
        name = sanitizeNameInput(value)
    }

    private fun resetReviewInputs() {
        selectedDifficulty = DEFAULT_REVIEW_DIFFICULTY
        name = current?.name.orEmpty()
    }

    fun refresh() {
        isLoading = true
        error = null
        launchGuarded {
            try {
                val firebaseUid = authRepository.currentFirebaseUid
                    .orMissing { error = SIGN_IN_REQUIRED_TO_REVIEW } ?: return@launchGuarded
                queue = syncService.pullPendingReviews(firebaseUid, REVIEW_BATCH_SIZE)
                resetReviewInputs()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Throwable) {
                error = failure.message ?: "Could not load pending requests."
            } finally {
                isLoading = false
            }
        }
    }

    fun accept() = decide(approve = true)

    fun deny() = decide(approve = false)

    private fun decide(approve: Boolean) {
        if (isDeciding) return
        val pending = current ?: return
        val problem = if (approve) nameProblem else null
        if (problem != null) {
            error = problem
            return
        }
        // The reviewer's name and difficulty only land with an approval; a denial keeps the author's.
        val decided = pending.copy(
            publishStatus = if (approve) PublishStatus.APPROVED else PublishStatus.DENIED,
            difficulty = if (approve) selectedDifficulty else pending.difficulty,
            name = if (approve) normalizeNonogramName(name) else pending.name,
            updatedAt = Clock.System.now().toEpochMilliseconds(),
        )
        isDeciding = true
        error = null
        launchGuarded {
            try {
                val firebaseUid = authRepository.currentFirebaseUid
                    .orMissing { error = SIGN_IN_REQUIRED_TO_REVIEW } ?: return@launchGuarded

                if (!syncService.decideReview(firebaseUid, decided, approve, selectedDifficulty)) {
                    error = "The decision could not be saved."
                    return@launchGuarded
                }
                if (sdk.getNonogramById(decided.id) != null) sdk.upsertNonogramFromRemote(decided)
                queue = queue.drop(1)
                resetReviewInputs()
                if (queue.isEmpty()) refresh()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Throwable) {
                error = failure.message ?: "The decision could not be saved."
            } finally {
                isDeciding = false
            }
        }
    }
}
