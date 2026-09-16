package com.trainpaths.nonogram.di

import com.trainpaths.nonogram.cache.AndroidDatabaseFactory
import com.trainpaths.nonogram.cache.DatabaseFactory
import com.trainpaths.nonogram.sync.FirebaseJvmSyncService
import com.trainpaths.nonogram.sync.SyncService
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val androidModule = module {
    single<DatabaseFactory> { AndroidDatabaseFactory(androidContext()) }
    single<SyncService> { FirebaseJvmSyncService(get()) }
}
