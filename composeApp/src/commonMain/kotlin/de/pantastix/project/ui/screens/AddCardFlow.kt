package de.pantastix.project.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import de.pantastix.project.platform.Platform
import de.pantastix.project.platform.getPlatform
import de.pantastix.project.ui.viewmodel.CardListViewModel

@Composable
fun AddCardFlow(
    viewModel: CardListViewModel,
    onDismiss: () -> Unit
) {
    val platform = getPlatform() // Holt die aktuelle Plattform (Desktop, Android, etc.)

    // Adaptive UI: Dialog für Desktop, sonst eine Vollbild-Box (simuliert eine neue Seite)
    when (platform) {
        Platform.Desktop -> {
            Dialog(onDismissRequest = onDismiss) {
                // Der Dialog bekommt einen eigenen Hintergrund, damit er wie ein Popup aussieht
                Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)) {
                    AddCardContent(viewModel = viewModel, onCardAdded = onDismiss)
                }
            }
        }
        else -> { // Android, iOS, etc.
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                AddCardContent(viewModel = viewModel, onCardAdded = onDismiss)
            }
        }
    }
}


@Composable
fun AddCardContent(
    viewModel: CardListViewModel,
    onCardAdded: () -> Unit
) {
    val apiCardDetails by viewModel.apiCardDetails.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // Entscheidet, ob der Set-Auswahl-Screen oder der Detail-Eingabe-Screen gezeigt wird.
    if (apiCardDetails == null) {
        SetSelectionScreen(
            viewModel = viewModel
        )
    } else {
        // Die API hat eine Karte gefunden, jetzt zeigen wir den Bestätigungs-/Bearbeitungs-Screen an.
        FinalAddCardScreen(
            cardDetails = apiCardDetails!!,
            isLoading = isLoading,
            onConfirm = { germanDetails ->
                viewModel.confirmAndSaveCard(germanDetails)
                onCardAdded()
            },
            onCancel = { viewModel.resetApiCardDetails() }
        )
    }
}