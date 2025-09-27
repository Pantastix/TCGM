package de.pantastix.project.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Sort
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

import de.pantastix.project.ui.components.ErrorDialog
import de.pantastix.project.ui.util.formatPrice
import de.pantastix.project.ui.components.WarningDialog
import de.pantastix.project.ui.viewmodel.CardListViewModel
import androidx.compose.runtime.setValue
import de.pantastix.project.ui.components.AddFilterDialog
import de.pantastix.project.ui.components.FilterAndSortControls
import de.pantastix.project.ui.viewmodel.Filter
import de.pantastix.project.ui.viewmodel.Sort

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

// Erweiterte Filter- und Sortieroptionen
val filterAttributes = listOf("nameLocal", "setName", "language", "currentPrice", "ownedCopies")
val sortAttributes = listOf("nameLocal", "setName", "language", "currentPrice", "ownedCopies")

@Composable
fun CardCollectionScreen(
    viewModel: CardListViewModel,
    onAddCardClick: () -> Unit,
    onCardClick: (Long) -> Unit
) {

    val uiState by viewModel.uiState.collectAsState()
    var showAddFilterDialog by remember { mutableStateOf(false) }

    println("UI: Card Infos size: ${uiState.cardInfos.size}, isSupabaseConnected: ${uiState.isSupabaseConnected}")

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = onAddCardClick) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(MR.strings.collection_add_card_button_desc))
                }
                Text(stringResource(MR.strings.collection_add_card_button))
            }
        }
        FilterAndSortControls(
            filters = uiState.filters,
            sort = uiState.sort,
            onAddFilter = { showAddFilterDialog = true },
            onUpdateSort = { viewModel.updateSort(it) },
            onRemoveFilter = { viewModel.removeFilter(it) },
            onResetSort = { viewModel.updateSort(Sort("nameLocal", true)) }
        )

        HorizontalDivider(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            thickness = 4.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
        )

        if (uiState.isLoading && uiState.cardInfos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
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
            onDismiss = { showAddFilterDialog = false },
            onAddFilter = { filter ->
                viewModel.addFilter(filter)
                showAddFilterDialog = false
            }
        )
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

@Composable
fun FilterAndSortControls(
    filters: List<Filter>,
    sort: Sort,
    onAddFilter: () -> Unit,
    onUpdateSort: (Sort) -> Unit,
    onRemoveFilter: (Filter) -> Unit,
    onResetSort: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End // Buttons rechts
    ) {
        // Chips für aktive Filter und Sortierungen
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.Start) {
            filters.forEach { filter ->
                FilterChip(
                    onClick = { onRemoveFilter(filter) },
                    label = {
                        Text(getFilterDisplayName(filter.attribute))
                    },
                    selected = true
                )
            }
            if (sort.sortBy.isNotEmpty()) {
                FilterChip(
                    onClick = { onResetSort() },
                    label = {
                        Text(getSortDisplayName(sort.sortBy))
                    },
                    selected = true
                )
            }
        }
        // Buttons für Filter und Sortierung
        Column(horizontalAlignment = Alignment.End) {
            IconButton(onClick = onAddFilter) {
                Icon(Icons.Default.FilterList, contentDescription = stringResource(MR.strings.filter_button_desc))
            }
            Text(stringResource(MR.strings.filter_button_text), style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.height(4.dp))
            IconButton(onClick = { /* Sortiermenü öffnen */ }) {
                Icon(Icons.Default.Sort, contentDescription = stringResource(MR.strings.sort_button_desc))
            }
            Text(stringResource(MR.strings.sort_button_text), style = MaterialTheme.typography.labelSmall)
        }
    }
}

// Dialog für Filterauswahl inkl. Preis- und Kopienfilter
@Composable
fun AddFilterDialog(
    onDismiss: () -> Unit,
    onAddFilter: (Filter) -> Unit
) {
    var selectedAttribute by remember { mutableStateOf(filterAttributes.first()) }
    var selectedLanguage by remember { mutableStateOf("de") }
    var priceValue by remember { mutableStateOf(0.0) }
    var priceType by remember { mutableStateOf("under") }
    var copiesValue by remember { mutableStateOf(1) }
    var copiesType by remember { mutableStateOf("min") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.filter_dialog_title)) },
        text = {
            Column {
                // Dropdown für Attributauswahl
                DropdownMenu(
                    expanded = true,
                    onDismissRequest = {},
                ) {
                    filterAttributes.forEach { attr ->
                        DropdownMenuItem(
                            onClick = { selectedAttribute = attr },
                            text = { Text(getFilterDisplayName(attr)) }
                        )
                    }
                }
                // Spezialfelder je nach Attribut
                when (selectedAttribute) {
                    "language" -> {
                        DropdownMenu(expanded = true, onDismissRequest = {}) {
                            listOf("de", "en", "fr", "es", "it", "pt", "jp").forEach { code ->
                                DropdownMenuItem(
                                    onClick = { selectedLanguage = code },
                                    text = { Text(getLanguageDisplayName(code)) }
                                )
                            }
                        }
                    }
                    "currentPrice" -> {
                        Row {
                            DropdownMenu(expanded = true, onDismissRequest = {}) {
                                DropdownMenuItem(onClick = { priceType = "under" }, text = { Text(stringResource(MR.strings.filter_price_under)) })
                                DropdownMenuItem(onClick = { priceType = "over" }, text = { Text(stringResource(MR.strings.filter_price_over)) })
                            }
                            TextField(value = priceValue.toString(), onValueChange = { priceValue = it.toDoubleOrNull() ?: 0.0 })
                        }
                    }
                    "ownedCopies" -> {
                        Row {
                            DropdownMenu(expanded = true, onDismissRequest = {}) {
                                DropdownMenuItem(onClick = { copiesType = "min" }, text = { Text(stringResource(MR.strings.filter_copies_min)) })
                                DropdownMenuItem(onClick = { copiesType = "max" }, text = { Text(stringResource(MR.strings.filter_copies_max)) })
                            }
                            TextField(value = copiesValue.toString(), onValueChange = { copiesValue = it.toIntOrNull() ?: 1 })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                // Filterobjekt erstellen und zurückgeben
                val filter = when (selectedAttribute) {
                    "language" -> Filter("language", selectedLanguage)
                    "currentPrice" -> Filter("currentPrice", "${priceType}:${priceValue}")
                    "ownedCopies" -> Filter("ownedCopies", "${copiesType}:${copiesValue}")
                    else -> Filter(selectedAttribute, "")
                }
                onAddFilter(filter)
            }) {
                Text(stringResource(MR.strings.filter_dialog_confirm))
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(MR.strings.filter_dialog_cancel))
            }
        }
    )
}
