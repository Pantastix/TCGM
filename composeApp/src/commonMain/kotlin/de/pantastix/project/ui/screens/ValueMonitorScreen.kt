package de.pantastix.project.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import de.pantastix.project.model.GradedCopy
import de.pantastix.project.model.PortfolioSnapshot
import de.pantastix.project.model.PortfolioSnapshotItem
import de.pantastix.project.ui.components.ChartDataPoint
import de.pantastix.project.ui.components.CustomValueLineChart
import de.pantastix.project.ui.components.TimeRange
import de.pantastix.project.ui.util.*
import de.pantastix.project.ui.viewmodel.UiState
import dev.icerock.moko.resources.compose.stringResource
import de.pantastix.project.shared.resources.MR
import kotlinx.serialization.json.Json
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

sealed class SnapshotDisplayRow {
    abstract val id: String
    abstract val value: Double
    abstract val count: Int
    abstract val imageUrl: String?
    abstract val name: String
    abstract val setName: String

    data class Raw(val item: PortfolioSnapshotItem) : SnapshotDisplayRow() {
        override val id = "raw-${item.cardId}"
        override val value = (item.rawPrice ?: 0.0) * item.rowCount
        override val count = item.rowCount
        override val imageUrl = item.imageUrl
        override val name = item.nameLocal
        override val setName = item.setName
    }

    data class Graded(val item: PortfolioSnapshotItem, val graded: GradedCopy, val index: Int) : SnapshotDisplayRow() {
        override val id = "graded-${item.cardId}-$index"
        override val value = graded.value * graded.count
        override val count = graded.count
        override val imageUrl = item.imageUrl
        override val name = "${item.nameLocal} (${graded.vendor} ${graded.grade})"
        override val setName = item.setName
    }
}

fun formatLocalDate(date: LocalDate): String {
    return date.format(DateTimeFormatter.ofPattern("dd.MM.yy"))
}

@Composable
fun ValueMonitorScreen(
    uiState: UiState,
    onSnapshotSelected: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedRange by remember { mutableStateOf(TimeRange.WEEK) }
    var viewDate by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        CustomDatePickerDialog(
            initialDate = viewDate,
            onDismiss = { showDatePicker = false },
            onDateSelected = { 
                viewDate = it
                showDatePicker = false
            }
        )
    }

    val displayRows = remember(uiState.selectedSnapshotItems, searchQuery) {
        val rows = mutableListOf<SnapshotDisplayRow>()
        uiState.selectedSnapshotItems.forEach { item ->
            if (item.rowCount > 0) rows.add(SnapshotDisplayRow.Raw(item))
            item.gradedCopiesJson?.let { json ->
                try {
                    Json.decodeFromString<List<GradedCopy>>(json).forEachIndexed { idx, g ->
                        rows.add(SnapshotDisplayRow.Graded(item, g, idx))
                    }
                } catch (e: Exception) {}
            }
        }
        rows.filter { 
            it.name.contains(searchQuery, ignoreCase = true) || 
            it.setName.contains(searchQuery, ignoreCase = true)
        }.sortedByDescending { it.value }
    }

    val chartData = remember(uiState.portfolioSnapshots, selectedRange, viewDate) {
        prepareChartData(uiState.portfolioSnapshots, selectedRange, viewDate)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = stringResource(MR.strings.value_monitor_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (uiState.portfolioSnapshots.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(MR.strings.value_monitor_no_snapshots))
                }
            }
        } else {
            item {
                PortfolioSummary(uiState.portfolioSnapshots.lastOrNull())
            }

            // Time Range & Navigation
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TimeRangeChip(TimeRange.WEEK, selectedRange == TimeRange.WEEK) { selectedRange = it }
                        TimeRangeChip(TimeRange.MONTH, selectedRange == TimeRange.MONTH) { selectedRange = it }
                        TimeRangeChip(TimeRange.YEAR, selectedRange == TimeRange.YEAR) { selectedRange = it }
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            viewDate = when(selectedRange) {
                                TimeRange.WEEK -> viewDate.minusDays(7)
                                TimeRange.MONTH -> viewDate.minusMonths(1)
                                TimeRange.YEAR -> viewDate.minusYears(1)
                            }
                        }) {
                            Icon(Icons.Default.ChevronLeft, "Previous")
                        }

                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarMonth, "Select Date")
                        }
                        
                        val today = LocalDate.now()
                        IconButton(
                            enabled = viewDate.isBefore(today),
                            onClick = {
                                viewDate = when(selectedRange) {
                                    TimeRange.WEEK -> viewDate.plusDays(7).let { if (it.isAfter(today)) today else it }
                                    TimeRange.MONTH -> viewDate.plusMonths(1).let { if (it.isAfter(today)) today else it }
                                    TimeRange.YEAR -> viewDate.plusYears(1).let { if (it.isAfter(today)) today else it }
                                }
                            }
                        ) {
                            Icon(Icons.Default.ChevronRight, "Next")
                        }
                    }
                }
            }

            // Graph Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().height(360.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(MR.strings.value_monitor_chart_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            if (chartData.isNotEmpty()) {
                                Text(
                                    text = when(selectedRange) {
                                        TimeRange.WEEK -> "${formatLocalDate(chartData.first().date)} - ${formatLocalDate(chartData.last().date)}"
                                        TimeRange.MONTH -> "${chartData.last().date.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${chartData.last().date.year}"
                                        TimeRange.YEAR -> chartData.last().date.year.toString()
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            CustomValueLineChart(
                                dataPoints = chartData,
                                timeRange = selectedRange,
                                onSnapshotSelected = { it?.let { s -> onSnapshotSelected(s.date) } }
                            )
                        }
                    }
                }
            }

            // Filters
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(MR.strings.value_monitor_search_placeholder)) },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${stringResource(MR.strings.value_monitor_details_prefix)}: ${uiState.selectedSnapshotDate?.let { formatDate(it) } ?: ""}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (uiState.isPortfolioLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }

            items(displayRows, key = { it.id }) { row ->
                GranularSnapshotRow(row)
            }
        }
    }
}

