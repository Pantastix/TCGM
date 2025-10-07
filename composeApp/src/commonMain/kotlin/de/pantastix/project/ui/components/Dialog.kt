package de.pantastix.project.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.pantastix.project.shared.resources.MR
import dev.icerock.moko.resources.compose.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.pantastix.project.ui.viewmodel.BulkUpdateProgress
import de.pantastix.project.ui.viewmodel.ComparisonType
import de.pantastix.project.ui.viewmodel.FilterCondition
import de.pantastix.project.ui.viewmodel.NumericAttribute
import java.util.Locale

@Composable
fun WarningDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        modifier = Modifier.border(4.dp, MaterialTheme.colorScheme.tertiary, MaterialTheme.shapes.large),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.warning)) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onDismiss ) { Text(stringResource(MR.strings.ok)) }
        }
    )
}

@Composable
fun ErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        modifier = Modifier.border(4.dp, MaterialTheme.colorScheme.error, MaterialTheme.shapes.large),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.settings_error_dialog_title)) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onDismiss) { Text(stringResource(MR.strings.settings_ok_button)) }
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BulkUpdateProgressDialog(
    progress: BulkUpdateProgress
) {
    Dialog(
        onDismissRequest = { /* Nicht schließbar durch den Nutzer */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .border(4.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Preise werden aktualisiert...",
                    style = MaterialTheme.typography.headlineSmall
                )

                if (progress.total > 0) {
                    // Bestimmter Fortschritt (wenn die Gesamtzahl bekannt ist)
                    val progressValue = progress.processed.toFloat() / progress.total.toFloat()

                    val animatedProgress by animateFloatAsState(
                        targetValue = progressValue,
                        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                        label = "progressAnimation"
                    )

                    LinearWavyProgressIndicator(
                        progress = { animatedProgress },
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // Unbestimmter Fortschritt (während die Daten vorbereitet werden)
                    LinearWavyProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Dieser Block zeigt die animierte Statusmeldung und den Zähler an
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.height(48.dp), // Feste Höhe, um Sprünge zu vermeiden
                    verticalArrangement = Arrangement.Center
                ) {
                    AnimatedContent(
                        targetState = progress.currentStepMessage,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                        },
                        label = "StatusMessageAnimation"
                    ) { message ->
                        Text(
                            text = message ?: "...",
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (progress.total > 0) {
                        Text(
                            text = "${progress.processed} / ${progress.total}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}


private data class FilterAttributeOption(
    val key: String,
    val displayName: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFilterDialog(
    availableLanguages: List<String>,
    onDismiss: () -> Unit,
    onAddFilter: (FilterCondition) -> Unit
) {
    // Liste der verfügbaren Filterattribute
    val filterAttributes = listOf(
        FilterAttributeOption("NAME", "Name"),
        FilterAttributeOption("LANGUAGE", "Sprache"),
        FilterAttributeOption("OWNED_COPIES", "Menge"),
        FilterAttributeOption("CURRENT_PRICE", "Preis")
    )

    // State-Management für den Dialog
    var selectedAttribute by remember { mutableStateOf(filterAttributes.first()) }
    var attributeDropdownExpanded by remember { mutableStateOf(false) }

    // States für die verschiedenen Filtertypen
    var nameQuery by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf(availableLanguages.firstOrNull() ?: "") }
    var languageDropdownExpanded by remember { mutableStateOf(false) }
    var numericValue by remember { mutableStateOf("") }
    var selectedComparison by remember { mutableStateOf(ComparisonType.EQUAL) }
    var comparisonDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter hinzufügen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Schritt 1: Attribut auswählen
                ExposedDropdownMenuBox(
                    expanded = attributeDropdownExpanded,
                    onExpandedChange = { attributeDropdownExpanded = !attributeDropdownExpanded }
                ) {
                    TextField(
                        value = selectedAttribute.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Filtern nach") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = attributeDropdownExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = attributeDropdownExpanded,
                        onDismissRequest = { attributeDropdownExpanded = false }
                    ) {
                        filterAttributes.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.displayName) },
                                onClick = {
                                    selectedAttribute = item
                                    attributeDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Schritt 2: Spezifische Eingabefelder anzeigen
                when (selectedAttribute.key) {
                    "NAME" -> {
                        OutlinedTextField(
                            value = nameQuery,
                            onValueChange = { nameQuery = it },
                            label = { Text("Kartenname enthält...") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    "LANGUAGE" -> {
                        ExposedDropdownMenuBox(
                            expanded = languageDropdownExpanded,
                            onExpandedChange = { languageDropdownExpanded = !languageDropdownExpanded }
                        ) {
                            TextField(
                                value = getLanguageDisplayName(selectedLanguage),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Sprache auswählen") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageDropdownExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = languageDropdownExpanded,
                                onDismissRequest = { languageDropdownExpanded = false }
                            ) {
                                availableLanguages.forEach { langCode ->
                                    DropdownMenuItem(
                                        text = { Text(getLanguageDisplayName(langCode)) },
                                        onClick = {
                                            selectedLanguage = langCode
                                            languageDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    "OWNED_COPIES", "CURRENT_PRICE" -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Vergleichsoperator ( =, >, < )
                            ExposedDropdownMenuBox(
                                expanded = comparisonDropdownExpanded,
                                onExpandedChange = { comparisonDropdownExpanded = !comparisonDropdownExpanded },
                                modifier = Modifier.weight(1f)
                            ) {
                                TextField(
                                    value = getComparisonSymbol(selectedComparison),
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = comparisonDropdownExpanded) },
                                    modifier = Modifier.menuAnchor()
                                )
                                ExposedDropdownMenu(
                                    expanded = comparisonDropdownExpanded,
                                    onDismissRequest = { comparisonDropdownExpanded = false }
                                ) {
                                    ComparisonType.entries.forEach { comparison ->
                                        DropdownMenuItem(
                                            text = { Text(getComparisonSymbol(comparison)) },
                                            onClick = {
                                                selectedComparison = comparison
                                                comparisonDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            // Zahlenwert
                            OutlinedTextField(
                                value = numericValue,
                                onValueChange = { numericValue = it.filter { char -> char.isDigit() || char == '.' || char == ',' } },
                                label = { Text("Wert") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(2f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val filter = when (selectedAttribute.key) {
                    "NAME" -> FilterCondition.ByName(nameQuery)
                    "LANGUAGE" -> FilterCondition.ByLanguage(selectedLanguage)
                    "OWNED_COPIES" -> FilterCondition.ByNumericValue(
                        attribute = NumericAttribute.OWNED_COPIES,
                        comparison = selectedComparison,
                        value = numericValue.replace(',', '.').toDoubleOrNull() ?: 0.0
                    )
                    "CURRENT_PRICE" -> FilterCondition.ByNumericValue(
                        attribute = NumericAttribute.CURRENT_PRICE,
                        comparison = selectedComparison,
                        value = numericValue.replace(',', '.').toDoubleOrNull() ?: 0.0
                    )
                    else -> null
                }
                filter?.let(onAddFilter)
                onDismiss()
            }) {
                Text("Anwenden")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

// Hilfsfunktionen für die Anzeige im Dialog
private fun getComparisonSymbol(comparison: ComparisonType): String {
    return when (comparison) {
        ComparisonType.EQUAL -> "="
        ComparisonType.GREATER_THAN -> ">"
        ComparisonType.LESS_THAN -> "<"
    }
}

fun getLanguageDisplayName(code: String): String {
    return when (code.lowercase(Locale.ROOT)) {
        "de" -> "Deutsch"
        "en" -> "English"
        "fr" -> "Français"
        "es" -> "Español"
        "it" -> "Italiano"
        "pt" -> "Português"
        "jp" -> "Japanisch"
        else -> code
    }
}
