package de.pantastix.project.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.pantastix.project.model.PortfolioSnapshot
import de.pantastix.project.shared.resources.MR
import dev.icerock.moko.resources.compose.stringResource
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

enum class TimeRange { WEEK, MONTH, YEAR }

data class ChartDataPoint(
    val date: LocalDate,
    val value: Double,
    val isInterpolated: Boolean,
    val originalSnapshot: PortfolioSnapshot? = null
)

@Composable
fun CustomValueLineChart(
    dataPoints: List<ChartDataPoint>,
    timeRange: TimeRange,
    onSnapshotSelected: (PortfolioSnapshot?) -> Unit
) {
    val textMeasurer = rememberTextMeasurer()
    val primaryColor = MaterialTheme.colorScheme.primary
    val labelStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    
    var selectedIndex by remember(dataPoints) { mutableStateOf<Int?>(null) }
    
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        
        val paddingLeft = 70f
        val paddingBottom = 60f
        val paddingTop = 20f
        val paddingRight = 20f
        
        val chartWidth = width - paddingLeft - paddingRight
        val chartHeight = height - paddingTop - paddingBottom
        
        if (dataPoints.isEmpty()) return@BoxWithConstraints

        val maxVal = (dataPoints.maxOfOrNull { it.value } ?: 1.0).toFloat() * 1.1f
        val minVal = 0f
        
        val points = remember(dataPoints, chartWidth, chartHeight, maxVal) {
            dataPoints.mapIndexed { index, dp ->
                val x = paddingLeft + (index.toFloat() / (dataPoints.size - 1).coerceAtLeast(1)) * chartWidth
                val y = paddingTop + chartHeight - ((dp.value.toFloat() - minVal) / (maxVal - minVal)) * chartHeight
                Offset(x, y)
            }
        }
        
        Canvas(modifier = Modifier.fillMaxSize()
            .pointerInput(dataPoints) {
                detectTapGestures { offset ->
                    val index = ((offset.x - paddingLeft) / chartWidth * (dataPoints.size - 1)).roundToInt()
                    if (index in dataPoints.indices) {
                        selectedIndex = index
                        onSnapshotSelected(dataPoints[index].originalSnapshot)
                    }
                }
            }
            .pointerInput(dataPoints) {
                detectDragGestures(
                    onDragEnd = { },
                    onDragCancel = { }
                ) { change, _ ->
                    val index = ((change.position.x - paddingLeft) / chartWidth * (dataPoints.size - 1)).roundToInt()
                    if (index in dataPoints.indices) {
                        selectedIndex = index
                        onSnapshotSelected(dataPoints[index].originalSnapshot)
                    }
                }
            }
        ) {
            // Draw Y-Axis Labels & Grid
            val gridCount = 5
            for (i in 0..gridCount) {
                val value = (maxVal / gridCount) * i
                val y = paddingTop + chartHeight - (i.toFloat() / gridCount) * chartHeight
                drawLine(color = Color.LightGray.copy(alpha = 0.3f), start = Offset(paddingLeft, y), end = Offset(width - paddingRight, y), strokeWidth = 1f)
                drawText(textMeasurer = textMeasurer, text = "${value.toInt()} €", topLeft = Offset(5f, y - 15f), style = labelStyle)
            }
            
            // Draw X-Axis Labels
            val labels = when(timeRange) {
                TimeRange.WEEK -> dataPoints.indices.toList()
                TimeRange.MONTH -> {
                   val indices = mutableListOf<Int>()
                   dataPoints.forEachIndexed { index, dp ->
                       if (index == 0 || index == dataPoints.size - 1 || dp.date.dayOfWeek == DayOfWeek.MONDAY) {
                           indices.add(index)
                       }
                   }
                   indices
                }
                TimeRange.YEAR -> {
                    val indices = mutableListOf<Int>()
                    dataPoints.forEachIndexed { index, dp ->
                        if (dp.date.dayOfMonth == 1 || (dp.date.monthValue == 12 && dp.date.dayOfMonth == 31)) {
                            indices.add(index)
                        }
                    }
                    indices
                }
            }

            labels.forEach { i ->
                if (i in points.indices) {
                    val p = points[i]
                    val date = dataPoints[i].date
                    val label = when(timeRange) {
                        TimeRange.WEEK -> "${date.dayOfMonth}.${date.monthValue}."
                        TimeRange.MONTH -> if (i == 0 || i == dataPoints.size - 1) "${date.dayOfMonth}.${date.monthValue}." else "${date.dayOfMonth}."
                        TimeRange.YEAR -> if (date.dayOfMonth == 1) date.month.name.take(3) else "31.12."
                    }
                    drawText(textMeasurer = textMeasurer, text = label, topLeft = Offset(p.x - 15f, paddingTop + chartHeight + 10f), style = labelStyle)
                }
            }
            
            // Draw Path
            if (points.size >= 2) {
                val path = Path().apply {
                    moveTo(points[0].x, points[0].y)
                    for (i in 1 until points.size) {
                        lineTo(points[i].x, points[i].y)
                    }
                }
                val fillPath = Path().apply {
                    addPath(path)
                    lineTo(points.last().x, paddingTop + chartHeight)
                    lineTo(points.first().x, paddingTop + chartHeight)
                    close()
                }
                drawPath(path = fillPath, brush = Brush.verticalGradient(colors = listOf(primaryColor.copy(alpha = 0.3f), Color.Transparent), startY = paddingTop, endY = paddingTop + chartHeight))
                drawPath(path = path, color = primaryColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            
            // Draw Selected Indicator
            selectedIndex?.let { index ->
                if (index in points.indices) {
                    val p = points[index]
                    drawLine(color = primaryColor.copy(alpha = 0.5f), start = Offset(p.x, paddingTop), end = Offset(p.x, paddingTop + chartHeight), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                    drawCircle(color = Color.White, radius = 6.dp.toPx(), center = p)
                    drawCircle(color = primaryColor, radius = 4.dp.toPx(), center = p, style = Stroke(width = 2.dp.toPx()))
                }
            }
        }
        
        // Tooltip Overlay
        selectedIndex?.let { index ->
            val dp = dataPoints[index]
            val p = points[index]
            
            Surface(
                modifier = Modifier
                    .offset(
                        x = (p.x / (width / constraints.maxWidth)).dp - 40.dp, 
                        y = (p.y / (height / constraints.maxHeight)).dp - 65.dp
                    )
                    .widthIn(min = 100.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${dp.date.dayOfMonth.toString().padStart(2, '0')}.${dp.date.monthValue.toString().padStart(2, '0')}.${dp.date.year.toString().takeLast(2)}", style = MaterialTheme.typography.labelSmall)
                    Text("${dp.value} €", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    if (dp.isInterpolated) {
                        Text(stringResource(MR.strings.value_monitor_interpolated_hint), style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
