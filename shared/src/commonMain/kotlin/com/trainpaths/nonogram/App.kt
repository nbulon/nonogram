package com.trainpaths.nonogram

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.DialogNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.trainpaths.nonogram.auth.AuthState
import com.trainpaths.nonogram.dialogs.ArtCardDialog
import com.trainpaths.nonogram.dialogs.PlayConfirmDialog
import com.trainpaths.nonogram.navigation.AdminRoute
import com.trainpaths.nonogram.navigation.ArtRoute
import com.trainpaths.nonogram.navigation.FilterRoute
import com.trainpaths.nonogram.navigation.GameRoute
import com.trainpaths.nonogram.navigation.GenConfRoute
import com.trainpaths.nonogram.navigation.GenListRoute
import com.trainpaths.nonogram.navigation.GenScanRoute
import com.trainpaths.nonogram.navigation.GeneratorRoute
import com.trainpaths.nonogram.navigation.LocalNavController
import com.trainpaths.nonogram.navigation.LoginRoute
import com.trainpaths.nonogram.navigation.MenuRoute
import com.trainpaths.nonogram.navigation.PlayDialogRoute
import com.trainpaths.nonogram.navigation.SettingsRoute
import com.trainpaths.nonogram.screens.AdminScreen
import com.trainpaths.nonogram.screens.DesktopDownloadButton
import com.trainpaths.nonogram.screens.FilterScreen
import com.trainpaths.nonogram.screens.GameScreen
import com.trainpaths.nonogram.screens.GenConfScreen
import com.trainpaths.nonogram.screens.GenScanScreen
import com.trainpaths.nonogram.screens.GenScreen
import com.trainpaths.nonogram.screens.LoadingScreen
import com.trainpaths.nonogram.screens.LoginScreen
import com.trainpaths.nonogram.screens.MenuScreen
import com.trainpaths.nonogram.screens.GenListScreen
import com.trainpaths.nonogram.screens.SettingsScreen
import com.trainpaths.nonogram.screens.UpdateBanner
import com.trainpaths.nonogram.screens.viewModel.AdminViewModel
import com.trainpaths.nonogram.screens.viewModel.AuthViewModel
import com.trainpaths.nonogram.screens.viewModel.GameViewModel
import com.trainpaths.nonogram.screens.viewModel.GenViewModel
import com.trainpaths.nonogram.screens.viewModel.GeneratorSyncState
import com.trainpaths.nonogram.screens.viewModel.MenuViewModel
import com.trainpaths.nonogram.screens.viewModel.ScanViewModel
import com.trainpaths.nonogram.screens.viewModel.SettingsViewModel
import com.trainpaths.nonogram.tutorial.TutorialHost
import com.trainpaths.nonogram.tutorial.TutorialRepository

@Composable
fun App(
    menuViewModelFactory: @Composable () -> MenuViewModel,
    authViewModel: AuthViewModel,
    genViewModelFactory: @Composable () -> GenViewModel,
    settingsViewModel: SettingsViewModel,
    tutorialRepository: TutorialRepository,
    gameViewModelFactory: @Composable () -> GameViewModel,
    adminViewModelFactory: @Composable () -> AdminViewModel,
    scanViewModelFactory: @Composable () -> ScanViewModel,
) {
    val theme by settingsViewModel.theme.collectAsState()
    val authState by authViewModel.authState.collectAsState()

    AppTheme(theme = theme) {
        if (authState == AuthState.INITIALIZING) {
            LoadingScreen()
        } else {
            AppContent(
                menuViewModel = menuViewModelFactory(),
                authViewModel = authViewModel,
                genViewModel = genViewModelFactory(),
                settingsViewModel = settingsViewModel,
                tutorialRepository = tutorialRepository,
                gameViewModelFactory = gameViewModelFactory,
                adminViewModelFactory = adminViewModelFactory,
                scanViewModelFactory = scanViewModelFactory,
            )
        }
    }
}

