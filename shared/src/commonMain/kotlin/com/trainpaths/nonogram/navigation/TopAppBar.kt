package com.trainpaths.nonogram.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.trainpaths.nonogram.MAX_CONTENT_WIDTH
import com.trainpaths.nonogram.icons.arrowBack
import com.trainpaths.nonogram.icons.build
import com.trainpaths.nonogram.icons.edit_square
import com.trainpaths.nonogram.icons.indeterminate_question_box
import com.trainpaths.nonogram.icons.settings
import com.trainpaths.nonogram.tutorial.TutorialStep
import com.trainpaths.nonogram.tutorial.tutorialAnchor
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing

val LocalNavController = staticCompositionLocalOf<NavController> {
    error("No NavController provided")
}

enum class AppBarMode { PUZZLE, GENERATOR }

@Composable
fun TopAppBar(
    title: String = "",
    titleIcon: ImageVector? = null,
    onBack: (() -> Unit)? = null,
    showSettings: Boolean = false,
    mode: AppBarMode? = null,
    onSwapMode: (() -> Unit)? = null,
    backArrow: Boolean = false,
    swapTutorialStep: TutorialStep? = null,
    navigationContent: (@Composable () -> Unit)? = null,
) {
    val navController = if (showSettings) LocalNavController.current else null
    Box(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.TopCenter,
    ) {
        CenterAlignedTopAppBar(
            modifier = Modifier.widthIn(max = MAX_CONTENT_WIDTH),
            title = {
                if (mode != null && onSwapMode != null) {
                    IconButton(
                        onClick = onSwapMode,
                        modifier = Modifier.tutorialAnchor(swapTutorialStep),
                    ) {
                        Icon(
                            imageVector = if (mode == AppBarMode.PUZZLE) indeterminate_question_box else edit_square,
                            contentDescription = if (mode == AppBarMode.PUZZLE) "Switch to Generator" else "Switch to Puzzles",
                            modifier = Modifier.size(32.dp),
                        )
                    }
                } else if (titleIcon != null) {
                    Icon(
                        imageVector = titleIcon,
                        contentDescription = title.ifEmpty { null },
                        modifier = Modifier.size(32.dp),
                    )
                } else if (title.isNotEmpty()) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                }
            },
            navigationIcon = {
                if (navigationContent != null) {
                    navigationContent()
                } else if (onBack != null) {
                    val isWrench = !backArrow && mode != AppBarMode.PUZZLE
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.tutorialAnchor(
                            TutorialStep.GEN_WRENCH.takeIf { isWrench && mode == AppBarMode.GENERATOR }
                        ),
                    ) {
                        Icon(
                            imageVector = if (isWrench) build else arrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            },
            actions = {
                if (showSettings) {
                    IconButton(
                        onClick = { navController?.navigate(SettingsRoute) },
                        modifier = Modifier.tutorialAnchor(TutorialStep.MENU_SETTINGS),
                    ) {
                        Icon(
                            settings,
                            contentDescription = "Settings",
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            },
            windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Unspecified,
                navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                actionIconContentColor = MaterialTheme.colorScheme.onPrimary
            ),
        )
    }
}
