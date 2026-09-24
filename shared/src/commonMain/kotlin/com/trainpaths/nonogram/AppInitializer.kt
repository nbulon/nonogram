package com.trainpaths.nonogram

import com.mmk.kmpauth.core.KMPAuth
import com.mmk.kmpauth.google.google
import com.trainpaths.nonogram.auth.AuthRepository
import kotlinx.coroutines.CancellationException

object AppInitializer {
    const val OAUTH_REDIRECT_URI = "http://localhost:42594/callback"

    fun onApplicationStart(googleWebClientId: String) {
        KMPAuth.initialize {
            google(serverId = googleWebClientId, redirectUri = OAUTH_REDIRECT_URI)
        }
    }

    /**
     * The startup work that has to finish before the UI reads the database. Both run while `authState` is
     * still `INITIALIZING`, so `App` is showing the loading screen and no ViewModel has been built yet —
     * `MenuViewModel.init` would otherwise list the puzzles before the seeds were in.
     */
    suspend fun initializeApp(appSDK: AppSDK, authRepository: AuthRepository) {
        try {
            appSDK.seedIfEmpty()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            println("Seed: inserting the built-in puzzles failed: ${error.message}")
        }
        authRepository.initialize()
    }
}
