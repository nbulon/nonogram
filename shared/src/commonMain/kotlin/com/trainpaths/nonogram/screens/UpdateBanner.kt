package com.trainpaths.nonogram.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A desktop-only prompt: GitHub Releases carries an installer newer than the running build. Every other
 * platform updates itself — the store on Android, a reload on web — so their actuals are empty. The no-op
 * cannot live in `jvmSharedMain` the way [DesktopDownloadButton]'s does: `desktopMain` depends on it, and
 * the real actual is there.
 */
@Composable
expect fun UpdateBanner(modifier: Modifier = Modifier)
