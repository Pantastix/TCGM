package de.pantastix.project.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import de.pantastix.project.ui.viewmodel.CardListViewModel
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import de.pantastix.project.platform.Platform
import de.pantastix.project.shared.resources.MR
import de.pantastix.project.ui.flow.AddCardFlow
import de.pantastix.project.ui.screens.*
import dev.icerock.moko.resources.compose.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Preview
@Composable
fun DesktopApp(
    viewModel: CardListViewModel,
    currentScreen: MainScreen,
    onScreenSelect: (MainScreen) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddCardDialog by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }

    val isDetailPaneVisible = uiState.selectedCardDetails != null && currentScreen == MainScreen.COLLECTION
    val uriHandler = LocalUriHandler.current

    val detailPaneWidth by animateDpAsState(
        targetValue = if (isDetailPaneVisible) 500.dp else 0.dp,
        animationSpec = tween(durationMillis = 400),
        label = "detailPaneWidthAnimation"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Row {
            NavigationRail {
                NavigationRailItem(
                    icon = {
                        Icon(
                            Icons.Default.Collections,
                            contentDescription = stringResource(MR.strings.nav_collection),
                        )
                    },
                    label = {
                        Text(
                            stringResource(MR.strings.nav_collection),
                            color = if (currentScreen == MainScreen.COLLECTION) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Unspecified
                            }
                        )
                    },
                    selected = currentScreen == MainScreen.COLLECTION,
                    onClick = { if (!uiState.isLoading) onScreenSelect(MainScreen.COLLECTION) },
                    colors = NavigationRailItemDefaults.colors(
                        indicatorColor = if (currentScreen == MainScreen.COLLECTION) MaterialTheme.colorScheme.primary.copy(
                            alpha = 0.1f
                        ) else Color.Transparent
                    )
                )
                NavigationRailItem(
                    icon = {
                        Icon(
                            Icons.Default.Analytics,
                            contentDescription = stringResource(MR.strings.nav_value),
                        )
                    },
                    label = {
                        Text(
                            stringResource(MR.strings.nav_value),
                            color = if (currentScreen == MainScreen.VALUE) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Unspecified
                            }
                        )
                    },
                    selected = currentScreen == MainScreen.VALUE,
                    onClick = { if (!uiState.isLoading) onScreenSelect(MainScreen.VALUE) },
                    colors = NavigationRailItemDefaults.colors(
                        indicatorColor = if (currentScreen == MainScreen.VALUE) MaterialTheme.colorScheme.primary.copy(
                            alpha = 0.1f
                        ) else Color.Transparent
                    )
                )
                NavigationRailItem(
                    icon = {
                        Icon(
                            Icons.Default.ImportExport,
                            contentDescription = stringResource(MR.strings.nav_export),
                        )
                    },
                    label = {
                        Text(
                            stringResource(MR.strings.nav_export),
                            color = if (currentScreen == MainScreen.EXPORT) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Unspecified
                            }
                        )
                    },
                    selected = currentScreen == MainScreen.EXPORT,
                    onClick = { if (!uiState.isLoading) onScreenSelect(MainScreen.EXPORT) },
                    colors = NavigationRailItemDefaults.colors(
                        indicatorColor = if (currentScreen == MainScreen.EXPORT) MaterialTheme.colorScheme.primary.copy(
                            alpha = 0.1f
                        ) else Color.Transparent
                    )
                )

                Spacer(Modifier.weight(1f))

                NavigationRailItem(
                    icon = {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(MR.strings.nav_settings),
                        )
                    },
                    label = {
                        Text(
                            stringResource(MR.strings.nav_settings),
                            color = if (currentScreen == MainScreen.SETTINGS) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Unspecified
                            }
                        )
                    },
                    selected = currentScreen == MainScreen.SETTINGS,
                    onClick = { if (!uiState.isLoading) onScreenSelect(MainScreen.SETTINGS) },
                    colors = NavigationRailItemDefaults.colors(
                        indicatorColor = if (currentScreen == MainScreen.SETTINGS) MaterialTheme.colorScheme.primary.copy(
                            alpha = 0.1f
                        ) else Color.Transparent
                    )
                )
            }

            VerticalDivider(
                modifier = Modifier
                    .fillMaxHeight(),
                thickness = 4.dp,
                color = MaterialTheme.colorScheme.primary
            )


            Box(modifier = Modifier.weight(1f)) {
                when (currentScreen) {
                    MainScreen.COLLECTION -> CardCollectionScreen(
                        viewModel = viewModel,
                        onAddCardClick = { showAddCardDialog = true },
                        onCardClick = { cardId -> viewModel.selectCard(cardId) }
                    )

                    MainScreen.VALUE -> ValueScreen()
                    MainScreen.SETTINGS -> SettingsScreen(
                        viewModel = viewModel,
                        onNavigateToGuide = { onScreenSelect(MainScreen.SUPABASE_GUIDE) }
                    )

                    MainScreen.SUPABASE_GUIDE -> SupabaseGuideScreen(
                        onBack = { onScreenSelect(MainScreen.SETTINGS) }
                    )

                    MainScreen.EXPORT -> ExportScreen(viewModel = viewModel)
                }
            }

            // Animierte Detailansicht auf der rechten Seite (rechte Spalte)
            if (detailPaneWidth > 0.dp) {
                Row(
                    modifier = Modifier.width(detailPaneWidth) // Feste, animierte Breite
                ) {
                    HorizontalDivider(
                        modifier = Modifier.fillMaxHeight().width(1.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                    key(uiState.selectedCardDetails?.id) {
                        if (isEditing) {
                            EditCardScreen(
                                card = uiState.selectedCardDetails!!,
                                isLoading = uiState.isEditingDetailsLoading,
                                apiDetails = uiState.editingCardApiDetails,
                                onSave = { id, copies, notes, price, priceSource ->
                                    viewModel.updateCard(id, copies, notes, price, priceSource)
                                    isEditing = false // Nach dem Speichern zurück in den Ansichtsmodus
                                    viewModel.clearEditingDetails() // Zustand aufräumen
                                },
                                onCancel = { isEditing = false }, // Zurück in den Ansichtsmodus
                                onLoadPrices = viewModel::fetchPriceDetailsForEditing
                            )
                        } else {
                            CardDetailScreen(
                                card = uiState.selectedCardDetails,
                                isLoading = uiState.isLoading,
                                onBack = { viewModel.clearSelectedCard() },
                                onEdit = { isEditing = true }, // Wechselt in den Bearbeitungsmodus
                                onDelete = { viewModel.deleteSelectedCard() }, // Ruft die neue Löschfunktion auf
                                onRefreshPrice = { cardToRefresh -> viewModel.refreshCardPrice(cardToRefresh) }
                            )
                        }
                    }
                }
            }
        }
    }

    uiState.updateInfo?.let { update ->
        AlertDialog(
            modifier = Modifier.border(4.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large),
            onDismissRequest = { /* Modal */ },
            title = { Text("Update verfügbar!") },
            text = { Text("Eine neue Version (${update.version}) ist verfügbar. Möchten Sie sie jetzt installieren?") },
            confirmButton = {
                if (update.platform in listOf(Platform.Windows, Platform.Mac, Platform.Linux)) {
                    Button(onClick = { viewModel.startUpdate(update.downloadUrl) }) {
                        Text("Jetzt aktualisieren")
                    }
                } else { // Für Android etc.
                    Button(onClick = { uriHandler.openUri(update.downloadUrl) }) {
                        Text("Herunterladen")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.dismissUpdateDialog()
                }) {
                    Text("Später")
                }
            }
        )
    }


    if (showAddCardDialog) {
        AddCardFlow(
            viewModel = viewModel,
            onDismiss = { showAddCardDialog = false }
        )
    }
}