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
import de.pantastix.project.ui.screens.EditCardScreen
import de.pantastix.project.ui.viewmodel.CardListViewModel

@Composable
fun EditCardFlow(
    viewModel: CardListViewModel,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val cardToEdit = uiState.selectedCardDetails

    if (cardToEdit != null) {
        val platform = getPlatform()
        when (platform) {
            Platform.Windows, Platform.Linux, Platform.Mac -> {
                Dialog(onDismissRequest = onDismiss) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 450.dp)
                            .heightIn(max = 600.dp)
                            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
                            .border(4.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large)
                    ) {
                        EditCardScreen(
                            card = cardToEdit,
                            onSave = { id, copies, notes, price ->
                                viewModel.updateCard(id, copies, notes, price)
                                onDismiss()
                            },
                            onCancel = onDismiss
                        )
                    }
                }
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    EditCardScreen(
                        card = cardToEdit,
                        onSave = { id, copies, notes, price ->
                            viewModel.updateCard(id, copies, notes, price)
                            onDismiss()
                        },
                        onCancel = onDismiss
                    )
                }
            }
        }
    }
}