@Composable
fun CustomDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit
) {
    var currentMonth by remember { mutableStateOf(initialDate.withDayOfMonth(1)) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.width(300.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ChevronLeft, null, modifier = Modifier.size(20.dp))
                    }
                    Text(
                        text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentMonth.year}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(20.dp))
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                
                // Days Header
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEach { day ->
                        Text(
                            text = day,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(Modifier.height(4.dp))
                
                // Days Grid
                val daysInMonth = currentMonth.lengthOfMonth()
                val firstDayOfWeek = currentMonth.dayOfWeek.value // 1 (Mon) to 7 (Sun)
                val totalSlots = 42
                
                // We use a fixed height grid but smaller
                Column {
                    for (row in 0 until 6) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            for (col in 0 until 7) {
                                val index = row * 7 + col
                                val dayNumber = index - (firstDayOfWeek - 2)
                                
                                Box(
                                    modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (dayNumber in 1..daysInMonth) {
                                        val date = currentMonth.withDayOfMonth(dayNumber)
                                        val isToday = date == LocalDate.now()
                                        val isSelected = date == initialDate
                                        
                                        Surface(
                                            modifier = Modifier.fillMaxSize(),
                                            shape = CircleShape,
                                            color = when {
                                                isSelected -> MaterialTheme.colorScheme.primary
                                                isToday -> MaterialTheme.colorScheme.primaryContainer
                                                else -> Color.Transparent
                                            },
                                            onClick = { onDateSelected(date) }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = dayNumber.toString(),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontSize = 11.sp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text(stringResource(MR.strings.cancel), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeRangeChip(range: TimeRange, selected: Boolean, onClick: (TimeRange) -> Unit) {
    val label = when(range) {
        TimeRange.WEEK -> stringResource(MR.strings.value_monitor_range_week)
        TimeRange.MONTH -> stringResource(MR.strings.value_monitor_range_month)
        TimeRange.YEAR -> stringResource(MR.strings.value_monitor_range_year)
    }
    FilterChip(
        selected = selected,
        onClick = { onClick(range) },
        label = { Text(text = label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
        shape = RoundedCornerShape(20.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.surface,
            selectedLabelColor = MaterialTheme.colorScheme.primary,
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = FilterChipDefaults.filterChipBorder(
            selectedBorderColor = MaterialTheme.colorScheme.primary,
            selectedBorderWidth = 4.dp,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            borderWidth = 1.dp,
            enabled = true,
            selected = selected
        )
    )
}

@Composable
fun PortfolioSummary(snapshot: PortfolioSnapshot?) {
    if (snapshot == null) return
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SummaryCard(
            title = stringResource(MR.strings.value_monitor_summary_total),
            value = "${snapshot.totalValue} €",
            subtitle = "${stringResource(MR.strings.value_monitor_summary_graded)}: ${snapshot.totalGradedValue} €",
            modifier = Modifier.weight(1.3f),
            border = BorderStroke(4.dp, MaterialTheme.colorScheme.primary)
        )
        SummaryCard(
            title = stringResource(MR.strings.value_monitor_summary_cards),
            value = "${snapshot.cardCount}",
            subtitle = "${stringResource(MR.strings.value_monitor_summary_positions)}: ${snapshot.cardCount}", 
            modifier = Modifier.weight(1f),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
        )
    }
}

fun prepareChartData(snapshots: List<PortfolioSnapshot>, range: TimeRange, endDate: LocalDate): List<ChartDataPoint> {
    if (snapshots.isEmpty()) return emptyList()
    val snapshotMap = snapshots.associateBy { LocalDate.parse(it.date) }
    val startDay = when(range) {
        TimeRange.WEEK -> endDate.minusDays(6)
        TimeRange.MONTH -> endDate.minusDays(29)
        TimeRange.YEAR -> endDate.minusDays(364)
    }
    val result = mutableListOf<ChartDataPoint>()
    var current = startDay
    while (!current.isAfter(endDate)) {
        val snapshot = snapshotMap[current]
        if (snapshot != null) {
            result.add(ChartDataPoint(current, snapshot.totalValue, false, snapshot))
        } else {
            val nearestBefore = snapshots.filter { LocalDate.parse(it.date).isBefore(current) }.lastOrNull()
            if (nearestBefore != null) {
                result.add(ChartDataPoint(current, nearestBefore.totalValue, true, nearestBefore))
            } else {
                val first = snapshots.first()
                result.add(ChartDataPoint(current, first.totalValue, true, first))
            }
        }
        current = current.plusDays(1)
    }
    return result
}

@Composable
fun GranularSnapshotRow(row: SnapshotDisplayRow) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = row.imageUrl,
                contentDescription = row.name,
                modifier = Modifier.size(50.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(row.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(row.setName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                val typeTag = when(row) {
                    is SnapshotDisplayRow.Raw -> "Raw"
                    is SnapshotDisplayRow.Graded -> "Graded"
                }
                Surface(
                    color = if (row is SnapshotDisplayRow.Raw) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(typeTag, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${row.value} €", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text("${row.count}x", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
