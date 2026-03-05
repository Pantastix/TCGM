package de.pantastix.project.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import de.pantastix.project.model.PokemonCardInfo
import de.pantastix.project.model.SetProgress
import de.pantastix.project.ui.util.*
import de.pantastix.project.ui.viewmodel.CardListViewModel
import org.koin.compose.koinInject
import dev.icerock.moko.resources.compose.stringResource
import de.pantastix.project.shared.resources.MR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetCollectionScreen(viewModel: CardListViewModel = koinInject(), onNavigateToCardDetail: (Long) -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedSetId by remember { mutableStateOf<String?>(null) }
    var sortOrder by remember { mutableStateOf("release_desc") }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadSetProgressList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (selectedSetId == null) stringResource(MR.strings.sets_title_overview) else stringResource(MR.strings.sets_title_content),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    if (selectedSetId != null) {
                        IconButton(onClick = { selectedSetId = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(MR.strings.card_details_back_button_desc))
                        }
                    }
                },
                actions = {
                    if (selectedSetId == null) {
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { expanded = true }) {
                                Icon(Icons.Default.Sort, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    when(sortOrder) {
                                        "release_desc" -> stringResource(MR.strings.sets_sort_newest)
                                        "release_asc" -> stringResource(MR.strings.sets_sort_oldest)
                                        "value_desc" -> stringResource(MR.strings.sets_sort_value)
                                        "name_asc" -> stringResource(MR.strings.sets_sort_name)
                                        else -> stringResource(MR.strings.sort)
                                    }
                                )
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(MR.strings.sets_sort_newest)) },
                                    onClick = { sortOrder = "release_desc"; expanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(MR.strings.sets_sort_oldest)) },
                                    onClick = { sortOrder = "release_asc"; expanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(MR.strings.sets_sort_value)) },
                                    onClick = { sortOrder = "value_desc"; expanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(MR.strings.sets_sort_name)) },
                                    onClick = { sortOrder = "name_asc"; expanded = false }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (selectedSetId == null) {
                val sortedList = remember(uiState.setProgressList, searchQuery, sortOrder) {
                    val filtered = uiState.setProgressList.filter { it.name.contains(searchQuery, ignoreCase = true) }
                    when (sortOrder) {
                        "release_desc" -> filtered.sortedByDescending { it.releaseDate?.takeIf { d -> d.isNotBlank() } ?: "0000-00-00" }
                        "release_asc" -> filtered.sortedWith(compareBy<SetProgress> { it.releaseDate?.takeIf { d -> d.isNotBlank() } ?: "9999-99-99" }.thenBy { it.name })
                        "value_desc" -> filtered.sortedByDescending { it.totalValue }
                        "name_asc" -> filtered.sortedBy { it.name }
                        else -> filtered
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header Summary
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val totalVal = uiState.setProgressList.sumOf { it.totalValue }
                            val totalCards = uiState.setProgressList.sumOf { it.totalPhysicalCount }
                            
                            SummaryCard(
                                title = stringResource(MR.strings.sets_summary_total_value),
                                value = "${totalVal.format(2)} €",
                                subtitle = stringResource(MR.strings.sets_summary_all_sets),
                                modifier = Modifier.weight(1.2f),
                                border = BorderStroke(4.dp, MaterialTheme.colorScheme.primary)
                            )
                            SummaryCard(
                                title = stringResource(MR.strings.sets_summary_total_cards),
                                value = totalCards.toString(),
                                subtitle = "${uiState.setProgressList.size} Sets",
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                            )
                        }
                    }

                    // Suche
                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(MR.strings.sets_search_placeholder)) },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    items(sortedList, key = { it.setId }) { progress ->
                        SetProgressCard(progress) {
                            selectedSetId = progress.setId
                            viewModel.loadCardsBySet(progress.setId)
                        }
                    }
                }
            } else {
                SetCardsListView(uiState.cardsBySet, onNavigateToCardDetail)
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
fun SetProgressCard(progress: SetProgress, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Set Logo
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                if (progress.logoUrl != null) {
                    AsyncImage(
                        model = progress.logoUrl + ".png",
                        contentDescription = progress.name,
                        modifier = Modifier.fillMaxSize().padding(6.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    progress.name, 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                
                Text(
                    progress.releaseDate?.let { formatDate(it) } ?: stringResource(MR.strings.sets_unknown_date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(Modifier.height(8.dp))
                
                val percentage = if (progress.cardCountOfficial > 0) {
                    (progress.ownedUniqueCount.toFloat() / progress.cardCountOfficial.toFloat()).coerceAtMost(1f)
                } else 0f
                
                LinearProgressIndicator(
                    progress = { percentage },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = if (percentage >= 1f) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${progress.ownedUniqueCount} / ${progress.cardCountOfficial}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                    
                    if (progress.artRarePlusCount > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Star, null, modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.tertiary)
                                Spacer(Modifier.width(2.dp))
                                Text(progress.artRarePlusCount.toString(), style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${progress.totalValue.format(2)} €",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "${progress.totalPhysicalCount}x",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SetCardsListView(cards: List<PokemonCardInfo>, onNavigateToCardDetail: (Long) -> Unit) {
    if (cards.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(MR.strings.sets_loading_cards), style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(cards, key = { it.id }) { card ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onNavigateToCardDetail(card.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = card.imageUrl,
                            contentDescription = card.nameLocal,
                            modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop
                        )
                        
                        Spacer(Modifier.width(12.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(card.nameLocal, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "x${card.ownedCopies}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${(card.currentPrice ?: 0.0).format(2)} €",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
