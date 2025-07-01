package de.pantastix.project.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import de.pantastix.project.ui.screens.*
import de.pantastix.project.ui.viewmodel.CardListViewModel

@Composable
fun MobileApp(viewModel: CardListViewModel) {
    var currentScreen by remember { mutableStateOf(MainScreen.COLLECTION) }
    var showAddCardDialog by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Collections, "Sammlung") },
                    label = { Text("Sammlung") },
                    selected = currentScreen == MainScreen.COLLECTION,
                    onClick = { currentScreen = MainScreen.COLLECTION }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Analytics, "Wert") },
                    label = { Text("Wert") },
                    selected = currentScreen == MainScreen.VALUE,
                    onClick = { currentScreen = MainScreen.VALUE }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, "Einstellungen") },
                    label = { Text("Einstellungen") },
                    selected = currentScreen == MainScreen.SETTINGS,
                    onClick = { currentScreen = MainScreen.SETTINGS }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddCardDialog = true }) {
                Icon(Icons.Default.Add, "Karte hinzufügen")
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentScreen) {
                MainScreen.COLLECTION -> CardCollectionScreen(
                    viewModel = viewModel,
                    onAddCardClick = { showAddCardDialog = true },
                    onCardClick = { /* TODO: Navigation zur Detailseite auf Mobile */ }
                )
                MainScreen.VALUE -> ValueScreen()
                MainScreen.SETTINGS -> SettingsScreen()
            }
        }
        if (showAddCardDialog) {
            AddCardFlow(viewModel = viewModel, onDismiss = { showAddCardDialog = false })
        }
    }
}