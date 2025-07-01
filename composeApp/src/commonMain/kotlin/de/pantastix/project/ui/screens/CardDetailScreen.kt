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

@Composable
fun CardDetailScreen(
    card: PokemonCard?,
    isLoading: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    Row() {
        VerticalDivider(
            modifier = Modifier.fillMaxHeight(),
            color = MaterialTheme.colorScheme.primary,
            thickness = 4.dp
        )
        Box(modifier = Modifier.fillMaxSize()) {


            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (card == null) {
                Text("Karte konnte nicht geladen werden.", modifier = Modifier.align(Alignment.Center))
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()) // Macht die Spalte scrollbar
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                        }
                        Row {
                            IconButton(onClick = onEdit) {
                                Icon(Icons.Default.Edit, contentDescription = "Karte bearbeiten")
                            }
                            IconButton(onClick = { showDeleteConfirmDialog = true }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Karte löschen",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    AsyncImage(
                        model = card.imageUrl,
                        contentDescription = card.nameLocal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp) // Feste Höhe oder anpassen
                            .align(Alignment.CenterHorizontally)
                    )
                    Spacer(Modifier.height(16.dp))

                    // Basis-Informationen
                    Text(card.nameLocal, style = MaterialTheme.typography.headlineMedium)
                    Text("${card.setName} - ${card.localId}", style = MaterialTheme.typography.titleMedium)
                    Text("Seltenheit: ${card.rarity ?: "N/A"}", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "HP: ${card.hp ?: "N/A"} - Typen: ${card.types.joinToString(", ")}",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    HorizontalDivider()

                    // Fähigkeiten
                    if (card.abilities.isNotEmpty()) {
                        Text("Fähigkeiten", style = MaterialTheme.typography.titleLarge)
                        card.abilities.forEach { ability ->
                            Text(ability.name, fontWeight = FontWeight.Bold)
                            Text(ability.effect, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                        }
                        Divider(modifier = Modifier.padding(vertical = 16.dp))
                    }

                    // Attacken
                    if (card.attacks.isNotEmpty()) {
                        Text("Attacken", style = MaterialTheme.typography.titleLarge)
                        card.attacks.forEach { attack ->
                            AttackView(attack)
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    // Weitere Details
                    Divider(modifier = Modifier.padding(vertical = 16.dp))
                    Text("Weitere Details", style = MaterialTheme.typography.titleLarge)
                    Text("Rückzugskosten: ${card.retreatCost ?: "N/A"}")
                    Text("Illustrator: ${card.illustrator ?: "N/A"}")
                    Text("Regulation Mark: ${card.regulationMark ?: "N/A"}")
                }
            }
        }


        if (showDeleteConfirmDialog) {
            AlertDialog(
                modifier = Modifier.border(4.dp, MaterialTheme.colorScheme.error, MaterialTheme.shapes.large),
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = { Text("Karte löschen?") },
                text = { Text("Möchtest du diese Karte wirklich aus deiner Sammlung entfernen?") },
                confirmButton = {
                    Button(
                        onClick = {
                            onDelete()
                            showDeleteConfirmDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Löschen")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text("Abbrechen")
                    }
                }
            )
        }
    }
}

@Composable
private fun AttackView(attack: Attack) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(attack.name, fontWeight = FontWeight.Bold)
            Text(attack.damage ?: "", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        attack.effect?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }
    }
}