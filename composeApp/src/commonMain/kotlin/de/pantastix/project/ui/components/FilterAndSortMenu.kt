package de.pantastix.project.ui.components

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
import de.pantastix.project.ui.viewmodel.Filter
import de.pantastix.project.ui.viewmodel.Sort
import dev.icerock.moko.resources.compose.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterChip(
    filter: Filter,
    onRemove: () -> Unit
) {
    val attributeDisplayName = getAttributeDisplayName(filter.attribute)
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text("$attributeDisplayName: ${filter.value}") },
        trailingIcon = {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove filter",
                modifier = Modifier.size(18.dp)
            )
        }
    )
}

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
        }
    )
}

@Composable
fun FilterAndSortChips(
    filters: List<Filter>,
    sort: Sort,
    onRemoveFilter: (Filter) -> Unit,
    onResetSort: () -> Unit
){
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),) {
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
    onAddFilter: () -> Unit,
    onUpdateSort: (Sort) -> Unit,
) {
    var showSortMenu by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Chips for active filters and sort
//            LazyRow(
//                modifier = Modifier.weight(1f),
//                horizontalArrangement = Arrangement.spacedBy(8.dp)
//            ) {
//                if (sort.sortBy != "nameLocal") { // Don't show default sort
//                    item {
//                        SortChip(sort = sort, onRemove = onResetSort)
//                    }
//                }
//                items(filters) { filter ->
//                    FilterChip(filter = filter, onRemove = { onRemoveFilter(filter) })
//                }
//            }

//            Spacer(modifier = Modifier.width(16.dp))

            // Filter Button
            TextButton(onClick = onAddFilter) {
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
                                onUpdateSort(Sort(option, !sort.ascending))
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
