package de.pantastix.project.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.pantastix.project.shared.resources.MR
import de.pantastix.project.ui.viewmodel.AppLanguage
import de.pantastix.project.ui.viewmodel.CardListViewModel
import dev.icerock.moko.resources.compose.stringResource
import org.koin.compose.koinInject

@Composable
fun SettingsScreen(viewModel: CardListViewModel = koinInject(), onNavigateToGuide: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    // Lokale Zustände für die Eingabefelder, initialisiert aus dem ViewModel
    var supabaseUrl by remember(uiState.supabaseUrl) { mutableStateOf(uiState.supabaseUrl) }
    var supabaseKey by remember(uiState.supabaseKey) { mutableStateOf(uiState.supabaseKey) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(MR.strings.settings_title), style = MaterialTheme.typography.headlineMedium)

        // Sprachauswahl (Beispiel für eine Einstellung)
        Text("App-Sprache", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppLanguage.entries.forEach { lang ->
                Button(
                    onClick = { viewModel.setAppLanguage(lang) },
                    colors = if (uiState.appLanguage == lang) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()
                ) { Text(lang.displayName) }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // Supabase-Einstellungen
        Text("Cloud-Synchronisation (Supabase)", style = MaterialTheme.typography.titleLarge)
        Text(
            "Verbinde deine eigene, kostenlose Supabase-Datenbank, um deine Sammlung auf mehreren Geräten zu synchronisieren.",
            style = MaterialTheme.typography.bodySmall
        )

        OutlinedTextField(
            value = supabaseUrl,
            onValueChange = { supabaseUrl = it },
            label = { Text("Supabase URL") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = supabaseKey,
            onValueChange = { supabaseKey = it },
            label = { Text("Supabase Anon Key") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { viewModel.connectNewToSupabase(supabaseUrl, supabaseKey) },
                enabled = !uiState.isLoading
            ) {
                Text("Verbinden & Prüfen")
            }
            Button(
                onClick = {
                    supabaseUrl = ""
                    supabaseKey = ""
                    viewModel.disconnectFromSupabase()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Verbindung trennen")
            }
            TextButton(onClick = onNavigateToGuide) {
                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Hilfe", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Anleitung")
            }
        }

        if (uiState.isLoading) {
            CircularProgressIndicator()
        }

        // Status- und Synchronisationsanzeige
        if (uiState.isSupabaseConnected) {
            Text("Status: Verbunden", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        } else {
            Text("Status: Nicht verbunden (Daten werden lokal gespeichert)", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }

        uiState.syncPromptMessage?.let { message ->
            SyncPromptDialog(
                message = message,
                onConfirm = { viewModel.syncLocalToSupabase() },
                onDismiss = { viewModel.dismissSyncPrompt() }
            )
        }

        uiState.disconnectPromptMessage?.let { message ->
            DisconnectPromptDialog(
                message = message,
                onConfirmAndMigrate = { viewModel.confirmDisconnect(migrateData = true) },
                onConfirmWithoutMigrate = { viewModel.confirmDisconnect(migrateData = false) },
                onDismiss = { viewModel.dismissDisconnectPrompt() }
            )
        }

        uiState.error?.let { message ->
            ErrorDialog(
                message = message,
                onDismiss = { viewModel.clearError() }
            )
        }
    }
}

@Composable
private fun ErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        modifier = Modifier.border(4.dp, MaterialTheme.colorScheme.error, MaterialTheme.shapes.large),
        onDismissRequest = onDismiss,
        title = { Text("Fehler") },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onDismiss) { Text("OK") }
        }
    )
}

@Composable
private fun DisconnectPromptDialog(
    message: String,
    onConfirmAndMigrate: () -> Unit,
    onConfirmWithoutMigrate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Verbindung trennen?") },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onConfirmAndMigrate) {
                Text("Herunterladen & Trennen")
            }
        },
        dismissButton = {
            Column(horizontalAlignment = Alignment.End) {
                Button(
                    onClick = onConfirmWithoutMigrate,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Nur Trennen")
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("Abbrechen")
                }
            }
        }
    )
}

@Composable
private fun SyncPromptDialog(
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Synchronisation") },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Ja, hochladen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Später") }
        },
        modifier = Modifier.border(4.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large)
    )
}
