package de.pantastix.project.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FilterNone
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.pantastix.project.model.api.TcgDexCardResponse
import de.pantastix.project.ui.screens.PriceSchema

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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

            val holoOptions = listOf("Normal", "Holo")
            val unCheckedIcons = listOf(Icons.Outlined.FilterNone, Icons.Outlined.AutoAwesome)
            val checkedIcons = listOf(Icons.Filled.FilterNone, Icons.Filled.AutoAwesome)
            val selectedIndex = if (isHolo) 1 else 0

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                verticalAlignment = Alignment.CenterVertically
            ) {
                holoOptions.forEachIndexed { index, label ->
                    ToggleButton(
                        checked = selectedIndex == index,
                        onCheckedChange = {
                            val newIsHolo = (index == 1)
                            isHolo = newIsHolo
                            updatePrice(isHoloCard = newIsHolo)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .semantics { role = Role.RadioButton },
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            holoOptions.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                    ) {
                        Icon(
                            if (selectedIndex == index) checkedIcons[index] else unCheckedIcons[index],
                            contentDescription = label,
                        )
                        Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                        Text(label)
                    }
                }
            }

//            val priceButtons = listOf(
//                Pair(PriceSchema.TREND, "Trend"),
//                Pair(PriceSchema.LOW, "Low"),
//                Pair(PriceSchema.AVG1, "Avg 1"),
//                Pair(PriceSchema.AVG7, "Avg 7"),
//                Pair(PriceSchema.AVG30, "Avg 30")
//            )
//            val firstRowButtons = priceButtons.subList(0, 3)
//            val secondRowButtons = priceButtons.subList(3, priceButtons.size)
//
//            Column(
//                modifier = Modifier.fillMaxWidth(),
//                verticalArrangement = Arrangement.spacedBy(8.dp)
//            ) {
//                Row(
//                    modifier = Modifier.fillMaxWidth(),
//                    horizontalArrangement = Arrangement.spacedBy(8.dp)
//                ) {
//                    firstRowButtons.forEach { (schema, label) ->
//                        val price = if (isHolo) {
//                            when (schema) {
//                                PriceSchema.TREND -> cardDetails.pricing.cardmarket.`trend-holo`
//                                PriceSchema.LOW -> cardDetails.pricing.cardmarket.`low-holo`
//                                PriceSchema.AVG1 -> cardDetails.pricing.cardmarket.`avg1-holo`
//                                else -> null
//                            }
//                        } else {
//                            when (schema) {
//                                PriceSchema.TREND -> cardDetails.pricing.cardmarket.trend
//                                PriceSchema.LOW -> cardDetails.pricing.cardmarket.low
//                                PriceSchema.AVG1 -> cardDetails.pricing.cardmarket.avg1
//                                else -> null
//                            }
//                        }
//
//                        if (price != null) {
//                            OutlinedButton(
//                                onClick = {
//                                    selectedPriceSchema = schema
//                                    updatePrice(schema, isHolo)
//                                },
//                                colors = if (selectedPriceSchema == schema) ButtonDefaults.outlinedButtonColors(
//                                    containerColor = MaterialTheme.colorScheme.primaryContainer
//                                ) else ButtonDefaults.outlinedButtonColors()
//                            ) {
//                                Text("$label: ${String.format("%.2f", price)}€")
//                            }
//                        }
//                    }
//                }
//                Row(
//                    modifier = Modifier.fillMaxWidth(),
//                    horizontalArrangement = Arrangement.spacedBy(8.dp)
//                ) {
//                    secondRowButtons.forEach { (schema, label) ->
//                        val price = if (isHolo) {
//                            when (schema) {
//                                PriceSchema.AVG7 -> cardDetails.pricing.cardmarket.`avg7-holo`
//                                PriceSchema.AVG30 -> cardDetails.pricing.cardmarket.`avg30-holo`
//                                else -> null
//                            }
//                        } else {
//                            when (schema) {
//                                PriceSchema.AVG7 -> cardDetails.pricing.cardmarket.avg7
//                                PriceSchema.AVG30 -> cardDetails.pricing.cardmarket.avg30
//                                else -> null
//                            }
//                        }
//
//                        if (price != null) {
//                            OutlinedButton(
//                                onClick = {
//                                    selectedPriceSchema = schema
//                                    updatePrice(schema, isHolo)
//                                },
//                                colors = if (selectedPriceSchema == schema) ButtonDefaults.outlinedButtonColors(
//                                    containerColor = MaterialTheme.colorScheme.primaryContainer
//                                ) else ButtonDefaults.outlinedButtonColors()
//                            ) {
//                                Text("$label: ${String.format("%.2f", price)}€")
//                            }
//                        }
//                    }
//                }
//            }
            val priceSchemaLabels = mapOf(
                PriceSchema.TREND to "Trend",
                PriceSchema.LOW to "Low",
                PriceSchema.AVG1 to "Avg 1",
                PriceSchema.AVG7 to "Avg 7",
                PriceSchema.AVG30 to "Avg 30"
            )

            val topRowSchemas = listOf(PriceSchema.TREND, PriceSchema.LOW)
            val bottomRowSchemas = listOf(PriceSchema.AVG1, PriceSchema.AVG7, PriceSchema.AVG30)

            // Hilfsfunktion, um den Preis für ein Schema zu ermitteln
            val getPriceForSchema = @Composable { schema: PriceSchema ->
                cardDetails.pricing.cardmarket.let { pricing ->
                    if (isHolo) {
                        when (schema) {
                            PriceSchema.TREND -> pricing.`trend-holo`
                            PriceSchema.LOW -> pricing.`low-holo`
                            PriceSchema.AVG1 -> pricing.`avg1-holo`
                            PriceSchema.AVG7 -> pricing.`avg7-holo`
                            PriceSchema.AVG30 -> pricing.`avg30-holo`
                        }
                    } else {
                        when (schema) {
                            PriceSchema.TREND -> pricing.trend
                            PriceSchema.LOW -> pricing.low
                            PriceSchema.AVG1 -> pricing.avg1
                            PriceSchema.AVG7 -> pricing.avg7
                            PriceSchema.AVG30 -> pricing.avg30
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                // Obere Button-Reihe: Trend, Low
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                ) {
                    topRowSchemas.forEachIndexed { index, schema ->
                        val price = getPriceForSchema(schema)
                        if (price != null) {
                            ToggleButton(
                                checked = selectedPriceSchema == schema,
                                onCheckedChange = {
                                    selectedPriceSchema = schema
                                    updatePrice(schema, isHolo)
                                },
                                modifier = Modifier.weight(1f),
                                shapes = when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                }
                            ) {
                                val label = priceSchemaLabels[schema] ?: ""
                                Text("$label: ${String.format("%.2f", price)}€")
                            }
                        }
                    }
                }

                // Untere Button-Reihe: Avg 1, Avg 7, Avg 30
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                ) {
                    bottomRowSchemas.forEachIndexed { index, schema ->
                        val price = getPriceForSchema(schema)
                        if (price != null) {
                            ToggleButton(
                                checked = selectedPriceSchema == schema,
                                onCheckedChange = {
                                    selectedPriceSchema = schema
                                    updatePrice(schema, isHolo)
                                },
                                modifier = Modifier.weight(1f),
                                shapes = when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    bottomRowSchemas.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                }
                            ) {
                                val label = priceSchemaLabels[schema] ?: ""
                                Text("$label: ${String.format("%.2f", price)}€")
                            }
                        }
                    }
                }
            }
        }
    }
}
