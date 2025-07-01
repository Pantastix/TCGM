package de.pantastix.project.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.pantastix.project.model.PokemonCardInfo
import de.pantastix.project.ui.util.formatPrice

@Composable
fun CardListScreen(
    cardInfos: List<PokemonCardInfo>,
    isLoading: Boolean,
    error: String?,
    onCardClick: (Long) -> Unit,
    onDismissError: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading && cardInfos.isEmpty()) { // Ladeanzeige nur, wenn die Liste noch leer ist
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (error != null) {
            AlertDialog(
                onDismissRequest = onDismissError,
                title = { Text("Fehler") },
                text = { Text(error) },
                confirmButton = { Button(onClick = onDismissError) { Text("OK") } },
                modifier = Modifier.border(4.dp, MaterialTheme.colorScheme.error, MaterialTheme.shapes.large)
            )
        } else if (cardInfos.isEmpty()) {
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
                items(cardInfos, key = { cardInfo -> cardInfo.id }) { cardInfo ->
                    CardRowItem(cardInfo = cardInfo, onClick = { onCardClick(cardInfo.id) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardRowItem(cardInfo: PokemonCardInfo, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            // Hier kann später ein Bild mit AsyncImage geladen werden
            // AsyncImage(model = cardInfo.imageUrl, ...)

            Column(modifier = Modifier.weight(1f)) {
                Text(text = cardInfo.nameDe, style = MaterialTheme.typography.titleMedium)
                Text(text = cardInfo.setName, style = MaterialTheme.typography.bodySmall)
                Text(text = "Anzahl: ${cardInfo.ownedCopies}", style = MaterialTheme.typography.bodySmall)
            }
            cardInfo.currentPrice?.let { price ->
                Text(text = formatPrice(price), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}