package de.pantastix.project.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import de.pantastix.project.ai.AiProviderType
import de.pantastix.project.model.gemini.Content
import de.pantastix.project.ui.viewmodel.CardListViewModel
import dev.icerock.moko.resources.compose.stringResource
import de.pantastix.project.shared.resources.MR
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: CardListViewModel = koinInject()) {
    val uiState by viewModel.uiState.collectAsState()
    var modelDropdownExpanded by remember { mutableStateOf(false) }
    var showParameterDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Automatisch nach unten scrollen bei neuen Nachrichten
    LaunchedEffect(uiState.chatMessages.size, uiState.isChatLoading) {
        if (uiState.chatMessages.isNotEmpty() || uiState.isChatLoading) {
            val lastIndex = if (uiState.isChatLoading) uiState.chatMessages.size else uiState.chatMessages.size - 1
            if (lastIndex >= 0) {
                listState.animateScrollToItem(lastIndex)
            }
        }
    }

    // Combine, Filter, and Group Models
    val modelFamilies = remember(uiState.availableGeminiModels, uiState.availableOllamaModels) {
        val families = mutableMapOf<String, MutableList<String>>() // Family Name -> List of Model IDs

        // 1. Gemini Filtering (Strict)
        val geminiRegex = Regex("""^(models/)?(gemini-[23]\.0-flash)$""", RegexOption.IGNORE_CASE)
        uiState.availableGeminiModels.forEach { model ->
            val pureName = model.substringAfter("models/")
            if (geminiRegex.matches(pureName)) {
                families.getOrPut(pureName) { mutableListOf() }.add(model)
            }
        }

        // 2. Ollama Filtering & Grouping
        // Regex for Gemma 3: gemma-3-<size>b-it
        val gemma3Regex = Regex("gemma-3-(\\d+)b-it", RegexOption.IGNORE_CASE)
        // Regex for GPT-OSS
        val gptOssRegex = Regex("gpt-oss(:.*)?", RegexOption.IGNORE_CASE)

        uiState.availableOllamaModels.forEach { model ->
            val name = model.lowercase()
            val gemmaMatch = gemma3Regex.find(name)
            if (gemmaMatch != null) {
                val size = gemmaMatch.groupValues[1]
                // Filter out 'e' prefix manually if regex missed it (though \d handles it)
                if (!name.contains("e${size}b")) {
                    families.getOrPut("Gemma 3") { mutableListOf() }.add(model)
                }
            } else if (gptOssRegex.matches(name)) {
                families.getOrPut("GPT-OSS") { mutableListOf() }.add(model)
            }
        }
        
        families.mapValues { it.value.sortedByDescending { id -> id }}
    }

    val currentModelId = if (uiState.selectedAiProvider == AiProviderType.GEMINI_CLOUD) {
        uiState.selectedGeminiModel
    } else {
        uiState.selectedOllamaModel
    }
    
    // Determine current family
    val currentFamilyName = modelFamilies.entries.find { it.value.contains(currentModelId) }?.key 
        ?: currentModelId.substringAfterLast("/").take(20)

    val isReady = (uiState.selectedAiProvider == AiProviderType.GEMINI_CLOUD && uiState.geminiApiKey.isNotBlank()) ||
                  (uiState.selectedAiProvider == AiProviderType.OLLAMA_LOCAL && uiState.ollamaHostUrl.isNotBlank())

    Column(modifier = Modifier.fillMaxSize()) {
        // --- TOP BAR ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Poké-Agent",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(MR.strings.chat_subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            // Model Selector & Settings
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Button(
                        onClick = { modelDropdownExpanded = true },
                        enabled = !uiState.isLoading && modelFamilies.isNotEmpty(),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Icon(Icons.Filled.SmartToy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = currentFamilyName)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = "Select Model")
                    }

                    DropdownMenu(
                        expanded = modelDropdownExpanded,
                        onDismissRequest = { modelDropdownExpanded = false }
                    ) {
                        modelFamilies.keys.forEach { familyName ->
                            val isGemini = familyName.startsWith("gemini")
                            DropdownMenuItem(
                                text = { 
                                    Column {
                                        Text(familyName)
                                        Text(
                                            if (isGemini) "Cloud (Gemini)" else "Local (Ollama)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                },
                                onClick = {
                                    val firstModel = modelFamilies[familyName]?.firstOrNull()
                                    if (firstModel != null) {
                                        viewModel.selectUnifiedModel(firstModel)
                                    }
                                    modelDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(Modifier.width(8.dp))
                
                IconButton(onClick = { showParameterDialog = true }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Parameters")
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            thickness = 4.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
        )

        // --- MESSAGE LIST ---
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (!isReady) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Filled.SmartToy,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Bitte konfiguriere einen AI Provider (Gemini API Key oder Ollama) in den Einstellungen.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.chatMessages) { message ->
                        if (message.role != "system" && message.role != "function") {
                             val isUser = message.role == "user"
                             ChatMessageItem(message, isUser)
                        }
                    }
                    if (uiState.isChatLoading) {
                        item {
                            ChatLoadingIndicator()
                        }
                    }
                }
            }
        }

        // --- INPUT AREA ---
        if (isReady) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = uiState.chatInput,
                    onValueChange = { viewModel.onChatInputChanged(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(MR.strings.chat_input_placeholder)) },
                    maxLines = 4,
                    enabled = !uiState.isChatLoading,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { viewModel.sendMessage() }),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = { viewModel.sendMessage() },
                            enabled = !uiState.isChatLoading && uiState.chatInput.isNotBlank(),
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary,
                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        ) {
                            Icon(Icons.Filled.Send, contentDescription = "Senden")
                        }
                    }
                )
            }
        }
    }
    
    if (showParameterDialog) {
        val currentVariants = modelFamilies[currentFamilyName] ?: emptyList()
        
        AlertDialog(
            onDismissRequest = { showParameterDialog = false },
            title = { Text("Model Settings") },
            text = {
                Column {
                    if (currentVariants.size > 1) {
                        Text("Modell-Variante wählen:", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        currentVariants.forEach { variantId ->
                            val displayVariant = if (variantId.contains("gemma-3")) {
                                "gemma3:" + variantId.substringAfter("gemma-3-").substringBefore("-it")
                            } else {
                                variantId.substringAfterLast("/")
                            }
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                RadioButton(
                                    selected = currentModelId == variantId,
                                    onClick = { viewModel.selectUnifiedModel(variantId) }
                                )
                                Text(displayVariant, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    } else {
                        Text("Aktuelles Modell: $currentModelId")
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    Text("Zukünftige Parameter (Temperature, etc.) kommen hier hinzu.")
                }
            },
            confirmButton = {
                Button(onClick = { showParameterDialog = false }) { Text("OK") }
            }
        )
    }
}

@Composable
fun ChatMessageItem(message: Content, isUser: Boolean) {
    val text = message.parts.firstOrNull { it.text != null }?.text ?: ""
    if (text.isBlank()) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.secondaryContainer,
            border = if (isUser) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 0.dp,
                bottomEnd = if (isUser) 0.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 600.dp)
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUser) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Text(
            text = if (isUser) "Du" else "Poké-Agent",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun ChatLoadingIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(8.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Poké-Agent denkt nach...",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}