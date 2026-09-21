package com.trainpaths.nonogram

/**
 * Whether the platform's primary input is a mouse and a keyboard rather than touch: always on
 * desktop, never on Android, and on web whatever the browser reports (`PlatformInput.web.kt`).
 */
expect val hasMouseAndKeyboard: Boolean
