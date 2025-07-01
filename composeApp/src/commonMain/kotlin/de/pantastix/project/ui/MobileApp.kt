package de.pantastix.project.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.pantastix.project.ui.screens.CardCollectionScreen
import de.pantastix.project.ui.screens.SettingsScreen
import de.pantastix.project.ui.screens.ValueScreen
import de.pantastix.project.ui.viewmodel.CardListViewModel

@Composable
fun MobileApp(
    viewModel: CardListViewModel,
    currentScreen: MainScreen,
    onScreenSelect: (MainScreen) -> Unit
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Collections, contentDescription = "Sammlung") },
                    label = { Text("Sammlung") },
                    selected = currentScreen == MainScreen.COLLECTION,
                    onClick = { onScreenSelect(MainScreen.COLLECTION) }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Analytics, contentDescription = "Wert") },
                    label = { Text("Wert") },
                    selected = currentScreen == MainScreen.VALUE,
                    onClick = { onScreenSelect(MainScreen.VALUE) }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Einstellungen") },
                    label = { Text("Einstellungen") },
                    selected = currentScreen == MainScreen.SETTINGS,
                    onClick = { onScreenSelect(MainScreen.SETTINGS) }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentScreen) {
                MainScreen.COLLECTION -> CardCollectionScreen(viewModel)
                MainScreen.VALUE -> ValueScreen()
                MainScreen.SETTINGS -> SettingsScreen()
            }
        }
    }
}