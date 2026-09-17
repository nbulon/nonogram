package com.trainpaths.nonogram.screens.viewModel

import androidx.lifecycle.ViewModel
import com.trainpaths.nonogram.auth.AuthRepository
import com.trainpaths.nonogram.auth.firebaseSignOut
import com.trainpaths.nonogram.sync.SyncService
import com.trainpaths.nonogram.sync.syncPublicNonograms
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

enum class GeneratorSyncState { IDLE, SYNCING, ERROR }

/**
 * How long a remote pass may run before the UI stops waiting on it: a Firestore promise that never
 * settles cannot be cancelled from Kotlin, so the coroutine gives up on it instead.
 */
internal val SYNC_TIMEOUT = 30.seconds

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val syncService: SyncService,
) : ViewModel() {

    val authState = authRepository.authState
    val hasCompletedOnboarding get() = authRepository.hasCompletedOnboarding

    private val _signInComplete = MutableStateFlow(false)
    val signInComplete = _signInComplete.asStateFlow()

    private val _generatorSyncState =
        MutableStateFlow(GeneratorSyncState.IDLE)
    val generatorNonogramSyncState = _generatorSyncState.asStateFlow()

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin = _isAdmin.asStateFlow()

    private val _publishBanned = MutableStateFlow(false)
    val publishBanned = _publishBanned.asStateFlow()

    init {
        launchGuarded {
            authRepository.currentUserUid.collect {
                val firebaseUid = authRepository.currentFirebaseUid
                _isAdmin.value = firebaseUid != null && authRepository.getIsAdmin(firebaseUid)
                _publishBanned.value =
                    firebaseUid != null && authRepository.getPublishBanned(firebaseUid)
            }
        }
    }

    fun onFirebaseSignInSuccess(firebaseUid: String, displayName: String?) {
        _signInComplete.value = false
        launchGuarded(
            Dispatchers.Default,
            onError = { println("SignIn: post-sign-in sync failed: ${it.message}") },
        ) {
            try {
                authRepository.linkFirebaseUser(firebaseUid, displayName)
                withTimeoutOrNull(SYNC_TIMEOUT) {
                    if (syncService.hasRemoteProgress(firebaseUid)) {
                        syncService.pullAllProgress(firebaseUid)
                    } else {
                        syncService.uploadAllLocalProgress(firebaseUid)
                    }
                    syncOwnedNonograms(firebaseUid)
                    syncService.uploadAllLocalNonograms(firebaseUid)
                    refreshPublishState(firebaseUid)
                }
            } finally {
                _signInComplete.value = true
            }
        }
    }

    /** The whole remote pass, awaitable and bounded, so a caller can hold its own spinner around it. */
    suspend fun syncAllNow() {
        withTimeoutOrNull(SYNC_TIMEOUT) {
            syncService.syncPublicNonograms(authRepository, authRepository.currentFirebaseUid)

            val firebaseUid = authRepository.currentFirebaseUid.orMissing() ?: return@withTimeoutOrNull
            syncService.pullAndMergeAllProgress(firebaseUid)
            syncOwnedNonograms(firebaseUid)
            refreshPublishState(firebaseUid)
        }
    }

    fun syncAll(onComplete: () -> Unit = {}) {
        launchGuarded(Dispatchers.Default) {
            try {
                syncAllNow()
            } finally {
                notifyComplete(onComplete)
            }
        }
    }

    fun retryOwnNonograms(onComplete: () -> Unit = {}) {
        launchGuarded(Dispatchers.Default) {
            try {
                val firebaseUid = authRepository.currentFirebaseUid.orMissing() ?: return@launchGuarded
                withTimeoutOrNull(SYNC_TIMEOUT) { syncOwnedNonograms(firebaseUid) }
            } finally {
                notifyComplete(onComplete)
            }
        }
    }

    /** [NonCancellable]: a plain `withContext` in a `finally` rethrows on a cancelled job and skips the callback. */
    private suspend fun notifyComplete(onComplete: () -> Unit) {
        withContext(NonCancellable + Dispatchers.Main) { onComplete() }
    }

    private suspend fun refreshPublishState(firebaseUid: String) {
        val admin = syncService.isAdmin(firebaseUid)
        authRepository.setIsAdmin(firebaseUid, admin)
        _isAdmin.value = admin

        val gate = syncService.fetchModerationGate(firebaseUid) ?: return
        authRepository.setModerationGate(firebaseUid, gate.denialStreak, gate.banned)
        _publishBanned.value = gate.banned
    }

    private suspend fun syncOwnedNonograms(firebaseUid: String) {
        val lastSyncedAt = authRepository.getLastOwnedNonogramSyncTimestamp(firebaseUid)
        _generatorSyncState.value = GeneratorSyncState.SYNCING
        try {
            val newestReceivedAt = syncService.pullOwnedNonograms(firebaseUid, lastSyncedAt)
            if (newestReceivedAt == null) {
                _generatorSyncState.value = GeneratorSyncState.ERROR
                return
            }
            if (newestReceivedAt != lastSyncedAt) {
                authRepository.setLastOwnedNonogramSyncTimestamp(firebaseUid, newestReceivedAt)
            }
            _generatorSyncState.value = GeneratorSyncState.IDLE
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            println("FirestoreSync: owned nonogram sync for Generator failed: ${error.message}")
            _generatorSyncState.value = GeneratorSyncState.ERROR
        } finally {
            if (_generatorSyncState.value == GeneratorSyncState.SYNCING) {
                _generatorSyncState.value = GeneratorSyncState.ERROR
            }
        }
    }

    fun completeOnboarding() {
        authRepository.completeOnboarding()
    }

    fun signOut(onComplete: () -> Unit = {}) {
        launchGuarded(Dispatchers.Default) {
            try {
                try {
                    firebaseSignOut()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    println("SignOut: firebase sign-out failed: ${error.message}")
                }
                authRepository.signOut()
                _signInComplete.value = false
                _generatorSyncState.value = GeneratorSyncState.IDLE
                _isAdmin.value = false
                _publishBanned.value = false
            } finally {
                notifyComplete(onComplete)
            }
        }
    }
}
