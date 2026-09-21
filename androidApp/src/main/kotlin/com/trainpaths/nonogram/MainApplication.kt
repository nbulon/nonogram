package com.trainpaths.nonogram

import android.app.Application
import com.trainpaths.nonogram.auth.AuthRepository
import com.trainpaths.nonogram.di.androidModule
import com.trainpaths.nonogram.di.appModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MainApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        val koinApp = startKoin {
            androidContext(this@MainApplication)
            modules(androidModule, appModule)
        }

        val appSDK = koinApp.koin.get<AppSDK>()
        val authRepository = koinApp.koin.get<AuthRepository>()
        applicationScope.launch { AppInitializer.initializeApp(appSDK, authRepository) }
    }
}