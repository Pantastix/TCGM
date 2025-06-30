package de.pantastix.project.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import de.pantastix.project.ui.screens.AddCardFlow
import de.pantastix.project.ui.screens.CardDetailScreen
import de.pantastix.project.ui.screens.CardListScreen
import de.pantastix.project.ui.screens.EditCardFlow
import de.pantastix.project.ui.theme.AppTheme
import de.pantastix.project.ui.viewmodel.CardListViewModel
import org.koin.compose.koinInject

// Enum zur Steuerung der Haupt-Navigation
enum class Screen { List, Detail, Edit }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(viewModel: CardListViewModel = koinInject()) {
    // UI-Zustand wird aus dem ViewModel geholt.
    // `collectAsState` sorgt dafür, dass die UI bei Änderungen automatisch neu gezeichnet wird.
    val uiState by viewModel.uiState.collectAsState()

    // Lokaler Zustand für die Navigation und die Sichtbarkeit des Dialogs.
    var currentScreen by remember { mutableStateOf(Screen.List) }
    var showAddCardDialog by remember { mutableStateOf(false) }

    // Das AppTheme umschließt die gesamte Anwendung.
    AppTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Trading Card Game Manager") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddCardDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Neue Karte hinzufügen")
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                // Hauptinhalt basierend auf dem aktuellen Screen
                when (currentScreen) {
                    Screen.List -> CardListScreen(
                        cardInfos = uiState.cardInfos,
                        isLoading = uiState.isLoading,
                        error = uiState.error,
                        onCardClick = { cardId ->
                            viewModel.selectCard(cardId)
                            currentScreen = Screen.Detail
                        },
                        onDismissError = { viewModel.clearError() }
                    )
                    Screen.Detail -> CardDetailScreen(
                        card = uiState.selectedCardDetails,
                        isLoading = uiState.isLoading,
                        onBack = {
                            viewModel.clearSelectedCard()
                            currentScreen = Screen.List
                        },
                        onEdit = { currentScreen = Screen.Edit }
                    )
                    Screen.Edit -> EditCardFlow( // NEU: Der Bearbeitungsdialog/screen
                        viewModel = viewModel,
                        onDismiss = { currentScreen = Screen.Detail } // Zurück zum Detail-Screen
                    )
                }

                // Zeigt den "Karte hinzufügen"-Dialog an, wenn showAddCardDialog true ist.
                if (showAddCardDialog) {
                    AddCardFlow(
                        viewModel = viewModel,
                        onDismiss = {
                            viewModel.resetApiCardDetails() // Setzt den API-Zustand zurück
                            showAddCardDialog = false
                        }
                    )
                }
            }
        }
    }
}
