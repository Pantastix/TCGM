package de.pantastix.project.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.pantastix.project.model.api.TcgDexCardResponse
import de.pantastix.project.ui.screens.PriceSchema

@Composable
fun PriceSelectionPanel(
    cardDetails: TcgDexCardResponse,
    initialSelectedSchema: PriceSchema?,
    initialIsHolo: Boolean,
    onSelectionChange: (price: String, source: PriceSchema?, isHolo: Boolean) -> Unit
) {
    var selectedPriceSchema by remember(initialSelectedSchema) { mutableStateOf(initialSelectedSchema) }
    var isHolo by remember(initialIsHolo) { mutableStateOf(initialIsHolo) }

    fun updatePrice(priceSchema: PriceSchema? = selectedPriceSchema, isHoloCard: Boolean = isHolo) {
        val pricing = cardDetails.pricing?.cardmarket
        if (pricing != null) {
            val priceToSet = if (isHoloCard) {
                when (priceSchema) {
                    PriceSchema.TREND -> pricing.`trend-holo`
                    PriceSchema.AVG1 -> pricing.`avg1-holo`
                    PriceSchema.AVG7 -> pricing.`avg7-holo`
                    PriceSchema.AVG30 -> pricing.`avg30-holo`
                    PriceSchema.LOW -> pricing.`low-holo`
                    else -> null
                }
            } else {
                when (priceSchema) {
                    PriceSchema.TREND -> pricing.trend
                    PriceSchema.AVG1 -> pricing.avg1
                    PriceSchema.AVG7 -> pricing.avg7
                    PriceSchema.AVG30 -> pricing.avg30
                    PriceSchema.LOW -> pricing.low
                    else -> null
                }
            }
            if (priceToSet != null) {
                onSelectionChange(String.format("%.2f", priceToSet), priceSchema, isHoloCard)
            } else {
                onSelectionChange("", priceSchema, isHoloCard)
            }
        } else {
            onSelectionChange("", priceSchema, isHoloCard)
        }
    }

    val hasPricingData = cardDetails.pricing?.cardmarket != null
    if (hasPricingData) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        isHolo = false
                        updatePrice(selectedPriceSchema, false)
                    },
                    modifier = Modifier.weight(1f),
                    colors = if (!isHolo) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ButtonDefaults.outlinedButtonColors()
                ) {
                    Text("Normal")
                }
                OutlinedButton(
                    onClick = {
                        isHolo = true
                        updatePrice(selectedPriceSchema, true)
                    },
                    modifier = Modifier.weight(1f),
                    colors = if (isHolo) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ButtonDefaults.outlinedButtonColors()
                ) {
                    Text("Holo")
                }
            }

            val priceButtons = listOf(
                Pair(PriceSchema.TREND, "Trend"),
                Pair(PriceSchema.LOW, "Low"),
                Pair(PriceSchema.AVG1, "Avg 1"),
                Pair(PriceSchema.AVG7, "Avg 7"),
                Pair(PriceSchema.AVG30, "Avg 30")
            )
            val firstRowButtons = priceButtons.subList(0, 3)
            val secondRowButtons = priceButtons.subList(3, priceButtons.size)

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    firstRowButtons.forEach { (schema, label) ->
                        val price = if (isHolo) {
                            when (schema) {
                                PriceSchema.TREND -> cardDetails.pricing.cardmarket.`trend-holo`
                                PriceSchema.LOW -> cardDetails.pricing.cardmarket.`low-holo`
                                PriceSchema.AVG1 -> cardDetails.pricing.cardmarket.`avg1-holo`
                                else -> null
                            }
                        } else {
                            when (schema) {
                                PriceSchema.TREND -> cardDetails.pricing.cardmarket.trend
                                PriceSchema.LOW -> cardDetails.pricing.cardmarket.low
                                PriceSchema.AVG1 -> cardDetails.pricing.cardmarket.avg1
                                else -> null
                            }
                        }

                        if (price != null) {
                            OutlinedButton(
                                onClick = {
                                    selectedPriceSchema = schema
                                    updatePrice(schema, isHolo)
                                },
                                colors = if (selectedPriceSchema == schema) ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ) else ButtonDefaults.outlinedButtonColors()
                            ) {
                                Text("$label: ${String.format("%.2f", price)}€")
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    secondRowButtons.forEach { (schema, label) ->
                        val price = if (isHolo) {
                            when (schema) {
                                PriceSchema.AVG7 -> cardDetails.pricing.cardmarket.`avg7-holo`
                                PriceSchema.AVG30 -> cardDetails.pricing.cardmarket.`avg30-holo`
                                else -> null
                            }
                        } else {
                            when (schema) {
                                PriceSchema.AVG7 -> cardDetails.pricing.cardmarket.avg7
                                PriceSchema.AVG30 -> cardDetails.pricing.cardmarket.avg30
                                else -> null
                            }
                        }

                        if (price != null) {
                            OutlinedButton(
                                onClick = {
                                    selectedPriceSchema = schema
                                    updatePrice(schema, isHolo)
                                },
                                colors = if (selectedPriceSchema == schema) ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ) else ButtonDefaults.outlinedButtonColors()
                            ) {
                                Text("$label: ${String.format("%.2f", price)}€")
                            }
                        }
                    }
                }
            }
        }
    }
}
