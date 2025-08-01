package de.pantastix.project.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.pantastix.project.model.api.TcgDexCardResponse
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import coil3.compose.AsyncImage
import de.pantastix.project.model.SetInfo
import de.pantastix.project.shared.resources.MR
import dev.icerock.moko.resources.compose.stringResource
import jdk.internal.org.jline.utils.AttributedStringBuilder.append
import org.jetbrains.compose.ui.tooling.preview.Preview


@Composable
fun FinalAddCardScreen(
    cardDetails: TcgDexCardResponse,
    setInfo: SetInfo?,
    isLoading: Boolean,
    onConfirm: (
        cardDetails: TcgDexCardResponse,
        editedName: String,
        abbreviation: String?,
        price: Double?,
        marketLink: String,
        quantity: Int,
        notes: String?
    ) -> Unit,
    onCancel: () -> Unit
) {
    var priceInput by remember { mutableStateOf("") }
    var abbreviationInput by remember(setInfo?.abbreviation) { mutableStateOf(setInfo?.abbreviation ?: "") }
    var quantityInput by remember { mutableStateOf("1") }
    var notesInput by remember { mutableStateOf("") }
    var linkInput by remember { mutableStateOf("") }
    var userHasEditedLink by remember { mutableStateOf(false) }

    LaunchedEffect(abbreviationInput, cardDetails, setInfo) {
        if (!userHasEditedLink) {
            fun slugify(input: String) = input.replace("'", "").replace(" ", "-").replace(":", "")
            val finalAbbreviation = if (abbreviationInput.isNotBlank()) abbreviationInput.uppercase() else cardDetails.set.id.uppercase()
            val autoGeneratedLink = "https://www.cardmarket.com/de/Pokemon/Products/Singles/" +
                    "${slugify(setInfo?.nameEn ?: cardDetails.set.name)}/" +
                    "${slugify(cardDetails.name)}-${finalAbbreviation}${cardDetails.localId}"
            linkInput = autoGeneratedLink
        }
    }

    var nameInput by remember(cardDetails.name) { mutableStateOf(cardDetails.name) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(
            text = cardDetails.name,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )
        Text(
            "${cardDetails.set.name} (${cardDetails.localId})",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )

        AsyncImage(
            model = cardDetails.image?.let { "$it/high.jpg" },
            contentDescription = cardDetails.name,
            modifier = Modifier.fillMaxWidth().height(250.dp).align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(16.dp))

        Text(
            stringResource(MR.strings.final_add_card_found_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = nameInput,
            onValueChange = { nameInput = it },
            readOnly = false,
            label = { Text(stringResource(MR.strings.final_add_card_name_label)) },
            modifier = Modifier.fillMaxWidth()
        )

        if (setInfo?.abbreviation == null) {
            OutlinedTextField(
                value = abbreviationInput,
                onValueChange = { abbreviationInput = it },
                label = { Text(stringResource(MR.strings.final_add_card_set_abbr_label)) },
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text(stringResource(MR.strings.final_add_card_set_abbr_support)) }
            )
        }

        OutlinedTextField(
            value = linkInput,
            onValueChange = {
                linkInput = it
                userHasEditedLink = true
            },
            readOnly = false,
            label = { Text(stringResource(MR.strings.final_add_card_cardmarket_link_label)) },
            modifier = Modifier.fillMaxWidth()
        )

        val uriHandler = LocalUriHandler.current
        TextButton(onClick = { uriHandler.openUri(linkInput) }, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(MR.strings.final_add_card_open_on_cardmarket))
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = priceInput,
                onValueChange = { priceInput = it },
                label = { Text(stringResource(MR.strings.final_add_card_purchase_price_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = quantityInput,
                onValueChange = { quantityInput = it.filter { char -> char.isDigit() } },
                label = { Text(stringResource(MR.strings.final_add_card_quantity_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(0.5f)
            )
        }

        OutlinedTextField(
            value = notesInput,
            onValueChange = { notesInput = it },
            label = { Text(stringResource(MR.strings.final_add_card_notes_label)) },
            modifier = Modifier.fillMaxWidth().height(100.dp)
        )

        Spacer(Modifier.weight(1f))

        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
            if (isLoading) {
                CircularProgressIndicator()
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(stringResource(MR.strings.final_add_card_cancel_button)) }
                    Button(
                        onClick = {
                            val quantity = quantityInput.toIntOrNull() ?: 1
                            onConfirm(
                                cardDetails,
                                nameInput,
                                abbreviationInput.ifBlank { null },
                                priceInput.replace(",", ".").toDoubleOrNull(),
                                linkInput,
                                quantity,
                                notesInput.ifBlank { null }
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(MR.strings.final_add_card_save_button))
                    }
                }
            }
        }
    }
}