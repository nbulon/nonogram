package com.trainpaths.nonogram.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trainpaths.nonogram.BUTTON_SHAPE
import com.trainpaths.nonogram.icons.download
import kotlinx.browser.window

private const val RELEASES = "https://github.com/nbulon/nonogram/releases/latest/download"

/**
 * The installer for the visitor's OS, or null where there is nothing to offer. Resolved once: the
 * user agent cannot change under a running page.
 */
private val downloadUrl: String? = run {
    val ua = window.navigator.userAgent
    when {
        // Android's user agent contains "Linux": phones have to be ruled out first
        ua.contains("Android", true) || ua.contains("iPhone") || ua.contains("iPad") -> null
        ua.contains("Windows", true) -> "$RELEASES/Nonogram-windows-x64.msi"
        ua.contains("Linux", true) || ua.contains("X11") -> "$RELEASES/Nonogram-linux-x64.deb"
        else -> null // macOS, until there is a signed .dmg worth handing out
    }
}

@Composable
actual fun DesktopDownloadButton(modifier: Modifier) {
    val url = downloadUrl ?: return

    Button(
        onClick = { window.location.href = url },
        shape = BUTTON_SHAPE,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.onPrimary,
            contentColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Download Desktop App", style = MaterialTheme.typography.titleMedium)
            Icon(download, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}
