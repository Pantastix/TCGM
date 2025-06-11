package de.pantastix.project.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.pantastix.project.model.PokemonCard
import kotlin.math.roundToInt
import de.pantastix.project.ui.util.formatPrice

@Composable
fun CardListScreen(
    cards: List<PokemonCard>,
    isLoading: Boolean,
    error: String?,
    onCardClick: (PokemonCard) -> Unit,
    onDismissError: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (error != null) {
            AlertDialog(
                onDismissRequest = onDismissError,
                title = { Text("Fehler") },
                text = { Text(error) },
                confirmButton = {
                    Button(onClick = onDismissError) { Text("OK") }
                }
            )
        } else if (cards.isEmpty()) {
            Text(
                "Noch keine Karten vorhanden. Füge eine neue hinzu!",
                modifier = Modifier.align(Alignment.Center).padding(16.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(cards, key = { card -> card.id ?: card.cardMarketLink }) { card ->
                    CardRowItem(card = card, onClick = { onCardClick(card) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardRowItem(card: PokemonCard, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Hier könntest du später ein Vorschaubild einfügen (card.imagePath)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = card.name, style = MaterialTheme.typography.titleMedium)
                Text(text = card.setName, style = MaterialTheme.typography.bodySmall)
                Text(text = "Anzahl: ${card.ownedCopies}", style = MaterialTheme.typography.bodySmall)
            }
            // Optional: Preis anzeigen
            card.currentPrice?.let { price ->
                // Ersetze die alte .format() Zeile durch unsere neue Funktion
                Text(text = formatPrice(price), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}