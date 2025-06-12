package de.pantastix.project.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.pantastix.project.model.SetInfo
import de.pantastix.project.ui.viewmodel.CardListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetSelectionScreen(
    viewModel: CardListViewModel
) {
    val sets by viewModel.sets.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var selectedCardNumber by remember { mutableStateOf("") }
    var selectedSet by remember { mutableStateOf<SetInfo?>(null) }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    // Bei einem Fehler einen Dialog anzeigen
    if (error != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Fehler") },
            text = { Text(error!!) },
            confirmButton = { Button(onClick = { viewModel.clearError() }) { Text("OK") } }
        )
    }

    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Karte via API hinzufügen", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        // Dropdown-Menü für die Set-Auswahl
        ExposedDropdownMenuBox(
            expanded = isDropdownExpanded,
            onExpandedChange = { isDropdownExpanded = !isDropdownExpanded }
        ) {
            OutlinedTextField(
                value = selectedSet?.nameDe ?: "Set auswählen",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = isDropdownExpanded,
                onDismissRequest = { isDropdownExpanded = false }
            ) {
                sets.forEach { set ->
                    DropdownMenuItem(
                        text = { Text(set.nameDe) },
                        onClick = {
                            selectedSet = set
                            isDropdownExpanded = false
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = selectedCardNumber,
            onValueChange = { selectedCardNumber = it },
            label = { Text("Kartennummer (z.B. 051)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    selectedSet?.let {
                        viewModel.fetchCardDetailsFromApi(it.setId, selectedCardNumber)
                    }
                },
                enabled = selectedSet != null && selectedCardNumber.isNotBlank()
            ) {
                Text("Karte suchen")
            }
        }
    }
}