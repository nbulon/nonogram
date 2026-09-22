package com.trainpaths.nonogram.update

import java.awt.Desktop
import java.net.URI

private const val RELEASES = "https://github.com/nbulon/nonogram/releases/latest/download"

/** The installer this OS can use, or null when there is none to offer */
fun installerUrl(): String? {
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.contains("win") -> "$RELEASES/Nonogram-windows-x64.msi"
        os.contains("mac") -> null
        else -> "$RELEASES/Nonogram-linux-x64.deb"
    }
}

/** `Desktop.browse` is unsupported on plenty of Linux desktops, hence the `xdg-open` fallback. */
fun openInBrowser(url: String) {
    try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        } else {
            ProcessBuilder("xdg-open", url).start()
        }
    } catch (e: Exception) {
        println("Update: opening $url failed: ${e.message}")
    }
}
