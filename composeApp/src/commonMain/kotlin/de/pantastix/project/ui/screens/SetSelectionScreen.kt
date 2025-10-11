package de.pantastix.project.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.PopupProperties
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchBySet(viewModel: CardListViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedCardNumber by remember { mutableStateOf("") }
    var selectedSet by remember { mutableStateOf<SetInfo?>(null) }
    var isSetDropdownExpanded by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf(CardLanguage.GERMAN) }
    var isLangDropdownExpanded by remember { mutableStateOf(false) }

//    var setQuery by remember { mutableStateOf("") }
    var setText by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    var textFieldSize by remember { mutableStateOf(Size.Zero) }

    val filteredSets = remember(setText, uiState.sets) {
        if (setText.isBlank()) {
            uiState.sets // Zeige alle Sets an, wenn das Suchfeld leer ist
        } else {
            uiState.sets.filter {
                it.nameLocal.contains(setText, ignoreCase = true)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // Sprachauswahl
        ExposedDropdownMenuBox(expanded = isLangDropdownExpanded, onExpandedChange = { isLangDropdownExpanded = it }) {
            OutlinedTextField(
                value = selectedLanguage.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(MR.strings.set_selection_language_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isLangDropdownExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = isLangDropdownExpanded,
                onDismissRequest = { isLangDropdownExpanded = false }) {
                CardLanguage.entries.forEach { lang ->
                    DropdownMenuItem(text = { Text(lang.displayName) }, onClick = {
                        selectedLanguage = lang
                        isLangDropdownExpanded = false
                    })
                }
            }
        }
        // Set-Auswahl
//        ExposedDropdownMenuBox(expanded = isSetDropdownExpanded, onExpandedChange = { isSetDropdownExpanded = it }) {
//            OutlinedTextField(
//                value = selectedSet?.nameLocal ?: stringResource(MR.strings.set_selection_set_placeholder),
//                onValueChange = {}, readOnly = true, label = { Text(stringResource(MR.strings.set_selection_set_label)) },
//                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isSetDropdownExpanded) },
//                modifier = Modifier.menuAnchor().fillMaxWidth()
//            )
//            ExposedDropdownMenu(expanded = isSetDropdownExpanded, onDismissRequest = { isSetDropdownExpanded = false }) {
//                uiState.sets.forEach { set ->
//                    DropdownMenuItem(text = { Text(set.nameLocal) }, onClick = {
//                        selectedSet = set
//                        isSetDropdownExpanded = false
//                    })
//                }
//            }
//        }

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = setText,
                onValueChange = {
                    setText = it
                    selectedSet = null // Auswahl zurücksetzen, während der Nutzer tippt
                    isSetDropdownExpanded = true // Menü offen halten während des Tippens
                },
                label = { Text(stringResource(MR.strings.set_selection_set_label)) },
                placeholder = { Text(stringResource(MR.strings.set_selection_set_placeholder)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isSetDropdownExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        textFieldSize = coordinates.size.toSize()
                    }
            )

            DropdownMenu(
                expanded = isSetDropdownExpanded,
                onDismissRequest = { isSetDropdownExpanded = false },
                // Diese Zeile ist die Lösung für das Fokus-Problem in einem Dialog
                properties = PopupProperties(focusable = false),
                modifier = Modifier
                    .width(with(LocalDensity.current) { textFieldSize.width.toDp() })
            ) {
                filteredSets.forEach { set ->
                    DropdownMenuItem(
                        text = { Text(set.nameLocal) },
                        onClick = {
                            selectedSet = set // Das ausgewählte Set speichern
                            setText = set.nameLocal // Textfeld mit dem vollen Namen aktualisieren
                            isSetDropdownExpanded = false // Menü schließen
                            focusManager.clearFocus() // Fokus vom Textfeld entfernen
                        }
                    )
                }
            }

//            if (filteredSets.isNotEmpty()) {
//                ExposedDropdownMenu(
//                    expanded = isSetDropdownExpanded,
//                    onDismissRequest = { isSetDropdownExpanded = false }
//                ) {
//                    filteredSets.forEach { set ->
//                        DropdownMenuItem(
//                            text = { Text(set.nameLocal) },
//                            onClick = {
//                                selectedSet = set // Das ausgewählte Set speichern
//                                setText = set.nameLocal // Textfeld mit dem vollen Namen aktualisieren
//                                isSetDropdownExpanded = false // Menü schließen
//                                focusManager.clearFocus() // Fokus vom Textfeld entfernen
//                            }
//                        )
//                    }
//                }
//            }
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
            LoadingIndicator()
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(MR.strings.set_selection_language_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isLangDropdownExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = isLangDropdownExpanded,
                onDismissRequest = { isLangDropdownExpanded = false }) {
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
            LoadingIndicator()
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
