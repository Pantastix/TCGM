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
import de.pantastix.project.ui.screens.AddCardFlow
import de.pantastix.project.ui.screens.AddCardScreen
import de.pantastix.project.ui.screens.CardDetailScreen
import de.pantastix.project.ui.screens.CardListScreen
import de.pantastix.project.ui.viewmodel.CardListViewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject

import simon.composeapp.generated.resources.Res
import simon.composeapp.generated.resources.compose_multiplatform

enum class Screen { List, Detail }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(viewModel: CardListViewModel = koinInject()) {
    var currentScreen by remember { mutableStateOf(Screen.List) }
    var showAddCardDialog by remember { mutableStateOf(false) }

    val cardInfos by viewModel.cardInfos.collectAsState()
    val selectedCard by viewModel.selectedCardDetails.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Pokémon Card Collector") }) },
            floatingActionButton = {
                FloatingActionButton(onClick = { showAddCardDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Neue Karte hinzufügen")
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                when (currentScreen) {
                    Screen.List -> CardListScreen(
                        cardInfos = cardInfos,
                        isLoading = isLoading,
                        error = error,
                        onCardClick = { cardId ->
                            viewModel.selectCard(cardId)
                            currentScreen = Screen.Detail
                        },
                        onDismissError = { viewModel.clearError() }
                    )
                    Screen.Detail -> CardDetailScreen(
                        card = selectedCard,
                        isLoading = isLoading,
                        onBack = {
                            viewModel.clearSelectedCard()
                            currentScreen = Screen.List
                        }
                    )
                }

                if (showAddCardDialog) {
                    AddCardFlow(
                        viewModel = viewModel,
                        onDismiss = {
                            viewModel.resetApiCardDetails()
                            showAddCardDialog = false
                        }
                    )
                }
            }
        }
    }
}