package de.pantastix.project.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import de.pantastix.project.model.PokemonCardInfo
import de.pantastix.project.shared.resources.MR
import dev.icerock.moko.resources.compose.stringResource

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Cached

import de.pantastix.project.ui.components.ErrorDialog
import de.pantastix.project.ui.util.formatPrice
import de.pantastix.project.ui.components.WarningDialog
import de.pantastix.project.ui.viewmodel.CardListViewModel
import androidx.compose.runtime.setValue
import de.pantastix.project.ui.components.AddFilterDialog
import de.pantastix.project.ui.components.BulkUpdateProgressDialog
import de.pantastix.project.ui.components.FilterAndSortChips
import de.pantastix.project.ui.components.FilterAndSortControls
import de.pantastix.project.ui.viewmodel.Sort
import org.jetbrains.compose.ui.tooling.preview.Preview

// Hilfsfunktionen für Übersetzungen
@Composable
fun getFilterDisplayName(attribute: String): String {
    return when (attribute) {
        "nameLocal" -> stringResource(MR.strings.filter_name)
        "setName" -> stringResource(MR.strings.filter_set)
        "language" -> stringResource(MR.strings.filter_language)
        "currentPrice" -> stringResource(MR.strings.filter_price)
        "ownedCopies" -> stringResource(MR.strings.filter_copies)
        else -> attribute
    }
}

@Composable
fun getSortDisplayName(attribute: String): String {
    return when (attribute) {
        "nameLocal" -> stringResource(MR.strings.sort_name)
        "setName" -> stringResource(MR.strings.sort_set)
        "language" -> stringResource(MR.strings.sort_language)
        "currentPrice" -> stringResource(MR.strings.sort_price)
        "ownedCopies" -> stringResource(MR.strings.sort_copies)
        else -> attribute
    }
}

@Composable
fun getLanguageDisplayName(code: String): String {
    return when (code) {
        "de" -> stringResource(MR.strings.language_german)
        "en" -> stringResource(MR.strings.language_english)
        "fr" -> stringResource(MR.strings.language_french)
        "es" -> stringResource(MR.strings.language_spanish)
        "it" -> stringResource(MR.strings.language_italian)
        "pt" -> stringResource(MR.strings.language_portuguese)
        "jp" -> stringResource(MR.strings.language_japanese)
        else -> code
    }
}

@Preview
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CardCollectionScreen(
    viewModel: CardListViewModel,
    onAddCardClick: () -> Unit,
    onCardClick: (Long) -> Unit
) {

    val maxFilterAmount = 3
    val uiState by viewModel.uiState.collectAsState()
    val availableLanguages by viewModel.availableLanguages.collectAsState()
    var showAddFilterDialog by remember { mutableStateOf(false) }
    var showConfirmUpdateCardsDialog by remember { mutableStateOf(false) }

    val shouldShowChips = uiState.filters.isNotEmpty() || uiState.sort.sortBy != "nameLocal"

    println("UI: Card Infos size: ${uiState.cardInfos.size}, isSupabaseConnected: ${uiState.isSupabaseConnected}")

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row {
                val largeCorner = 50
                val smallCorner = 10
                val leftButtonShape = RoundedCornerShape(
                    topStartPercent = largeCorner,
                    bottomStartPercent = largeCorner,
                    topEndPercent = smallCorner,
                    bottomEndPercent = smallCorner
                )
                val rightButtonShape = RoundedCornerShape(
                    topStartPercent = smallCorner,
                    bottomStartPercent = smallCorner,
                    topEndPercent = largeCorner,
                    bottomEndPercent = largeCorner
                )

                Button(
                    onClick = onAddCardClick,
                    shape = leftButtonShape,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(MR.strings.collection_add_card_button_desc)
                        )
                    }
                    Text(stringResource(MR.strings.collection_add_card_button))
                }
                Spacer(modifier = Modifier.width(4.dp))
//                if (uiState.canBulkUpdatePrices) {

                Button(
                    onClick = { showConfirmUpdateCardsDialog = true },
                    enabled = uiState.canBulkUpdatePrices,
                    shape = rightButtonShape,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.offset(x = (-1).dp),
                    border = if (!uiState.canBulkUpdatePrices) {
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        null
                    }
                ) {
                    Icon(
                        Icons.Filled.Cached,
                        contentDescription = stringResource(MR.strings.collection_reload_prices_button)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(MR.strings.collection_reload_prices_button))
                }
//                }
            }
            // Fill entwire Space between items


//            FilterAndSortControls(
//                sort = uiState.sort,
//                isAddFilterEnabled = uiState.filters.size < maxFilterAmount, // Button deaktivieren, wenn 3 Filter aktiv sind
//                onAddFilter = { showAddFilterDialog = true },
//                onUpdateSort = { viewModel.updateSort(it) },
//            )
        }

//        AnimatedVisibility(
//            visible = shouldShowChips,
//            enter = expandVertically(
//                animationSpec = spring(
//                    dampingRatio = Spring.DampingRatioMediumBouncy,
//                    stiffness = Spring.StiffnessLow
//                )
//            ),
//            exit = shrinkVertically(
//                animationSpec = spring(
//                    dampingRatio = Spring.DampingRatioMediumBouncy,
//                    stiffness = Spring.StiffnessLow
//                )
//            )
//        ) {
//            FilterAndSortChips(
//                filters = uiState.filters,
//                sort = uiState.sort,
//                onRemoveFilter = { viewModel.removeFilter(it) },
//                onResetSort = { viewModel.updateSort(Sort("nameLocal", true)) }
//            )
//        }

        HorizontalDivider(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            thickness = 4.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
        )

        if (uiState.isLoading && uiState.cardInfos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LoadingIndicator()
                    if (uiState.loadingMessage != null) {
                        Text(
                            text = uiState.loadingMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        } else {
            // Die Kachelansicht für die Karten
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(uiState.cardInfos, key = { it.id }) { cardInfo ->
                    CardGridItem(cardInfo = cardInfo, onClick = { onCardClick(cardInfo.id) })
                }
            }
        }
    }

    uiState.error?.let { message ->
        ErrorDialog(
            message = message,
            onDismiss = { viewModel.clearError() }
        )
    }

    uiState.setsUpdateWarning?.let { message ->
        WarningDialog(
            message = message,
            onDismiss = { viewModel.dismissSetsUpdateWarning() }
        )
    }

    if (showAddFilterDialog) {
        AddFilterDialog(
            availableLanguages = availableLanguages,
            onDismiss = { showAddFilterDialog = false },
            onAddFilter = { filter ->
                viewModel.addFilter(filter)
                showAddFilterDialog = false
            }
        )
    }

    if (showConfirmUpdateCardsDialog) {
        AlertDialog(
            modifier = Modifier.border(4.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large),
            onDismissRequest = { showConfirmUpdateCardsDialog = false },
            title = { Text(stringResource(MR.strings.warning)) },
            text = { Text(stringResource(MR.strings.collection_reload_prices_button_desc)) },
            confirmButton = {
                Button(onClick = {
                    viewModel.startBulkPriceUpdate()
                    showConfirmUpdateCardsDialog = false
                }) { Text(stringResource(MR.strings.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmUpdateCardsDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    // NEU: Anzeige des Fortschrittsdialogs
    if (uiState.bulkUpdateProgress.inProgress) {
        BulkUpdateProgressDialog(progress = uiState.bulkUpdateProgress)
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
                contentDescription = cardInfo.nameLocal,
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
                    text = cardInfo.nameLocal,
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