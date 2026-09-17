package com.trainpaths.nonogram

import kotlinx.browser.document
import org.w3c.dom.events.Event

actual val hasMouseAndKeyboard: Boolean = true

/** The board's right-click erases; without this the browser puts its context menu over the canvas instead. */
fun suppressContextMenu() {
    document.addEventListener("contextmenu") { event: Event -> event.preventDefault() }
}
