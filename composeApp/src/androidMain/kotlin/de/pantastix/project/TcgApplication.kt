package de.pantastix.project

import android.app.Application
import de.pantastix.project.di.androidModule
import de.pantastix.project.di.commonModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class TcgApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@TcgApplication)
            androidLogger() // Optional: Fügt Koin-Logging zur Android-Logcat hinzu
            modules(commonModule, androidModule)
        }
    }
}