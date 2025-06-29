package de.pantastix.project.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.pantastix.project.model.SetInfo
import de.pantastix.project.ui.viewmodel.CardLanguage
import de.pantastix.project.ui.viewmodel.CardListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetSelectionScreen(
    viewModel: CardListViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    var selectedCardNumber by remember { mutableStateOf("") }
    var setInputText by remember { mutableStateOf("") }
    var selectedSet by remember { mutableStateOf<SetInfo?>(null) }
    var isSetDropdownExpanded by remember { mutableStateOf(false) }

    val filteredSets = remember(setInputText, uiState.sets) {
        if (setInputText.isEmpty()) {
            uiState.sets
        } else {
            uiState.sets.filter {
                it.nameLocal.contains(setInputText, ignoreCase = true)
            }
        }
    }

    var selectedLanguage by remember(uiState.appLanguage) { mutableStateOf(CardLanguage.GERMAN) }
    var isLangDropdownExpanded by remember { mutableStateOf(false) }

    if (uiState.error != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Fehler") }, text = { Text(uiState.error!!) },
            confirmButton = { Button(onClick = { viewModel.clearError() }) { Text("OK") } }
        )
    }

    LaunchedEffect(uiState) {
        println("DEBUG: uiState hat sich geändert! isLoading: ${uiState.isLoading}, error: ${uiState.error}, anzahl sets: ${uiState.sets.size}")
    }

    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Karte via API hinzufügen", style = MaterialTheme.typography.headlineSmall)

        // Sprachauswahl
        ExposedDropdownMenuBox(expanded = isLangDropdownExpanded, onExpandedChange = { isLangDropdownExpanded = it }) {
            OutlinedTextField(
                value = selectedLanguage.displayName,
                onValueChange = {}, readOnly = true, label = { Text("Kartensprache") },
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

        ExposedDropdownMenuBox(
            expanded = isSetDropdownExpanded,
            onExpandedChange = { isSetDropdownExpanded = it }, // Öffnet/Schließt bei Klick auf den Pfeil
        ) {
            // 1. Das Textfeld, das anzeigt, was ausgewählt ist.
            OutlinedTextField(
                // Zeigt den Namen des Sets an oder einen Platzhalter
                value = selectedSet?.nameLocal ?: "Set auswählen...",

                // WICHTIG: onValueChange ist leer, da der Nutzer nicht tippen soll
                onValueChange = {},

                // WICHTIG: Das macht das Feld nicht editierbar
                readOnly = true,

                label = { Text("Set") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = isSetDropdownExpanded)
                },
                modifier = Modifier
                    .menuAnchor() // Verbindet das Feld mit dem Menü
                    .fillMaxWidth()
            )

            // 2. Das Menü, das aufklappt
            ExposedDropdownMenu(
                expanded = isSetDropdownExpanded,
                onDismissRequest = {
                    // Schließt das Menü, wenn daneben geklickt wird
                    isSetDropdownExpanded = false
                }
            ) {
                // Wir gehen die VOLLE Liste der Sets durch (nicht mehr filteredSets)
                uiState.sets.forEach { set ->
                    DropdownMenuItem(
                        text = { Text(set.nameLocal) },
                        onClick = {
                            // Die Logik beim Klick auf ein Item:
                            selectedSet = set         // 1. Auswahl speichern
                            isSetDropdownExpanded = false // 2. Menü schließen
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = selectedCardNumber,
            onValueChange = { selectedCardNumber = it },
            label = { Text("Kartennummer (z.B. 051)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

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
            ) { Text("Karte suchen") }
        }
    }
}