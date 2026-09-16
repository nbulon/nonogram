package com.trainpaths.nonogram.di

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import com.trainpaths.nonogram.cache.DatabaseFactory
import com.trainpaths.nonogram.cache.DesktopDatabaseFactory
import com.trainpaths.nonogram.sync.FirebaseJvmSyncService
import com.trainpaths.nonogram.sync.SyncService
import org.koin.dsl.module
import java.io.File
import java.util.prefs.Preferences

/**
 * [dataDir] is per environment, and so is the `Settings` node: dev and prod desktop builds share one user account,
 * unlike Android (separate app ids) and web (separate origins), so this overrides `appModule`'s default `Settings()`.
 */
fun desktopModule(dataDir: File) = module {
    single<DatabaseFactory> { DesktopDatabaseFactory(dataDir) }
    single<SyncService> { FirebaseJvmSyncService(get()) }
    single<Settings> { PreferencesSettings(Preferences.userRoot().node("com/trainpaths/nonogram/${dataDir.name}")) }
}
