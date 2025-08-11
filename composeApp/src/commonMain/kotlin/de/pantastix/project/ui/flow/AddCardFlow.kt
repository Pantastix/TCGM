package de.pantastix.project.ui.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import de.pantastix.project.platform.Platform
import de.pantastix.project.platform.getPlatform
import de.pantastix.project.ui.screens.FinalAddCardScreen
import de.pantastix.project.ui.screens.SetSelectionScreen
import de.pantastix.project.ui.viewmodel.CardListViewModel

@Composable
fun AddCardFlow(
    viewModel: CardListViewModel,
    onDismiss: () -> Unit
) {
    val platform = getPlatform()

    when (platform) {
        Platform.Windows, Platform.Linux, Platform.Mac -> {
            Dialog(
                onDismissRequest = onDismiss,
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 450.dp)
                        .heightIn(max = 800.dp)
                        .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
                        .border(4.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large)
                ) {
                    AddCardContent(viewModel = viewModel, onCardAdded = onDismiss)
                }
            }
        }

        else -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
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
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.apiCardDetails == null) {
        SetSelectionScreen(viewModel = viewModel)
    } else {
        val setInfo = uiState.sets.find { it.setId == uiState.apiCardDetails!!.set.id }
        FinalAddCardScreen(
            cardDetails = uiState.apiCardDetails!!,
            setInfo = setInfo,
            isLoading = uiState.isLoading,
            onConfirm = { details, name, abbreviation, price, marketLink, quantity, notes ->
                viewModel.confirmAndSaveCard(
                    cardDetails = details.copy(name = name),
                    languageCode = uiState.searchedCardLanguage!!.code,
                    abbreviation = abbreviation,
                    price = price,
                    cardMarketLink = marketLink,
                    ownedCopies = quantity,
                    notes = notes
                )
                onCardAdded()
            },
            onCancel = { viewModel.resetApiCardDetails() }
        )
    }
}