package com.trainpaths.nonogram

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.trainpaths.nonogram.auth.AuthRepository
import com.trainpaths.nonogram.di.appModule
import com.trainpaths.nonogram.di.desktopModule
import com.trainpaths.nonogram.firebase.FirebaseDesktop
import com.trainpaths.nonogram.screens.viewModel.AdminViewModel
import com.trainpaths.nonogram.screens.viewModel.AuthViewModel
import com.trainpaths.nonogram.screens.viewModel.GameViewModel
import com.trainpaths.nonogram.screens.viewModel.GenViewModel
import com.trainpaths.nonogram.screens.viewModel.MenuViewModel
import com.trainpaths.nonogram.screens.viewModel.ScanViewModel
import com.trainpaths.nonogram.screens.viewModel.SettingsViewModel
import com.trainpaths.nonogram.tutorial.TutorialRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.startKoin

fun main() {
    val dataDir = appDataDir(FirebaseDesktopConfig.DATA_DIR_NAME)
    // Before Koin: it builds the sync service, which touches Firestore
    FirebaseDesktop.initialize(
        dataDir = dataDir,
        apiKey = FirebaseDesktopConfig.API_KEY,
        projectId = FirebaseDesktopConfig.PROJECT_ID,
        appId = FirebaseDesktopConfig.APP_ID,
    )
    AppInitializer.onApplicationStart(FirebaseDesktopConfig.GOOGLE_WEB_CLIENT_ID)

    // appModule first, so desktopModule's Settings binding is the one that wins
    val koinApp = startKoin { modules(appModule, desktopModule(dataDir)) }
    val appSDK = koinApp.koin.get<AppSDK>()
    val authRepository = koinApp.koin.get<AuthRepository>()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        AppInitializer.initializeApp(appSDK, authRepository)
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Nonogram",
            state = rememberWindowState(size = DpSize(1100.dp, 800.dp)),
        ) {
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
}
