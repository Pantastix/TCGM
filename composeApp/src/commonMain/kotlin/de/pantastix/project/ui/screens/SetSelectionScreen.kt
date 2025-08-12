package de.pantastix.project.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.pantastix.project.model.SetInfo
import de.pantastix.project.shared.resources.MR
import de.pantastix.project.ui.viewmodel.CardLanguage
import de.pantastix.project.ui.viewmodel.CardListViewModel
import dev.icerock.moko.resources.compose.stringResource

// NEU: Enum zur Steuerung der Suchmethode
enum class SearchMode {
    BY_NAME_AND_NUMBER, BY_SET
}

@Composable
fun SetSelectionScreen(
    viewModel: CardListViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedSearchMode by remember { mutableStateOf(SearchMode.BY_NAME_AND_NUMBER) } // Bevorzugte Methode

    if (uiState.error != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text(stringResource(MR.strings.set_selection_error_dialog_title)) },
            text = { Text(uiState.error!!) },
            confirmButton = { Button(onClick = { viewModel.clearError() }) { Text(stringResource(MR.strings.set_selection_ok_button)) } },
            modifier = Modifier.border(4.dp, MaterialTheme.colorScheme.error, MaterialTheme.shapes.large)
        )
    }

    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(MR.strings.set_selection_add_card_title), style = MaterialTheme.typography.headlineSmall)

        // Tab-Leiste zum Umschalten der Suchmethode
        TabRow(selectedTabIndex = selectedSearchMode.ordinal) {
            Tab(
                selected = selectedSearchMode == SearchMode.BY_NAME_AND_NUMBER,
                onClick = { selectedSearchMode = SearchMode.BY_NAME_AND_NUMBER },
                text = { Text(stringResource(MR.strings.set_selection_tab_by_name)) }
            )
            Tab(
                selected = selectedSearchMode == SearchMode.BY_SET,
                onClick = { selectedSearchMode = SearchMode.BY_SET },
                text = { Text(stringResource(MR.strings.set_selection_tab_by_set)) }
            )
        }

        // Zeigt die passende Such-UI an
        when (selectedSearchMode) {
            SearchMode.BY_NAME_AND_NUMBER -> SearchByNameAndNumber(viewModel)
            SearchMode.BY_SET -> SearchBySet(viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBySet(viewModel: CardListViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedCardNumber by remember { mutableStateOf("") }
    var selectedSet by remember { mutableStateOf<SetInfo?>(null) }
    var isSetDropdownExpanded by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf(CardLanguage.GERMAN) }
    var isLangDropdownExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // Sprachauswahl
        ExposedDropdownMenuBox(expanded = isLangDropdownExpanded, onExpandedChange = { isLangDropdownExpanded = it }) {
            OutlinedTextField(
                value = selectedLanguage.displayName,
                onValueChange = {}, readOnly = true, label = { Text(stringResource(MR.strings.set_selection_language_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isLangDropdownExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = isLangDropdownExpanded, onDismissRequest = { isLangDropdownExpanded = false }) {
                CardLanguage.entries.forEach { lang ->
                    DropdownMenuItem(text = { Text(lang.displayName) }, onClick = {
                        selectedLanguage = lang
                        isLangDropdownExpanded = false
                    })
                }
            }
        }
        // Set-Auswahl
        ExposedDropdownMenuBox(expanded = isSetDropdownExpanded, onExpandedChange = { isSetDropdownExpanded = it }) {
            OutlinedTextField(
                value = selectedSet?.nameLocal ?: stringResource(MR.strings.set_selection_set_placeholder),
                onValueChange = {}, readOnly = true, label = { Text(stringResource(MR.strings.set_selection_set_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isSetDropdownExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = isSetDropdownExpanded, onDismissRequest = { isSetDropdownExpanded = false }) {
                uiState.sets.forEach { set ->
                    DropdownMenuItem(text = { Text(set.nameLocal) }, onClick = {
                        selectedSet = set
                        isSetDropdownExpanded = false
                    })
                }
            }
        }
        // Kartennummer
        OutlinedTextField(
            value = selectedCardNumber,
            onValueChange = { selectedCardNumber = it },
            label = { Text(stringResource(MR.strings.set_selection_card_number_label)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        // Such-Button
        if (uiState.isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    selectedSet?.let {
                        viewModel.fetchCardDetailsFromApi(it.setId, selectedCardNumber, selectedLanguage)
                    }
                },
                enabled = selectedSet != null && selectedCardNumber.isNotBlank()
            ) { Text(stringResource(MR.strings.set_selection_search_button)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchByNameAndNumber(viewModel: CardListViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var cardName by remember { mutableStateOf("") }
    var cardNumber by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf(CardLanguage.GERMAN) }
    var isLangDropdownExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // Sprachauswahl
        ExposedDropdownMenuBox(expanded = isLangDropdownExpanded, onExpandedChange = { isLangDropdownExpanded = it }) {
            OutlinedTextField(
                value = selectedLanguage.displayName,
                onValueChange = {}, readOnly = true, label = { Text(stringResource(MR.strings.set_selection_language_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isLangDropdownExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = isLangDropdownExpanded, onDismissRequest = { isLangDropdownExpanded = false }) {
                CardLanguage.entries.forEach { lang ->
                    DropdownMenuItem(text = { Text(lang.displayName) }, onClick = {
                        selectedLanguage = lang
                        isLangDropdownExpanded = false
                    })
                }
            }
        }
        // Kartenname
        OutlinedTextField(
            value = cardName,
            onValueChange = { cardName = it },
            label = { Text(stringResource(MR.strings.set_selection_card_name_label)) },
            modifier = Modifier.fillMaxWidth()
        )
        // Kartennummer
        OutlinedTextField(
            value = cardNumber,
            onValueChange = { cardNumber = it },
            label = { Text(stringResource(MR.strings.set_selection_card_number_count_label)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        // Such-Button
        if (uiState.isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    viewModel.fetchCardDetailsByNameAndNumber(cardName, cardNumber, selectedLanguage)
                },
                enabled = cardName.isNotBlank() && cardNumber.contains("/")
            ) { Text(stringResource(MR.strings.set_selection_search_button)) }
        }
    }
}
