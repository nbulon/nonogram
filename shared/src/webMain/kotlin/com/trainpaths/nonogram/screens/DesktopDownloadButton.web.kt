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
import com.trainpaths.nonogram.hasMouseAndKeyboard
import com.trainpaths.nonogram.icons.download
import kotlinx.browser.window

private const val RELEASES = "https://github.com/nbulon/nonogram/releases/latest/download"
private const val PLAY_STORE = "https://play.google.com/store/apps/details?id=com.trainpaths.nonogram"

/** [newTab] separates a store page from a file the browser should just download in place. */
private class NativeApp(val label: String, val url: String, val newTab: Boolean)

/**
 * What this visitor should be offered, or null when that is nothing. Lazy rather than eager: it
 * reads [hasMouseAndKeyboard], another file's top-level `val`, and Kotlin/JS gives no guarantee
 * about the order two files' initializers run in.
 */
private val nativeApp: NativeApp? by lazy {
    val ua = window.navigator.userAgent
    when {
        ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iPod") -> null
        // Android's user agent contains "Linux", has to be matched before the desktops
        ua.contains("Android", true) -> NativeApp("Download Android App", PLAY_STORE, newTab = true)
        !hasMouseAndKeyboard -> NativeApp("Download Android App", PLAY_STORE, newTab = true)
        ua.contains("Windows", true) ->
            NativeApp("Download Desktop App", "$RELEASES/Nonogram-windows-x64.msi", newTab = false)

        ua.contains("Linux", true) || ua.contains("X11") ->
            NativeApp("Download Desktop App", "$RELEASES/Nonogram-linux-x64.deb", newTab = false)

        else -> null // macOS, until there is a signed .dmg worth handing out
    }
}

@Composable
actual fun DesktopDownloadButton(modifier: Modifier) {
    val app = nativeApp ?: return

    Button(
        onClick = {
            if (app.newTab) {
                window.open(app.url, "_blank")
            } else {
                window.location.href = app.url
            }
        },
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
            Text(app.label, style = MaterialTheme.typography.titleMedium)
            Icon(download, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}
