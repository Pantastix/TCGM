package de.pantastix.project.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.pantastix.project.model.api.TcgDexCardResponse
import androidx.compose.ui.Alignment

@Composable
fun FinalAddCardScreen(
    cardDetails: TcgDexCardResponse,
    isLoading: Boolean,
    onConfirm: (TcgDexCardResponse) -> Unit,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Karte gefunden!", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Text("Name: ${cardDetails.name}")
        Text("Set: ${cardDetails.set.name}")
        Text("Nummer: ${cardDetails.localId}")
        Text("Seltenheit: ${cardDetails.rarity ?: "N/A"}")

        Spacer(Modifier.weight(1f))

        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
            if (isLoading) {
                CircularProgressIndicator()
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onCancel, modifier = Modifier.weight(1f)) {
                        Text("Abbrechen")
                    }
                    Button(onClick = { onConfirm(cardDetails) }, modifier = Modifier.weight(1f)) {
                        Text("Speichern")
                    }
                }
            }
        }
    }
}