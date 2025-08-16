package de.pantastix.project.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.pantastix.project.model.PokemonCard

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import coil3.compose.AsyncImage
import de.pantastix.project.model.Attack
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import de.pantastix.project.model.Ability
import de.pantastix.project.shared.resources.MR
import dev.icerock.moko.resources.compose.stringResource
import java.text.NumberFormat
import java.util.Locale

@Composable
fun CardDetailScreen(
    card: PokemonCard?,
    isLoading: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxSize()) {
        VerticalDivider(
            modifier = Modifier.fillMaxHeight(),
            color = MaterialTheme.colorScheme.primary,
            thickness = 4.dp
        )

        Box(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (card == null) {
                Text(stringResource(MR.strings.card_details_loading_error), modifier = Modifier.align(Alignment.Center))
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Top Action Bar
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(MR.strings.card_details_back_button_desc))
                        }
                        Row {
                            IconButton(onClick = onEdit) {
                                Icon(Icons.Default.Edit, contentDescription = stringResource(MR.strings.card_details_edit_button_desc))
                            }
                            IconButton(onClick = { showDeleteConfirmDialog = true }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(MR.strings.card_details_delete_button_desc),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(card.nameLocal, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)

                        AsyncImage(
                            model = card.imageUrl,
                            contentDescription = card.nameLocal,
                            modifier = Modifier
                                .fillMaxWidth(0.6f) // Macht das Bild schmaler
                                .aspectRatio(0.72f)
                        )

                        // Primary Info
                        Text("${card.setName} - ${card.localId}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        // Combat Info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            InfoChip(stringResource(MR.strings.card_details_rarity), card.rarity ?: "N/A")
                            InfoChip(stringResource(MR.strings.card_details_hp), card.hp?.toString() ?: "N/A")
                            InfoChip(stringResource(MR.strings.card_details_types), card.types.joinToString(", "))
                        }

                        HorizontalDivider()

                        // Collection Info Section
                        CollectionInfoSection(card)

                        // Abilities Section
                        if (card.abilities.isNotEmpty()) {
                            Section(title = stringResource(MR.strings.card_details_abilities)) {
                                card.abilities.forEach { ability -> AbilityView(ability) }
                            }
                        }

                        // Attacks Section
                        if (card.attacks.isNotEmpty()) {
                            Section(title = stringResource(MR.strings.card_details_attacks)) {
                                card.attacks.forEach { attack -> AttackView(attack) }
                            }
                        }

                        // Further Details Section
                        Section(title = stringResource(MR.strings.card_details_other_details)) {
                            DetailRow(label = stringResource(MR.strings.card_details_retreat_cost), value = card.retreatCost?.toString() ?: "N/A")
                            DetailRow(label = stringResource(MR.strings.card_details_illustrator), value = card.illustrator ?: "N/A")
                            DetailRow(label = stringResource(MR.strings.card_details_regulation_mark), value = card.regulationMark ?: "N/A")
                        }

                        // KORRIGIERT: Padding am Ende der Seite
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }

        if (showDeleteConfirmDialog) {
            AlertDialog(
                modifier = Modifier.border(4.dp, MaterialTheme.colorScheme.error, MaterialTheme.shapes.large),
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = { Text(stringResource(MR.strings.card_details_delete_dialog_title)) },
                text = { Text(stringResource(MR.strings.card_details_delete_dialog_text)) },
                confirmButton = {
                    Button(
                        onClick = {
                            onDelete()
                            showDeleteConfirmDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(MR.strings.card_details_delete_button))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text(stringResource(MR.strings.card_details_cancel_button))
                    }
                }
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        HorizontalDivider()
        content()
    }
}

@Composable
private fun CollectionInfoSection(card: PokemonCard) {
    val uriHandler = LocalUriHandler.current
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale.GERMANY)

    Section(title = stringResource(MR.strings.card_details_collection_info)) {
        DetailRow(label = stringResource(MR.strings.card_details_owned_copies), value = card.ownedCopies.toString())
        DetailRow(label = stringResource(MR.strings.card_details_current_price), value = card.currentPrice?.let { currencyFormat.format(it) } ?: "-")
        Text(stringResource(MR.strings.card_details_notes), style = MaterialTheme.typography.titleSmall)
        Text(
            text = if (card.notes.isNullOrBlank()) stringResource(MR.strings.card_details_no_notes) else card.notes!!,
            style = MaterialTheme.typography.bodyMedium,
            color = if (card.notes.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant else LocalContentColor.current
        )
        card.cardMarketLink?.let { link ->
            if (link.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { uriHandler.openUri(link) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(MR.strings.card_details_cardmarket_link))
                }
            }
        }
    }
}


@Composable
private fun AbilityView(ability: Ability) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text("${ability.type}: ${ability.name}", style = MaterialTheme.typography.titleMedium)
        Text(ability.effect, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AttackView(attack: Attack) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(attack.name, style = MaterialTheme.typography.titleMedium)
            Text(attack.damage ?: "", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        // TODO: Hier könnten die Energiekosten (`attack.cost`) als Symbole dargestellt werden.
        attack.effect?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}