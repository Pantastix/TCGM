package de.pantastix.project.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.pantastix.project.model.PokemonCard
import de.pantastix.project.ui.util.formatPrice

@Composable
fun CardDetailScreen(
    card: PokemonCard?,
    onBack: () -> Unit,
    onDelete: (Long) -> Unit
) {
    if (card == null) {
        // Sollte nicht passieren, wenn die Logik in App.kt stimmt, aber als Fallback
        Text("Keine Karte ausgewählt.", modifier = Modifier.padding(16.dp))
        Button(onClick = onBack, modifier = Modifier.padding(16.dp)) {
            Text("Zurück zur Liste")
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()){
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
            }
            IconButton(onClick = { onDelete(card.id) }) {
                Icon(Icons.Filled.Delete, contentDescription = "Löschen", tint = MaterialTheme.colorScheme.error)
            }
        }

        Text("Name: ${card.nameDe}", style = MaterialTheme.typography.headlineSmall)
        Text("Set: ${card.setName}")
        Text("Kartennr.: ${card.localId}")
        Text("CardMarket Link: ${card.cardMarketLink}")
        Text("Preis: ${card.currentPrice?.let { price -> Text(text = formatPrice(price)) } ?: "N/A"}")
        Text("Letztes Preisupdate: ${card.lastPriceUpdate ?: "N/A"}")
        Text("Anzahl: ${card.ownedCopies}")

        Spacer(modifier = Modifier.weight(1f)) // Drückt den Button nach unten
    }
}