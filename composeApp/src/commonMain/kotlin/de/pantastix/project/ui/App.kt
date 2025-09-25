package de.pantastix.project.ui

import androidx.compose.runtime.*
import de.pantastix.project.ui.theme.AppTheme
import de.pantastix.project.ui.viewmodel.CardListViewModel
import org.koin.compose.koinInject
import de.pantastix.project.platform.Platform
import de.pantastix.project.platform.getPlatform
import de.pantastix.project.ui.screens.LoadingScreen

enum class MainScreen {
    COLLECTION,
    VALUE,
    SETTINGS,
    SUPABASE_GUIDE,
    EXPORT
}

@Composable
fun App(viewModel: CardListViewModel = koinInject()) {
    val uiState by viewModel.uiState.collectAsState()

    // Ruft die Initialisierung genau einmal auf, wenn die App startet.
    LaunchedEffect(Unit) {
        viewModel.initialize()
    }

    var currentScreen by remember { mutableStateOf(MainScreen.COLLECTION) }

    key(uiState.appLanguage) {
        AppTheme {
            if (!uiState.isInitialized) {
                LoadingScreen(viewModel)
            } else {
                when (getPlatform()) {
                    Platform.Windows, Platform.Linux, Platform.Mac -> {
                        DesktopApp(
                            viewModel = viewModel,
                            currentScreen = currentScreen,
                            onScreenSelect = { screen -> currentScreen = screen }
                        )
                    }
                    else -> { // Android, iOS, etc.
                        MobileApp(
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}

