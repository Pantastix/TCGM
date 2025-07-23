package de.pantastix.project.ui.screens

import androidx.compose.foundation.background
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: CardListViewModel = koinInject(), onNavigateToGuide: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    var supabaseUrl by remember(uiState.supabaseUrl) { mutableStateOf(uiState.supabaseUrl) }
    var supabaseKey by remember(uiState.supabaseKey) { mutableStateOf(uiState.supabaseKey) }
    var languageDropdownExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(MR.strings.settings_title), style = MaterialTheme.typography.headlineMedium)

            Text(stringResource(MR.strings.settings_language), style = MaterialTheme.typography.titleLarge)
            Box(
                modifier = Modifier.width(300.dp),
            ) {
                ExposedDropdownMenuBox(
                    expanded = languageDropdownExpanded && !uiState.isLoading,
                    onExpandedChange = { if (!uiState.isLoading) languageDropdownExpanded = !languageDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = uiState.appLanguage.displayName,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageDropdownExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        enabled = !uiState.isLoading
                    )
                    ExposedDropdownMenu(
                        expanded = languageDropdownExpanded,
                        onDismissRequest = { languageDropdownExpanded = false }
                    ) {
                        AppLanguage.entries.forEach { lang ->
                            DropdownMenuItem(
                                text = { Text(lang.displayName) },
                                onClick = {
                                    viewModel.setAppLanguage(lang)
                                    languageDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(MR.strings.settings_cloud_sync), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onNavigateToGuide, enabled = !uiState.isLoading) {
                    Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = stringResource(MR.strings.settings_guide_button_desc), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(MR.strings.settings_guide_button))
                }
            }

            Text(
                stringResource(MR.strings.settings_cloud_sync_description),
                style = MaterialTheme.typography.bodySmall
            )

            OutlinedTextField(
                value = supabaseUrl,
                onValueChange = { supabaseUrl = it },
                label = { Text(stringResource(MR.strings.settings_supabase_url_label)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading
            )

            OutlinedTextField(
                value = supabaseKey,
                onValueChange = { supabaseKey = it },
                label = { Text(stringResource(MR.strings.settings_supabase_anon_key_label)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.connectNewToSupabase(supabaseUrl, supabaseKey) },
                    enabled = !uiState.isLoading && !uiState.isSupabaseConnected
                ) {
                    Text(stringResource(MR.strings.settings_connect_button))
                }
                Button(
                    onClick = viewModel::disconnectFromSupabase,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = !uiState.isLoading && uiState.isSupabaseConnected
                ) {
                    Text(stringResource(MR.strings.settings_disconnect_button))
                }
            }

            if (uiState.isSupabaseConnected) {
                Text(stringResource(MR.strings.settings_status_connected), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            } else {
                Text(stringResource(MR.strings.settings_status_disconnected), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
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

        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    uiState.loadingMessage?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.surface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorDialog(message: String, onDismiss: () -> Unit) {
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

@Composable
private fun DisconnectPromptDialog(
    message: String,
    onConfirmAndMigrate: () -> Unit,
    onConfirmWithoutMigrate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.settings_disconnect_dialog_title)) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onConfirmAndMigrate) {
                Text(stringResource(MR.strings.settings_disconnect_dialog_migrate_button))
            }
        },
        dismissButton = {
            Column(horizontalAlignment = Alignment.End) {
                Button(
                    onClick = onConfirmWithoutMigrate,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(MR.strings.settings_disconnect_dialog_disconnect_only_button))
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(MR.strings.settings_disconnect_dialog_cancel_button))
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
        title = { Text(stringResource(MR.strings.settings_sync_dialog_title)) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onConfirm) { Text(stringResource(MR.strings.settings_sync_dialog_confirm_button)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.settings_sync_dialog_dismiss_button)) }
        },
        modifier = Modifier.border(4.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large)
    )
}
