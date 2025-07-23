package de.pantastix.project.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.pantastix.project.shared.resources.MR
import dev.icerock.moko.resources.compose.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCardScreen(
    onAddCard: (
        name: String, setName: String, cardNumber: String, language: String,
        cardMarketLink: String, currentPrice: Double?, lastPriceUpdate: String?,
        imagePath: String?, ownedCopies: Int
    ) -> Unit,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var setName by remember { mutableStateOf("") }
    var cardNumber by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("Deutsch") } // Default Wert
    var cardMarketLink by remember { mutableStateOf("") }
    var currentPriceStr by remember { mutableStateOf("") }
    var lastPriceUpdate by remember { mutableStateOf("") }
    var imagePath by remember { mutableStateOf("") }
    var ownedCopiesStr by remember { mutableStateOf("1") } // Default Wert

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()), // Damit die Spalte scrollbar ist
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(MR.strings.add_card_back_button_desc))
        }
        Text(stringResource(MR.strings.add_card_title), style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(MR.strings.add_card_name_label)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = setName, onValueChange = { setName = it }, label = { Text(stringResource(MR.strings.add_card_set_name_label)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = cardNumber, onValueChange = { cardNumber = it }, label = { Text(stringResource(MR.strings.add_card_number_label)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = language, onValueChange = { language = it }, label = { Text(stringResource(MR.strings.add_card_language_label)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = cardMarketLink, onValueChange = { cardMarketLink = it }, label = { Text(stringResource(MR.strings.add_card_cardmarket_link_label)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = currentPriceStr,
            onValueChange = { currentPriceStr = it },
            label = { Text(stringResource(MR.strings.add_card_price_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(value = lastPriceUpdate, onValueChange = { lastPriceUpdate = it }, label = { Text(stringResource(MR.strings.add_card_price_update_label)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = imagePath, onValueChange = { imagePath = it }, label = { Text(stringResource(MR.strings.add_card_image_label)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = ownedCopiesStr,
            onValueChange = { ownedCopiesStr = it },
            label = { Text(stringResource(MR.strings.add_card_owned_copies_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                val price = currentPriceStr.toDoubleOrNull()
                val copies = ownedCopiesStr.toIntOrNull() ?: 0
                if (name.isNotBlank() && setName.isNotBlank() && language.isNotBlank() && cardMarketLink.isNotBlank() && cardNumber.isNotBlank()) {
                    onAddCard(name, setName, cardNumber, language, cardMarketLink, price, lastPriceUpdate.ifBlank { null }, imagePath.ifBlank { null }, copies)
                } else {
                    // TODO: Bessere Fehlermeldung für den User anzeigen, z.B. über einen Snackbar oder Dialog
                    println(MR.strings.add_card_error_fill_fields.localized())
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(MR.strings.add_card_save_button))
        }
    }
}