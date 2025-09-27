package de.pantastix.project.ui.components

import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import de.pantastix.project.ui.viewmodel.Filter

@Composable
fun WarningDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        modifier = Modifier.border(4.dp, MaterialTheme.colorScheme.tertiary, MaterialTheme.shapes.large),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.warning)) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onDismiss) { Text(stringResource(MR.strings.ok)) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFilterDialog(
    onDismiss: () -> Unit,
    onAddFilter: (Filter) -> Unit
) {
    val attributes = listOf("setName", "language")
    var expanded by remember { mutableStateOf(false) }
    var selectedAttribute by remember { mutableStateOf(attributes[0]) }
    var filterValue by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Filter") },
        text = {
            Column {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    TextField(
                        value = selectedAttribute,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        attributes.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item) },
                                onClick = {
                                    selectedAttribute = item
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = filterValue,
                    onValueChange = { filterValue = it },
                    label = { Text("Value") }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onAddFilter(Filter(selectedAttribute, filterValue)) }) {
                Text("Add")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
