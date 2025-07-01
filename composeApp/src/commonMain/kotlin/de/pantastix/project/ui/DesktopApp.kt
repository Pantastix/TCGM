package de.pantastix.project.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.pantastix.project.ui.screens.CardCollectionScreen
import de.pantastix.project.ui.screens.SettingsScreen
import de.pantastix.project.ui.screens.ValueScreen
import de.pantastix.project.ui.viewmodel.CardListViewModel
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.unit.dp

@Composable
fun DesktopApp(
    viewModel: CardListViewModel,
    currentScreen: MainScreen,
    onScreenSelect: (MainScreen) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Row {
            // Die seitliche Navigationsleiste
            NavigationRail {
                NavigationRailItem(
                    icon = { Icon(Icons.Default.Collections, contentDescription = "Sammlung") },
                    label = { Text("Sammlung") },
                    selected = currentScreen == MainScreen.COLLECTION,
                    onClick = { onScreenSelect(MainScreen.COLLECTION) }
                )
                NavigationRailItem(
                    icon = { Icon(Icons.Default.Analytics, contentDescription = "Wert") },
                    label = { Text("Wert") },
                    selected = currentScreen == MainScreen.VALUE,
                    onClick = { onScreenSelect(MainScreen.VALUE) }
                )

                // Dieser Spacer schiebt das Einstellungs-Icon nach unten
                Spacer(Modifier.weight(1f))

                NavigationRailItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Einstellungen") },
                    label = { Text("Einstellungen") },
                    selected = currentScreen == MainScreen.SETTINGS,
                    onClick = { onScreenSelect(MainScreen.SETTINGS) }
                )
            }

            VerticalDivider(
                modifier = Modifier
                    .fillMaxHeight(),
                thickness = 4.dp,
                color = MaterialTheme.colorScheme.primary
            )


            // Der Hauptinhaltsbereich rechts neben der Leiste
            when (currentScreen) {
                MainScreen.COLLECTION -> CardCollectionScreen(viewModel)
                MainScreen.VALUE -> ValueScreen()
                MainScreen.SETTINGS -> SettingsScreen()
            }
        }
    }
}