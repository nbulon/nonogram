package com.trainpaths.nonogram

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.events.Event

actual val hasMouseAndKeyboard: Boolean = window.matchMedia("(pointer: fine) and (hover: hover)").matches

/** The board's right-click erases; without this the browser puts its context menu over the canvas instead. */
fun suppressContextMenu() {
    document.addEventListener("contextmenu") { event: Event -> event.preventDefault() }
}
