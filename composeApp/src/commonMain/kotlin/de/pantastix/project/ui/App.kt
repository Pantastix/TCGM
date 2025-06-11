package de.pantastix.project.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.pantastix.project.ui.screens.AddCardScreen
import de.pantastix.project.ui.screens.CardDetailScreen
import de.pantastix.project.ui.screens.CardListScreen
import de.pantastix.project.ui.viewmodel.CardListViewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject

import simon.composeapp.generated.resources.Res
import simon.composeapp.generated.resources.compose_multiplatform

enum class Screen {
    List, Detail, Add
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(viewModel: CardListViewModel = koinInject()) { // ViewModel-Injection mit Koin
    var currentScreen by remember { mutableStateOf(Screen.List) }

    // Koin-Erklärung:
    // `koinInject<CardListViewModel>()` ist eine Funktion von Koin für Compose.
    // Sie sucht im Koin-Modul (das wir in Main.kt initialisiert haben) nach einer
    // Definition für `CardListViewModel`. Koin erstellt dann eine Instanz davon
    // (oder gibt eine bestehende zurück, je nach Definition z.B. `single` vs `factory`).
    // Alle Abhängigkeiten des ViewModels (hier `CardRepository`) werden von Koin
    // automatisch aufgelöst und injiziert.

    val cards by viewModel.cards.collectAsState()
    val selectedCard by viewModel.selectedCard.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    MaterialTheme { // Stellt das Material Design Theme bereit
        Scaffold( // Grundgerüst für Material Design (bietet Platz für TopBar, FAB, etc.)
            topBar = {
                TopAppBar(
                    title = { Text("Pokémon Card Collector") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            },
            floatingActionButton = {
                if (currentScreen == Screen.List) { // FAB nur im Listenscreen anzeigen
                    FloatingActionButton(onClick = {
                        currentScreen = Screen.Add
                    }) {
                        Icon(Icons.Filled.Add, "Neue Karte hinzufügen")
                    }
                }
            }
        ) { innerPadding -> // Padding, das vom Scaffold für TopBar etc. bereitgestellt wird
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                when (currentScreen) {
                    Screen.List -> CardListScreen(
                        cards = cards,
                        isLoading = isLoading,
                        error = error,
                        onCardClick = { card ->
                            viewModel.selectCard(card)
                            currentScreen = Screen.Detail
                        },
                        onDismissError = { viewModel.clearError() }
                    )
                    Screen.Detail -> CardDetailScreen(
                        card = selectedCard,
                        onBack = {
                            viewModel.clearSelectedCard()
                            currentScreen = Screen.List
                        },
                        onDelete = { cardId ->
                            viewModel.deleteCard(cardId)
                            currentScreen = Screen.List // Zurück zur Liste nach dem Löschen
                        }
                    )
                    Screen.Add -> AddCardScreen(
                        onAddCard = { name, setName, cardNumber, language, cardMarketLink, price, lastUpdate, imagePath, copies ->
                            viewModel.addCard(name, setName, cardNumber, language, cardMarketLink, price, lastUpdate, imagePath, copies)
                            currentScreen = Screen.List // Zurück zur Liste nach dem Hinzufügen
                        },
                        onBack = { currentScreen = Screen.List }
                    )
                }
            }
        }
    }
}