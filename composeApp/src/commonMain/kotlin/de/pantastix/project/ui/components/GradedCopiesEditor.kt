package de.pantastix.project.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.pantastix.project.model.GradedCopy

@Composable
fun GradedCopiesEditor(
    initialCopies: List<GradedCopy>,
    onCopiesChanged: (List<GradedCopy>) -> Unit
) {
    var copies by remember { mutableStateOf(initialCopies) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Graded Cards", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = {
                copies = copies + GradedCopy("PSA", "10", 1, 0.0)
                onCopiesChanged(copies)
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Grading")
            }
        }

        if (copies.isEmpty()) {
            Text(
                "Keine Graded-Versionen hinterlegt.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            copies.forEachIndexed { index, copy ->
                GradedCopyRow(
                    copy = copy,
                    onChanged = { updated ->
                        copies = copies.toMutableList().apply { set(index, updated) }
                        onCopiesChanged(copies)
                    },
                    onDelete = {
                        copies = copies.toMutableList().apply { removeAt(index) }
                        onCopiesChanged(copies)
                    }
                )
                if (index < copies.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun GradedCopyRow(
    copy: GradedCopy,
    onChanged: (GradedCopy) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Vendor (PSA, CGC, etc)
        OutlinedTextField(
            value = copy.vendor,
            onValueChange = { onChanged(copy.copy(vendor = it)) },
            label = { Text("Firma") },
            modifier = Modifier.weight(1.5f),
            singleLine = true
        )

        // Grade
        OutlinedTextField(
            value = copy.grade,
            onValueChange = { onChanged(copy.copy(grade = it)) },
            label = { Text("Grade") },
            modifier = Modifier.weight(1f),
            singleLine = true
        )

        // Count
        OutlinedTextField(
            value = copy.count.toString(),
            onValueChange = { val c = it.filter { char -> char.isDigit() }.toIntOrNull() ?: 0
                onChanged(copy.copy(count = c)) },
            label = { Text("Anzahl") },
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )

        // Value
        OutlinedTextField(
            value = copy.value.toString(),
            onValueChange = { val v = it.replace(",", ".").toDoubleOrNull() ?: 0.0
                onChanged(copy.copy(value = v)) },
            label = { Text("Wert (€)") },
            modifier = Modifier.weight(1.5f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )

        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
        }
    }
}
