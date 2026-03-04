package de.pantastix.project.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.pantastix.project.ai.AiProviderType
import de.pantastix.project.shared.resources.MR
import de.pantastix.project.ui.components.ErrorDialog
import de.pantastix.project.ui.viewmodel.AppLanguage
import de.pantastix.project.ui.viewmodel.CardListViewModel
import dev.icerock.moko.resources.compose.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(viewModel: CardListViewModel = koinInject(), onNavigateToGuide: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    var supabaseUrl by remember(uiState.supabaseUrl) { mutableStateOf(uiState.supabaseUrl) }
    var supabaseKey by remember(uiState.supabaseKey) { mutableStateOf(uiState.supabaseKey) }
    var languageDropdownExpanded by remember { mutableStateOf(false) }
    var showAiDialog by remember { mutableStateOf(false) }

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

            // --- AI SECTION ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(MR.strings.settings_ai_assistant), style = MaterialTheme.typography.titleLarge)
                Button(
                    onClick = { showAiDialog = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Konfigurieren")
                }
            }

            AiStatusSummary(uiState.aiProviders)

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // --- CLOUD SYNC SECTION ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(MR.strings.settings_cloud_sync), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onNavigateToGuide, enabled = !uiState.isLoading) {
                    Icon(
                        Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = stringResource(MR.strings.settings_guide_button_desc),
                        modifier = Modifier.size(18.dp)
                    )
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
                Text(
                    stringResource(MR.strings.settings_status_connected),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    stringResource(MR.strings.settings_status_disconnected),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
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
                    LoadingIndicator()
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
        
        if (showAiDialog) {
            AiConfigurationDialog(
                uiState = uiState,
                onDismiss = { showAiDialog = false },
                onUpdateSettings = { type, key, url -> viewModel.updateAiProviderSettings(type, key, url) }
            )
        }
    }
}

@Composable
fun AiStatusSummary(providers: Map<AiProviderType, de.pantastix.project.ui.viewmodel.AiProviderStatus>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        providers.values.forEach { status ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (status.isConfigured) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (status.isConfigured) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${status.label}: ",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (status.isConfigured) "Konfiguriert" else "Nicht konfiguriert",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (status.isConfigured) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiConfigurationDialog(
    uiState: de.pantastix.project.ui.viewmodel.UiState,
    onDismiss: () -> Unit,
    onUpdateSettings: (AiProviderType, String?, String?) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("KI-Dienste konfigurieren", style = MaterialTheme.typography.headlineSmall)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Schließen")
                    }
                }
                
                HorizontalDivider()
                
                // Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Hier kannst du deine API-Keys und lokalen Instanzen verwalten. Die Modelle wählst du später direkt im Chat.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    AiProviderType.entries.forEach { type ->
                        val status = uiState.aiProviders[type] ?: return@forEach
                        ProviderConfigCard(
                            status = status,
                            onUpdate = { key, url -> onUpdateSettings(type, key, url) }
                        )
                    }
                }
                
                HorizontalDivider()
                
                // Footer
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.CenterEnd) {
                    Button(onClick = onDismiss) {
                        Text("Fertig")
                    }
                }
            }
        }
    }
}

@Composable
fun ProviderConfigCard(
    status: de.pantastix.project.ui.viewmodel.AiProviderStatus,
    onUpdate: (String?, String?) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var tempKey by remember(status.apiKey) { mutableStateOf(status.apiKey) }
    var tempUrl by remember(status.hostUrl) { mutableStateOf(status.hostUrl) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (status.isConfigured) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) 
                            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, if (status.isConfigured) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) 
                                    else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp))
                            .background(if (status.isConfigured) Color(0xFF4CAF50) else Color.Gray)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(status.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                
                if (!isEditing) {
                    IconButton(onClick = { isEditing = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Bearbeiten", modifier = Modifier.size(20.dp))
                    }
                }
            }
            
            if (isEditing) {
                Spacer(Modifier.height(12.dp))
                if (status.type == AiProviderType.OLLAMA_LOCAL) {
                    OutlinedTextField(
                        value = tempUrl,
                        onValueChange = { tempUrl = it },
                        label = { Text("Host URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                } else {
                    OutlinedTextField(
                        value = tempKey,
                        onValueChange = { tempKey = it },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { 
                        isEditing = false
                        tempKey = status.apiKey
                        tempUrl = status.hostUrl
                    }) {
                        Text("Abbrechen")
                    }
                    Button(onClick = { 
                        onUpdate(if (tempKey != status.apiKey) tempKey else null, if (tempUrl != status.hostUrl) tempUrl else null)
                        isEditing = false 
                    }) {
                        Text("Speichern")
                    }
                }
            } else {
                if (status.isConfigured) {
                    val displayValue = if (status.type == AiProviderType.OLLAMA_LOCAL) status.hostUrl else "••••••••••••••••"
                    Text(
                        text = displayValue,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DisconnectPromptDialog(
    message: String,
    onConfirmAndMigrate: () -> Unit,
    onConfirmWithoutMigrate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        modifier = Modifier.border(4.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.settings_disconnect_dialog_title)) },
        text = { Text(message) },
        confirmButton = {
            Row {
                Button(
                    onClick = onConfirmWithoutMigrate,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(MR.strings.settings_disconnect_dialog_disconnect_only_button))
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onConfirmAndMigrate) {
                    Text(stringResource(MR.strings.settings_disconnect_dialog_migrate_button))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.settings_disconnect_dialog_cancel_button))
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
