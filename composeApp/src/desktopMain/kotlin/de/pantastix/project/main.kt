package de.pantastix.project

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import de.pantastix.project.data.local.DatabaseDriverFactory
import de.pantastix.project.di.commonModule
import org.koin.core.context.startKoin
import org.koin.dsl.module

// Plattformspezifisches Modul für Desktop
val desktopModule = module {
    // Stellt die actual Implementierung der DatabaseDriverFactory für Desktop bereit
    single<DatabaseDriverFactory> { DatabaseDriverFactory() }
}

fun main() = application {
    // Koin starten, bevor die UI geladen wird
    startKoin {
        // Loglevel (optional, nützlich für Debugging)
        // printLogger() // oder KoinLogger(Level.DEBUG) je nach Koin Version
        // Module laden
        modules(commonModule, desktopModule)
    }

    Window(onCloseRequest = ::exitApplication, title = "SIMON") {
        App() // Deine Haupt-Compose-UI-Funktion
    }
}