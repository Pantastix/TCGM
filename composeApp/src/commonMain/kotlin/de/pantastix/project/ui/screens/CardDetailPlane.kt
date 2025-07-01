package de.pantastix.project.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.pantastix.project.model.PokemonCard

@Composable
fun CardDetailPane(
    card: PokemonCard?,
    isLoading: Boolean,
    onClose: () -> Unit,
    onEdit: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Ruft den bestehenden CardDetailScreen auf, den wir schon haben
        CardDetailScreen(
            card = card,
            isLoading = isLoading,
            onBack = onClose, // Der "Zurück"-Button schließt jetzt die Leiste
            onEdit = onEdit
        )

    }
}