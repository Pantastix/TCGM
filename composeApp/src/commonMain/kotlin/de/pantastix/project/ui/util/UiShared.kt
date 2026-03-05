package de.pantastix.project.ui.util

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SummaryCard(
    title: String, 
    value: String, 
    subtitle: String, 
    modifier: Modifier, 
    border: BorderStroke? = null
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        border = border,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = LocalContentColor.current.copy(alpha = 0.7f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = LocalContentColor.current.copy(alpha = 0.6f)
            )
        }
    }
}

fun formatDate(isoDate: String): String {
    return try {
        val parts = isoDate.split("-")
        if (parts.size >= 3) {
            "${parts[2]}.${parts[1]}.${parts[0].substring(2)}"
        } else isoDate
    } catch (e: Exception) { isoDate }
}

fun Double.format(digits: Int) = "%.${digits}f".format(this)
