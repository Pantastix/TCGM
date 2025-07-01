package de.pantastix.project.ui

import androidx.compose.runtime.*
import de.pantastix.project.ui.theme.AppTheme
import de.pantastix.project.ui.viewmodel.CardListViewModel
import org.koin.compose.koinInject
import de.pantastix.project.platform.Platform
import de.pantastix.project.platform.getPlatform

// Enum zur Steuerung der Haupt-Navigation
enum class MainScreen {
    COLLECTION,
    VALUE,
    SETTINGS
}

@Composable
fun App(viewModel: CardListViewModel = koinInject()) {
    // Das zentrale Theme, das die gesamte App umschließt
    AppTheme {
        // Zustand für den aktuell ausgewählten Haupt-Screen
        var currentScreen by remember { mutableStateOf(MainScreen.COLLECTION) }

        // Adaptive Logik: Wähle die passende UI-Struktur basierend auf der Plattform
        when (getPlatform()) {
            Platform.Desktop -> {
                DesktopApp(
                    viewModel = viewModel,
                    currentScreen = currentScreen,
                    onScreenSelect = { screen -> currentScreen = screen }
                )
            }
            else -> { // Android, iOS, etc.
                MobileApp(
                    viewModel = viewModel,
                    currentScreen = currentScreen,
                    onScreenSelect = { screen -> currentScreen = screen }
                )
            }
        }
    }
}
