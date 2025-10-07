package de.pantastix.project.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.pantastix.project.shared.resources.MR
import de.pantastix.project.ui.util.getAttributeDisplayName
import de.pantastix.project.ui.viewmodel.ComparisonType
import de.pantastix.project.ui.viewmodel.FilterCondition
import de.pantastix.project.ui.viewmodel.Sort
import dev.icerock.moko.resources.compose.stringResource
import java.util.Locale

@Composable
private fun getFilterConditionDisplayName(filter: FilterCondition): String {
    return when (filter) {
        is FilterCondition.ByName -> "Name: \"${filter.nameQuery}\""
        is FilterCondition.ByLanguage -> "Sprache: ${getLanguageDisplayName(filter.languageCode)}"
        is FilterCondition.ByNumericValue -> {
            val attr = when (filter.attribute) {
                de.pantastix.project.ui.viewmodel.NumericAttribute.OWNED_COPIES -> "Menge"
                de.pantastix.project.ui.viewmodel.NumericAttribute.CURRENT_PRICE -> "Preis"
            }
            val comp = when (filter.comparison) {
                ComparisonType.EQUAL -> "="
                ComparisonType.GREATER_THAN -> ">"
                ComparisonType.LESS_THAN -> "<"
            }
            val value = if (filter.attribute == de.pantastix.project.ui.viewmodel.NumericAttribute.OWNED_COPIES)
                filter.value.toInt().toString() else "%.2f".format(Locale.ROOT, filter.value)
            "$attr $comp $value"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterChip(
    filter: FilterCondition,
    onRemove: () -> Unit
) {
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(getFilterConditionDisplayName(filter)) },
        trailingIcon = {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove filter",
                modifier = Modifier.size(InputChipDefaults.IconSize)
            )
        },
        colors = InputChipDefaults.inputChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.primary,
            trailingIconColor = MaterialTheme.colorScheme.primary
        ),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortChip(
    sort: Sort,
    onRemove: () -> Unit
) {
    val attributeDisplayName = getAttributeDisplayName(sort.sortBy)
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text("${stringResource(MR.strings.sort)}: $attributeDisplayName") },
        leadingIcon = {
            Icon(
                if (sort.ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                contentDescription = "Sort direction"
            )
        },
        colors = InputChipDefaults.inputChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.primary,
            leadingIconColor = MaterialTheme.colorScheme.primary
        ),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    )
}

@Composable
fun FilterAndSortChips(
    filters: List<FilterCondition>,
    sort: Sort,
    onRemoveFilter: (FilterCondition) -> Unit,
    onResetSort: () -> Unit
){
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 0.dp)) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (sort.sortBy != "nameLocal") { // Don't show default sort
                item {
                    SortChip(sort = sort, onRemove = onResetSort)
                }
            }
            items(filters) { filter ->
                FilterChip(filter = filter, onRemove = { onRemoveFilter(filter) })
            }
        }
    }
}

@Composable
fun FilterAndSortControls(
    sort: Sort,
    isAddFilterEnabled: Boolean, // NEU: um den Button zu (de)aktivieren
    onAddFilter: () -> Unit,
    onUpdateSort: (Sort) -> Unit,
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Filter Button
        TextButton(
            onClick = onAddFilter,
            enabled = isAddFilterEnabled // Button wird bei >= 3 Filtern deaktiviert
        ) {
            Icon(Icons.Default.FilterList, contentDescription = stringResource(MR.strings.filter), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(MR.strings.filter))
        }

        // Sort Button
        Box {
            TextButton(onClick = { showSortMenu = true }) {
                Icon(Icons.Default.Sort, contentDescription = stringResource(MR.strings.sort), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(MR.strings.sort))
            }
            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false }
            ) {
                val sortOptions = listOf("nameLocal", "setName", "currentPrice", "ownedCopies", "language")
                sortOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(getAttributeDisplayName(option)) },
                        onClick = {
                            // Wenn auf das gleiche Attribut geklickt wird, Richtung umkehren
                            val newAscending = if (sort.sortBy == option) !sort.ascending else true
                            onUpdateSort(Sort(option, newAscending))
                            showSortMenu = false
                        },
                        trailingIcon = {
                            if (sort.sortBy == option) {
                                Icon(
                                    if (sort.ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                    contentDescription = "Sort Direction"
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}
