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