package de.pantastix.project.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.Modifier
import de.pantastix.project.ui.viewmodel.CardListViewModel
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import de.pantastix.project.ui.screens.*

@Composable
fun DesktopApp(
    viewModel: CardListViewModel,
    currentScreen: MainScreen,
    onScreenSelect: (MainScreen) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddCardDialog by remember { mutableStateOf(false) }

    val detailPaneWeight by animateFloatAsState(
        targetValue = if (uiState.selectedCardDetails != null && currentScreen == MainScreen.COLLECTION) 0.4f else 0f,
        animationSpec = tween(durationMillis = 500)
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Row {
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


            Box(modifier = Modifier.weight(1f - detailPaneWeight)) {
                when (currentScreen) {
                    MainScreen.COLLECTION -> CardCollectionScreen(
                        viewModel = viewModel,
                        onAddCardClick = { showAddCardDialog = true },
                        onCardClick = { cardId -> viewModel.selectCard(cardId) }
                    )
                    MainScreen.VALUE -> ValueScreen()
                    MainScreen.SETTINGS -> SettingsScreen()
                }
            }

            // Animierte Detailansicht auf der rechten Seite (rechte Spalte)
            if (detailPaneWeight > 0f) {
                Row(modifier = Modifier.weight(detailPaneWeight)) {
                    HorizontalDivider(modifier = Modifier.fillMaxHeight().width(1.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    key(uiState.selectedCardDetails?.id) {
                        CardDetailScreen(
                            card = uiState.selectedCardDetails,
                            isLoading = uiState.isLoading,
                            onBack = { viewModel.clearSelectedCard() },
                            onEdit = { /* TODO: Edit-Logik hier einfügen */ }
                        )
                    }
                }
            }
        }
    }

    if (showAddCardDialog) {
        AddCardFlow(
            viewModel = viewModel,
            onDismiss = { showAddCardDialog = false }
        )
    }
}