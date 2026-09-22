package com.trainpaths.nonogram

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.trainpaths.nonogram.auth.AuthRepository
import com.trainpaths.nonogram.desktop.resources.Res
import com.trainpaths.nonogram.desktop.resources.icon
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
import com.trainpaths.nonogram.update.UpdateCheck
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.startKoin
import java.awt.GraphicsEnvironment

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
        UpdateCheck.check(BuildInfo.VERSION, BuildInfo.IS_PROD)
    }

    application {
        val screen = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.bounds
        val fullscreen = DpSize(screen.width.dp, screen.height.dp)
        val state = rememberWindowState(
            placement = WindowPlacement.Floating,
            position = WindowPosition(0.dp, 0.dp),
            size = fullscreen,
        )
        Window(
            onCloseRequest = ::exitApplication,
            title = "Nonogram",
            icon = painterResource(Res.drawable.icon),
            undecorated = true,
            state = state,
            onPreviewKeyEvent = { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.F11) {
                    if (state.placement == WindowPlacement.Maximized) {
                        state.placement = WindowPlacement.Floating
                        state.position = WindowPosition(0.dp, 0.dp)
                        state.size = fullscreen
                    } else {
                        state.placement = WindowPlacement.Maximized
                    }
                    true
                } else {
                    false
                }
            },
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
