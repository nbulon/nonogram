package com.trainpaths.nonogram

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.trainpaths.nonogram.auth.AuthRepository
import com.trainpaths.nonogram.di.appModule
import com.trainpaths.nonogram.di.webModule
import com.trainpaths.nonogram.firebase.FirebaseWeb
import com.trainpaths.nonogram.screens.viewModel.AdminViewModel
import com.trainpaths.nonogram.screens.viewModel.AuthViewModel
import com.trainpaths.nonogram.screens.viewModel.GameViewModel
import com.trainpaths.nonogram.screens.viewModel.GenViewModel
import com.trainpaths.nonogram.screens.viewModel.MenuViewModel
import com.trainpaths.nonogram.screens.viewModel.ScanViewModel
import com.trainpaths.nonogram.screens.viewModel.SettingsViewModel
import com.trainpaths.nonogram.tutorial.TutorialRepository
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    FirebaseWeb.initialize(
        apiKey = FirebaseWebConfig.API_KEY,
        authDomain = FirebaseWebConfig.AUTH_DOMAIN,
        projectId = FirebaseWebConfig.PROJECT_ID,
        messagingSenderId = FirebaseWebConfig.MESSAGING_SENDER_ID,
        appId = FirebaseWebConfig.APP_ID,
    )
    AppInitializer.onApplicationStart(FirebaseWebConfig.GOOGLE_WEB_CLIENT_ID)
    startKoin { modules(webModule, appModule) }
    MainScope().launch {
        val koin = KoinPlatform.getKoin()
        AppInitializer.initializeApp(koin.get<AppSDK>(), koin.get<AuthRepository>())
    }

    suppressContextMenu()
    ComposeViewport {
        App(
            menuViewModelFactory = { koinViewModel<MenuViewModel>() },
            authViewModel = koinViewModel<AuthViewModel>(),
            genViewModelFactory = { koinViewModel<GenViewModel>() },
            settingsViewModel = koinViewModel<SettingsViewModel>(),
            tutorialRepository = koinInject<TutorialRepository>(),
            gameViewModelFactory = { koinViewModel<GameViewModel>() },
            adminViewModelFactory = { koinViewModel<AdminViewModel>() },
            scanViewModelFactory = { koinViewModel<ScanViewModel>() },
        )
    }
}
