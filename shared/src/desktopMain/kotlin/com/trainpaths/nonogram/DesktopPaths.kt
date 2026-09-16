package com.trainpaths.nonogram

import java.io.File

/** The per-user directory holding the database and the Firebase auth store, placed where the OS expects app data. */
fun appDataDir(name: String): File {
    val os = System.getProperty("os.name").lowercase()
    val home = System.getProperty("user.home")
    val base = when {
        os.contains("win") -> System.getenv("APPDATA")?.let(::File) ?: File(home, "AppData/Roaming")
        os.contains("mac") -> File(home, "Library/Application Support")
        else -> System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }?.let(::File) ?: File(home, ".local/share")
    }
    return File(base, name).apply { mkdirs() }
}
