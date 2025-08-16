package de.pantastix.project.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.pantastix.project.model.PokemonCard
import de.pantastix.project.shared.resources.MR
import dev.icerock.moko.resources.compose.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCardScreen(
    card: PokemonCard,
    onSave: (id: Long, ownedCopies: Int, notes: String?, currentPrice: Double?) -> Unit,
    onCancel: () -> Unit
) {
    var ownedCopiesInput by remember { mutableStateOf(card.ownedCopies.toString()) }
    var notesInput by remember { mutableStateOf(card.notes ?: "") }
    var priceInput by remember { mutableStateOf(card.currentPrice?.toString() ?: "") }

    Row(modifier = Modifier.fillMaxSize()) {
        VerticalDivider(
            modifier = Modifier.fillMaxHeight(),
            color = MaterialTheme.colorScheme.primary,
            thickness = 4.dp
        )

        Box(
            modifier = Modifier
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(stringResource(MR.strings.edit_card_title), style = MaterialTheme.typography.headlineSmall)

                OutlinedTextField(
                    value = ownedCopiesInput,
                    onValueChange = { ownedCopiesInput = it.filter { char -> char.isDigit() } },
                    label = { Text(stringResource(MR.strings.edit_card_quantity_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notesInput,
                    onValueChange = { notesInput = it },
                    label = { Text(stringResource(MR.strings.edit_card_notes_label)) },
                    modifier = Modifier.fillMaxWidth().height(100.dp)
                )

                OutlinedTextField(
                    value = priceInput,
                    onValueChange = { priceInput = it },
                    label = { Text(stringResource(MR.strings.edit_card_price_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                        Text(stringResource(MR.strings.edit_card_cancel_button))
                    }
                    Button(
                        onClick = {
                            card.id?.let { nonNullId ->
                                onSave(
                                    nonNullId,
                                    ownedCopiesInput.toIntOrNull() ?: 1,
                                    notesInput.ifBlank { null },
                                    priceInput.replace(",", ".").toDoubleOrNull()
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(MR.strings.edit_card_save_button))
                    }
                }
            }
        }
    }
}