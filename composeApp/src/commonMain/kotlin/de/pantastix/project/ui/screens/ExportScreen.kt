package de.pantastix.project.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.pantastix.project.ui.viewmodel.CardListViewModel
import de.pantastix.project.ui.viewmodel.ExportAttribute

@Composable
fun ExportScreen(
    viewModel: CardListViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "PDF Export Konfiguration",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Left: Attribute Configuration
            Column(modifier = Modifier.width(400.dp).fillMaxHeight()) {
                Text(
                    "Spalten & Reihenfolge",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(modifier = Modifier.weight(1f)) {
                    // Available List
                    AttributeListColumn(
                        title = "Verfügbar",
                        attributes = uiState.availableAttributesForExport,
                        modifier = Modifier.weight(1f),
                        onItemClick = { viewModel.moveAttributeToSelected(it) },
                        icon = Icons.AutoMirrored.Filled.ArrowForward
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Selected List
                    AttributeListColumn(
                        title = "Ausgewählt (Spaltenfolge)",
                        attributes = uiState.selectedAttributesForExport,
                        modifier = Modifier.weight(1.2f),
                        showSortButtons = true,
                        onItemClick = { viewModel.moveAttributeToAvailable(it) },
                        onMoveUp = { viewModel.moveAttributeUp(it) },
                        onMoveDown = { viewModel.moveAttributeDown(it) },
                        icon = Icons.AutoMirrored.Filled.ArrowBack
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.startExportToPdf() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.selectedCardsForExport.isNotEmpty() && uiState.selectedAttributesForExport.isNotEmpty(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("PDF Exportieren")
                }
            }

            Spacer(modifier = Modifier.width(32.dp))

            // Right: Card Selection
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text(
                        "Karten auswählen (${uiState.selectedCardsForExport.size})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )

                    TextButton(onClick = { viewModel.selectAllCardsForExport() }) {
                        Text("Alle", color = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(onClick = { viewModel.clearCardSelectionForExport() }) {
                        Text("Keine", color = MaterialTheme.colorScheme.outline)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.cardInfos) { cardInfo ->
                            val isSelected = uiState.selectedCardsForExport.contains(cardInfo.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleCardSelectionForExport(cardInfo.id) }
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { viewModel.toggleCardSelectionForExport(cardInfo.id) },
                                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(cardInfo.nameLocal, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${cardInfo.setName} • ${cardInfo.id}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AttributeListColumn(
    title: String,
    attributes: List<ExportAttribute>,
    modifier: Modifier = Modifier,
    showSortButtons: Boolean = false,
    onItemClick: (ExportAttribute) -> Unit,
    onMoveUp: (ExportAttribute) -> Unit = {},
    onMoveDown: (ExportAttribute) -> Unit = {},
    icon: ImageVector
) {
    Column(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(attributes) { attr ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showSortButtons) {
                            Column {
                                Icon(
                                    Icons.Default.ArrowDropUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp).clickable { onMoveUp(attr) },
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp).clickable { onMoveDown(attr) },
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        Text(
                            getAttributeLabel(attr),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(onClick = { onItemClick(attr) }, modifier = Modifier.size(24.dp)) {
                            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun getAttributeLabel(attribute: ExportAttribute): String {
    return when (attribute) {
        ExportAttribute.NAME -> "Name"
        ExportAttribute.TYPE -> "Typ"
        ExportAttribute.NUMBER -> "Nummer"
        ExportAttribute.SET -> "Set"
        ExportAttribute.PRICE -> "Preis"
        ExportAttribute.QUANTITY -> "Menge"
        ExportAttribute.IMAGE -> "Bild"
    }
}
