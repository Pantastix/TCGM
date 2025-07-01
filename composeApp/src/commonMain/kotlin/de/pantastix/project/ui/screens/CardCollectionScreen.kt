package de.pantastix.project.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import de.pantastix.project.model.PokemonCardInfo
import de.pantastix.project.ui.util.formatPrice
import de.pantastix.project.ui.viewmodel.CardListViewModel

@Composable
fun CardCollectionScreen(viewModel: CardListViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // TODO: Hier später die Filterleiste einfügen
        Box(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Filter (Platzhalter)", style = MaterialTheme.typography.bodySmall)
        }

        if (uiState.isLoading && uiState.cardInfos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            // Die Kachelansicht für die Karten
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp), // Passt die Anzahl der Spalten an die Bildschirmgröße an
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(uiState.cardInfos, key = { it.id }) { cardInfo ->
                    CardGridItem(cardInfo = cardInfo, onClick = { /* TODO: Detailansicht öffnen */ })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardGridItem(cardInfo: PokemonCardInfo, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            AsyncImage(
                model = cardInfo.imageUrl,
                contentDescription = cardInfo.nameDe,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f), // Typisches Seitenverhältnis einer Pokémon-Karte
                // TODO: Platzhalterbild hinzufügen, z.B. mit painterResource()
            )
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = cardInfo.nameDe,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = cardInfo.currentPrice?.let { formatPrice(it) } ?: "---",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "x${cardInfo.ownedCopies}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}