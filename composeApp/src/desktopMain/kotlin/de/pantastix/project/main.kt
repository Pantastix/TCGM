// composeApp/src/desktopMain/kotlin/de/pantastix/project/Main.kt
package de.pantastix.project

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import de.pantastix.project.data.local.DatabaseDriverFactory
import de.pantastix.project.di.commonModule
import de.pantastix.project.ui.App // Importiere dein gemeinsames App Composable
import de.pantastix.project.ui.theme.AppTheme
import org.koin.core.context.startKoin
import org.koin.dsl.module

val desktopModule = module {
    single<DatabaseDriverFactory> { DatabaseDriverFactory() }
}

fun main() = application {
    startKoin {
        modules(commonModule, desktopModule)
    }
    Window(onCloseRequest = ::exitApplication, title = "Trading Card Game Manager") {
        AppTheme {
            App()
        }
    }
}