@Composable
private fun AppContent(
    menuViewModel: MenuViewModel,
    authViewModel: AuthViewModel,
    genViewModel: GenViewModel,
    settingsViewModel: SettingsViewModel,
    tutorialRepository: TutorialRepository,
    gameViewModelFactory: @Composable () -> GameViewModel,
    adminViewModelFactory: @Composable () -> AdminViewModel,
    scanViewModelFactory: @Composable () -> ScanViewModel,
) {
    val startDestination = if (authViewModel.hasCompletedOnboarding) MenuRoute else LoginRoute

    LaunchedEffect(Unit) {
        authViewModel.syncAll { menuViewModel.reload() }
    }

    val navController = rememberNavController()

    val currentDestination = navController.currentBackStackEntryAsState().value?.destination

    TutorialHost(
        tutorialRepository = tutorialRepository,
        paused = currentDestination is DialogNavigator.Destination,
    ) {
        CompositionLocalProvider(LocalNavController provides navController) {
            Box(Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary)
                        .fillMaxSize(),
                ) {
                    composable<LoginRoute> {
                        LoginScreen(
                            authViewModel = authViewModel,
                            onLoginSuccess = {
                                menuViewModel.reload(true)
                                navController.navigate(MenuRoute) {
                                    popUpTo(LoginRoute) { inclusive = true }
                                }
                            },
                            onContinueAsGuest = {
                                navController.navigate(MenuRoute) {
                                    popUpTo(LoginRoute) { inclusive = true }
                                }
                            },
                        )
                    }
                    composable<MenuRoute> {
                        LaunchedEffect(Unit) {
                            menuViewModel.reload()
                        }
                        MenuScreen(
                            viewModel = menuViewModel,
                            onRefresh = { menuViewModel.refresh { authViewModel.syncAllNow() } },
                            onNonogramClick = { ng ->
                                navController.navigate(
                                    PlayDialogRoute(
                                        ng.id,
                                        ng.difficulty.toString()
                                    )
                                )
                            },
                            onGenClick = {
                                navController.navigate(GenListRoute)
                            },
                            onMoreFilters = { navController.navigate(FilterRoute(generator = false)) },
                        )
                    }
                    composable<GenListRoute> {
                        val generatorSyncState by authViewModel.generatorNonogramSyncState.collectAsState()
                        LaunchedEffect(generatorSyncState) {
                            if (generatorSyncState != GeneratorSyncState.SYNCING) {
                                genViewModel.loadMyNonograms()
                            }
                        }
                        GenListScreen(
                            genViewModel = genViewModel,
                            generatorSyncState = generatorSyncState,
                            onRetrySync = {
                                authViewModel.retryOwnNonograms()
                            },
                            onSwap = {
                                navController.navigate(MenuRoute) {
                                    popUpTo(MenuRoute) { inclusive = true }
                                }
                            },
                            onNewClick = {
                                genViewModel.startNew()
                                navController.navigate(GenConfRoute(editing = false))
                            },
                            onScanClick = { navController.navigate(GenScanRoute) },
                            onEditClick = { nonogram ->
                                genViewModel.loadForEdit(nonogram)
                                navController.navigate(GeneratorRoute)
                            },
                            onMoreFilters = { navController.navigate(FilterRoute(generator = true)) },
                        )
                    }
                    composable<FilterRoute> { entry ->
                        val route: FilterRoute = entry.toRoute()
                        if (route.generator) {
                            FilterScreen(
                                state = genViewModel.filterSort,
                                onChange = genViewModel::applyFilterSort,
                                onBack = { navController.popBackStack() },
                            )
                        } else {
                            FilterScreen(
                                state = menuViewModel.filterSort,
                                onChange = menuViewModel::applyFilterSort,
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                    composable<GenConfRoute> { entry ->
                        val route: GenConfRoute = entry.toRoute()
                        val isPublishBanned by authViewModel.publishBanned.collectAsState()
                        LaunchedEffect(Unit) {
                            genViewModel.reloadSaved()
                        }
                        GenConfScreen(
                            genViewModel = genViewModel,
                            editing = route.editing,
                            isPublishBanned = isPublishBanned,
                            onBack = { navController.popBackStack() },
                            onDone = {
                                if (route.editing) {
                                    navController.popBackStack()
                                } else {
                                    navController.navigate(GeneratorRoute) {
                                        popUpTo(GenListRoute) { inclusive = false }
                                    }
                                }
                            },
                            onDeleted = {
                                navController.navigate(GenListRoute) {
                                    popUpTo(GenListRoute) { inclusive = true }
                                }
                            },
                        )
                    }
                    composable<GenScanRoute> {
                        GenScanScreen(
                            genViewModel = genViewModel,
                            scanViewModel = scanViewModelFactory(),
                            onBack = { navController.popBackStack() },
                            onDone = {
                                navController.navigate(GeneratorRoute) {
                                    popUpTo(GenListRoute) { inclusive = false }
                                }
                            },
                        )
                    }
                    composable<GeneratorRoute> {
                        LaunchedEffect(Unit) {
                            genViewModel.reloadSaved()
                        }
                        GenScreen(
                            genViewModel = genViewModel,
                            onConfig = {
                                if (genViewModel.canSave) genViewModel.onSave()
                                navController.navigate(GenConfRoute(editing = true))
                            },
                            onExitToList = {
                                navController.navigate(GenListRoute) {
                                    popUpTo(GenListRoute) { inclusive = true }
                                }
                            },
                        )
                    }
                    dialog<PlayDialogRoute> { entry ->
                        val route: PlayDialogRoute = entry.toRoute()
                        PlayConfirmDialog(
                            route.difficulty,
                            menuViewModel.getBeatCount(route.nonogramId),
                            hasProgress = menuViewModel.hasProgress(route.nonogramId),
                            onConfirm = {
                                navController.navigate(GameRoute(route.nonogramId)) {
                                    popUpTo(MenuRoute)
                                }
                            },
                            onDismiss = { navController.popBackStack() },
                            onShow = {
                                navController.navigate(ArtRoute(route.nonogramId)) {
                                    popUpTo<PlayDialogRoute> { inclusive = true }
                                }
                            },
                        )
                    }
                    composable<GameRoute> { entry ->
                        val route: GameRoute = entry.toRoute()
                        val viewModel = gameViewModelFactory()
                        LaunchedEffect(route.nonogramId) {
                            viewModel.loadNonogram(route.nonogramId)
                        }
                        GameScreen(
                            viewModel = viewModel,
                            onBack = {
                                viewModel.saveCurrentProgress()
                                viewModel.currentNonogramId?.let { id ->
                                    menuViewModel.updateSingleProgress(id, viewModel.currentProgress)
                                }
                                navController.popBackStack(MenuRoute, inclusive = false)
                            },
                            onWin = {
                                viewModel.saveCurrentProgress(true)
                                viewModel.currentNonogramId?.let { id ->
                                    menuViewModel.clearProgress(id)
                                    menuViewModel.incrementBeatCount(id)
                                }
                            },
                            onHome = {
                                navController.navigate(MenuRoute) {
                                    popUpTo(MenuRoute) { inclusive = true }
                                }
                            },
                            onSwapMode = {
                                viewModel.saveCurrentProgress()
                                viewModel.currentNonogramId?.let { id ->
                                    menuViewModel.updateSingleProgress(id, viewModel.currentProgress)
                                }
                                navController.navigate(GenListRoute) {
                                    popUpTo(MenuRoute) { inclusive = true }
                                }
                            },
                        )
                    }
                    dialog<ArtRoute> { entry ->
                        val route: ArtRoute = entry.toRoute()
                        val nonogram = menuViewModel.nonograms.firstOrNull { it.id == route.nonogramId }
                        if (nonogram == null) {
                            LaunchedEffect(Unit) { navController.popBackStack() }
                            return@dialog
                        }
                        ArtCardDialog(
                            nonogram = nonogram,
                            won = false,
                            onClose = { navController.popBackStack() },
                        )
                    }
                    composable<SettingsRoute> {
                        SettingsScreen(
                            authViewModel = authViewModel,
                            settingsViewModel = settingsViewModel,
                            tutorialRepository = tutorialRepository,
                            onBack = { navController.popBackStack() },
                            onAdminPanel = { navController.navigate(AdminRoute) },
                            onSignedIn = { menuViewModel.reload() },
                            onSignOut = {
                                authViewModel.signOut {
                                    menuViewModel.reload(true)
                                    genViewModel.loadMyNonograms()
                                }
                            },
                        )
                    }
                    composable<AdminRoute> {
                        AdminScreen(
                            adminViewModel = adminViewModelFactory(),
                            onBack = { navController.popBackStack() },
                        )
                    }
                }

                val hidden = currentDestination is DialogNavigator.Destination ||
                        currentDestination?.hasRoute<GameRoute>() == true ||
                        currentDestination?.hasRoute<GeneratorRoute>() == true
                if (!hidden) {
                    // Never both: the download button is empty on desktop and the update banner is empty off it
                    DesktopDownloadButton(
                        Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    )
                    UpdateBanner(
                        Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    )
                }
            }
        }
    }
}
