package de.pantastix.project.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.pantastix.project.model.PokemonCard
import de.pantastix.project.model.api.TcgDexCardResponse
import de.pantastix.project.shared.resources.MR
import de.pantastix.project.ui.components.PriceSelectionPanel
import dev.icerock.moko.resources.compose.stringResource
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditCardScreen(
    card: PokemonCard,
    isLoading: Boolean,
    apiDetails: TcgDexCardResponse?,
    onSave: (id: Long, ownedCopies: Int, notes: String?, currentPrice: Double?, selectedPriceSource: String?) -> Unit,
    onCancel: () -> Unit,
    onLoadPrices: (PokemonCard) -> Unit
) {
    var ownedCopiesInput by remember { mutableStateOf(card.ownedCopies.toString()) }
    var notesInput by remember { mutableStateOf(card.notes ?: "") }
    var priceInput by remember { mutableStateOf(card.currentPrice?.toString() ?: "") }
    var selectedPriceSchema by remember { mutableStateOf<PriceSchema?>(null) }
    var isHolo by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onLoadPrices(card)
    }

    LaunchedEffect(apiDetails) {
        if (apiDetails != null && !card.selectedPriceSource.isNullOrBlank() && card.selectedPriceSource != "CUSTOM") {
            val source = card.selectedPriceSource
            val isHoloSource = source?.endsWith("-holo") ?: false
            val schemaName = source?.removeSuffix("-holo")?.uppercase()

            val schema = PriceSchema.entries.find { it.name == schemaName }
            if (schema != null) {
                isHolo = isHoloSource
                selectedPriceSchema = schema
                // updatePrice aus dem PriceSelectionPanel hier simulieren/aufrufen
                val pricing = apiDetails.pricing?.cardmarket
                if (pricing != null) {
                    val priceToSet = if (isHoloSource) {
                        when (schema) {
                            PriceSchema.TREND -> pricing.`trend-holo`
                            PriceSchema.AVG1 -> pricing.`avg1-holo`
                            PriceSchema.AVG7 -> pricing.`avg7-holo`
                            PriceSchema.AVG30 -> pricing.`avg30-holo`
                            PriceSchema.LOW -> pricing.`low-holo`
                        }
                    } else {
                        when (schema) {
                            PriceSchema.TREND -> pricing.trend
                            PriceSchema.AVG1 -> pricing.avg1
                            PriceSchema.AVG7 -> pricing.avg7
                            PriceSchema.AVG30 -> pricing.avg30
                            PriceSchema.LOW -> pricing.low
                        }
                    }
                    if (priceToSet != null) {
                        priceInput = String.format("%.2f", priceToSet)
                    }
                }
            }
        }
    }

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

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                OutlinedTextField(
                    value = priceInput,
                    onValueChange = {
                        priceInput = it
                        selectedPriceSchema = null
                    },
                    label = { Text(stringResource(MR.strings.edit_card_price_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        if (!card.selectedPriceSource.isNullOrBlank() && card.selectedPriceSource != "CUSTOM") {
                            val oldPrice = card.currentPrice?.let { "%.2f€".format(it) } ?: "N/A"
                            val updateDate = card.lastPriceUpdate?.let {
                                try {
                                    val odt = OffsetDateTime.parse(it)
                                    odt.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                                } catch (e: Exception) { "unbekanntes Datum" }
                            } ?: "unbekannt"
                            Text("Letzter bekannter Preis: $oldPrice (vom $updateDate)")
                        }
                    }
                )

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                        LoadingIndicator(modifier = Modifier.size(48.dp))
                    }
                } else if (apiDetails != null) {
                    PriceSelectionPanel(
                        cardDetails = apiDetails,
                        initialSelectedSchema = selectedPriceSchema,
                        initialIsHolo = isHolo,
                        onSelectionChange = { price, source, holo ->
                            priceInput = price
                            selectedPriceSchema = source
                            isHolo = holo
                        }
                    )
                }

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
//                                onSave(
//                                    nonNullId,
//                                    ownedCopiesInput.toIntOrNull() ?: 1,
//                                    notesInput.ifBlank { null },
//                                    priceInput.replace(",", ".").toDoubleOrNull()
//                                )
                                val priceSource = when {
                                    selectedPriceSchema != null -> {
                                        val baseName = selectedPriceSchema!!.name.lowercase()
                                        if (isHolo) "${baseName}-holo" else baseName
                                    }
                                    priceInput.isNotBlank() -> "CUSTOM"
                                    else -> null
                                }
                                onSave(
                                    nonNullId,
                                    ownedCopiesInput.toIntOrNull() ?: 1,
                                    notesInput.ifBlank { null },
                                    priceInput.replace(",", ".").toDoubleOrNull(),
                                    priceSource
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