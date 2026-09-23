package com.trainpaths.nonogram.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.trainpaths.nonogram.AppButton
import com.trainpaths.nonogram.ColorTheme
import com.trainpaths.nonogram.switchColors
import com.trainpaths.nonogram.darken
import com.trainpaths.nonogram.dialogs.KeybindsDialog
import com.trainpaths.nonogram.dialogs.SignOutConfirmDialog
import com.trainpaths.nonogram.hasMouseAndKeyboard
import com.trainpaths.nonogram.icons.settings
import com.trainpaths.nonogram.navigation.TopAppBar
import com.trainpaths.nonogram.screens.viewModel.AuthViewModel
import com.trainpaths.nonogram.screens.viewModel.SettingsViewModel
import com.trainpaths.nonogram.tutorial.TutorialRepository
import com.trainpaths.nonogram.tutorial.TutorialStep
import com.trainpaths.nonogram.tutorial.tutorialAnchor
import com.trainpaths.nonogram.auth.AuthState

private val SETTINGS_BUTTON_HEIGHT = 42.dp
private val SETTINGS_ITEM_GAP = 16.dp

@Composable
fun SettingsScreen(
    authViewModel: AuthViewModel,
    settingsViewModel: SettingsViewModel,
    tutorialRepository: TutorialRepository,
    onBack: () -> Unit,
    onAdminPanel: () -> Unit,
    onSignedIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    val authState by authViewModel.authState.collectAsState()
    val theme by settingsViewModel.theme.collectAsState()
    val showAllNames by settingsViewModel.showAllNames.collectAsState()
    val autoCrossLines by settingsViewModel.autoCrossLines.collectAsState()
    val isAdmin by authViewModel.isAdmin.collectAsState()
    val signInComplete by authViewModel.signInComplete.collectAsState()

    var showSignOutDialog by remember { mutableStateOf(false) }
    var showKeybinds by remember { mutableStateOf(false) }
    var isSigningIn by remember { mutableStateOf(false) }

    LaunchedEffect(signInComplete) {
        if (signInComplete && isSigningIn) {
            isSigningIn = false
            onSignedIn()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopAppBar(
            titleIcon = settings,
            onBack = onBack,
            backArrow = true,
        )
        Column(
            modifier = Modifier.fillMaxHeight().width(310.dp).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().tutorialAnchor(TutorialStep.SETTINGS_THEME),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Theme",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ColorTheme.entries.forEach { entry ->
                        ThemeSwatch(
                            theme = entry,
                            selected = entry == theme,
                            onSelect = { settingsViewModel.selectTheme(entry) },
                        )
                    }
                }
            }
            SettingsDivider()
            Column(verticalArrangement = Arrangement.spacedBy(SETTINGS_ITEM_GAP)) {
                SettingsToggle(
                    text = "Always show names",
                    checked = showAllNames,
                    onCheckedChange = { settingsViewModel.setShowAllNames(it) },
                    step = TutorialStep.SETTINGS_SHOW_NAMES,
                )
                SettingsToggle(
                    text = "Auto-cross line",
                    checked = autoCrossLines,
                    onCheckedChange = { settingsViewModel.setAutoCrossLines(it) },
                    step = TutorialStep.SETTINGS_AUTO_CROSS,
                )
            }
            SettingsDivider()
            Column(verticalArrangement = Arrangement.spacedBy(SETTINGS_ITEM_GAP)) {
                if (hasMouseAndKeyboard) {
                    AppButton(
                        text = "Keybinds",
                        onClick = { showKeybinds = true },
                        modifier = Modifier.fillMaxWidth(),
                        height = SETTINGS_BUTTON_HEIGHT,
                    )
                }
                AppButton(
                    text = "Show tips again",
                    onClick = { tutorialRepository.resetAll() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .tutorialAnchor(TutorialStep.SETTINGS_REPLAY),
                    height = SETTINGS_BUTTON_HEIGHT,
                )
                if (isAdmin) {
                    AppButton(
                        text = "Admin panel",
                        onClick = onAdminPanel,
                        modifier = Modifier.fillMaxWidth(),
                        height = SETTINGS_BUTTON_HEIGHT,
                    )
                }
            }
            if (isSigningIn) {
                SettingsDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().height(SETTINGS_BUTTON_HEIGHT),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Text(
                        "Syncing progress...",
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            } else if (authState == AuthState.GUEST) {
                SettingsDivider()
                GoogleSignInSection(
                    onSignedIn = { uid, displayName ->
                        isSigningIn = true
                        authViewModel.onFirebaseSignInSuccess(uid, displayName)
                    },
                    modifier = Modifier.fillMaxWidth().height(SETTINGS_BUTTON_HEIGHT),
                )
            } else if (authState == AuthState.SIGNED_IN) {
                SettingsDivider()
                AppButton(
                    text = "Log Out",
                    onClick = { showSignOutDialog = true },
                    containerColor = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.fillMaxWidth(),
                    height = SETTINGS_BUTTON_HEIGHT,
                )
            }
        }
    }

    if (showKeybinds) {
        KeybindsDialog(onClose = { showKeybinds = false })
    }

    if (showSignOutDialog) {
        SignOutConfirmDialog(
            onConfirm = {
                showSignOutDialog = false
                onSignOut()
            },
            onCancel = { showSignOutDialog = false },
        )
    }
}

@Composable
private fun SettingsToggle(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    step: TutorialStep,
) {
    Row(
        modifier = Modifier.fillMaxWidth().tutorialAnchor(step),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = switchColors(),
        )
    }
}

@Composable
private fun ColumnScope.SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.fillMaxWidth(0.8f).align(Alignment.CenterHorizontally),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.onPrimary,
    )
}

@Composable
private fun ThemeSwatch(
    theme: ColorTheme,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    Box(modifier = Modifier.size(46.dp)) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .offset(x = 4.dp, y = 4.dp)
                    .background(color = MaterialTheme.colorScheme.onPrimary, shape = shape)
            )
        }
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(shape)
                .background(theme.scheme.primary.darken(if (selected) 0.15f else 0f))
                .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
                .semantics { contentDescription = theme.label },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(14.dp).background(theme.scheme.onPrimary, CircleShape))
        }
    }
}
