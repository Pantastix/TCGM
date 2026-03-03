package de.pantastix.project.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.pantastix.project.ai.AiProviderType
import de.pantastix.project.model.gemini.Content
import de.pantastix.project.ui.components.MarkdownText
import de.pantastix.project.ui.viewmodel.CardListViewModel
import dev.icerock.moko.resources.compose.stringResource
import de.pantastix.project.shared.resources.MR
import org.koin.compose.koinInject

// Helper interface for grouped messages
private sealed interface ChatUiItem {
    data class User(val message: Content) : ChatUiItem
    data class AiGroup(val messages: List<Content>) : ChatUiItem
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: CardListViewModel = koinInject()) {
    val uiState by viewModel.uiState.collectAsState()
    var modelDropdownExpanded by remember { mutableStateOf(false) }
    var showParameterDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Grouping Logic: Combine consecutive AI messages
    val groupedMessages = remember(uiState.chatMessages) {
        val groups = mutableListOf<ChatUiItem>()
        var currentAiMessages = mutableListOf<Content>()

        fun flushAi() {
            if (currentAiMessages.isNotEmpty()) {
                groups.add(ChatUiItem.AiGroup(currentAiMessages.toList()))
                currentAiMessages.clear()
            }
        }

        uiState.chatMessages.forEach { msg ->
            if (msg.role == "user") {
                flushAi()
                groups.add(ChatUiItem.User(msg))
            } else if (msg.role != "system" && msg.role != "function") {
                // Assuming everything else (model, assistant) is AI
                currentAiMessages.add(msg)
            }
        }
        flushAi()
        groups
    }

    // Scroll to bottom on new messages
    LaunchedEffect(groupedMessages.size, uiState.isChatLoading, uiState.currentThought) {
        if (groupedMessages.isNotEmpty() || uiState.isChatLoading) {
            val lastIndex = groupedMessages.size - 1
            if (lastIndex >= 0) {
                listState.animateScrollToItem(lastIndex)
            }
        }
    }

    // Combine, Filter, and Group Models
    val modelFamilies = remember(uiState.availableGeminiModels, uiState.availableOllamaModels) {
        val families = mutableMapOf<String, MutableList<String>>() // Family Name -> List of Model IDs

        // 1. Gemini Filtering
        uiState.availableGeminiModels.forEach { model ->
            val family = de.pantastix.project.ai.AiModelRegistry.resolveFamily(model, de.pantastix.project.ai.ModelCategory.GEMINI_CLOUD)
            if (family != null && family.filter(model)) {
                families.getOrPut(family.displayName) { mutableListOf() }.add(model)
            }
        }

        // 2. Ollama Filtering & Grouping
        uiState.availableOllamaModels.forEach { model ->
             val family = de.pantastix.project.ai.AiModelRegistry.resolveFamily(model, de.pantastix.project.ai.ModelCategory.OLLAMA_LOCAL)
             if (family != null && family.filter(model)) {
                 families.getOrPut(family.displayName) { mutableListOf() }.add(model)
             }
        }
        
        // Sort individual lists based on family comparator or default
        families.mapValues { (familyName, models) ->
            val sampleModel = models.firstOrNull() ?: return@mapValues models
            // Try to find the family definition again to get the comparator (a bit inefficient but safe)
            // We assume models in the list belong to the same family (which they should by design above)
            val family = de.pantastix.project.ai.AiModelRegistry.families.find { it.displayName == familyName }
            
            if (family?.modelComparator != null) {
                models.sortedWith(family.modelComparator)
            } else {
                models.sortedByDescending { it }
            }
        }
    }

    val currentModelId = if (uiState.selectedAiProvider == AiProviderType.GEMINI_CLOUD) {
        uiState.selectedGeminiModel
    } else {
        uiState.selectedOllamaModel
    }
    
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
                        Text(text = currentFamilyName.replace(" (Cloud)", "").replace(" (Local)", ""))
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = "Select Model")
                    }

                    DropdownMenu(
                        expanded = modelDropdownExpanded,
                        onDismissRequest = { modelDropdownExpanded = false }
                    ) {
                        modelFamilies.keys.forEach { familyName ->
                            val isCloud = familyName.contains("(Cloud)") || familyName.startsWith("gemini", ignoreCase = true)
                            DropdownMenuItem(
                                text = { 
                                    Column {
                                        Text(familyName.replace(" (Cloud)", "").replace(" (Local)", ""))
                                        Text(
                                            if (isCloud) "Cloud (Gemini)" else "Local (Ollama)",
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

                IconButton(
                    onClick = { viewModel.clearChat() },
                    enabled = uiState.chatMessages.isNotEmpty() && !uiState.isChatLoading
                ) {
                    Icon(
                        Icons.Filled.DeleteSweep, 
                        contentDescription = "Clear Chat",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
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
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(groupedMessages) { item ->
                             // Determine if this is the very last item in the list and we are still loading
                             val isLast = item == groupedMessages.last()
                             val isGenerating = isLast && uiState.isChatLoading
                             
                             when (item) {
                                 is ChatUiItem.User -> UserMessageItem(item.message)
                                 is ChatUiItem.AiGroup -> AiMessageGroupItem(item.messages, isGenerating)
                             }
                        }
                    }
                    
                    // Error Display Overlay
                    if (uiState.error != null) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                                .fillMaxWidth(),
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = uiState.error ?: "",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { viewModel.clearError() }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
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
    
    // Dialog code
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
fun UserMessageItem(message: Content) {
    val text = message.parts.firstOrNull { it.text != null }?.text ?: ""
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp),
            modifier = Modifier.widthIn(max = 600.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Text(
            text = "Du",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp, end = 4.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun AiMessageGroupItem(messages: List<Content>, isGenerating: Boolean) {
    // Collect all unique non-blank thoughts and texts
    val thoughts = messages.mapNotNull { it.thought }.filter { it.isNotBlank() }
    val texts = messages.flatMap { it.parts }.mapNotNull { it.text }.filter { it.isNotBlank() }

    // If nothing to show and not generating, return
    if (thoughts.isEmpty() && texts.isEmpty() && !isGenerating) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 32.dp)
    ) {
        var isThoughtVisible by remember { mutableStateOf(false) }
        val hasThoughts = thoughts.isNotEmpty()

        // 1. Header (Once per group)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (hasThoughts) Modifier.clickable { isThoughtVisible = !isThoughtVisible } else Modifier)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isGenerating) Icons.Filled.AutoAwesome else Icons.Filled.SmartToy,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isGenerating && texts.isEmpty()) "Poké-Agent denkt nach..." else "Poké-Agent",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            if (hasThoughts) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = if (isThoughtVisible) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = "Toggle Thought",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
                Text(
                   text = "(${thoughts.size})",
                   style = MaterialTheme.typography.labelSmall,
                   color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                   modifier = Modifier.padding(start = 2.dp)
                )
            }
        }

        // 2. Thoughts (List of thoughts)
        if (hasThoughts) {
            AnimatedVisibility(visible = isThoughtVisible) {
                Column {
                    thoughts.forEachIndexed { index, thought ->
                        Row(
                            modifier = Modifier
                                .padding(start = 10.dp, top = 4.dp, bottom = 8.dp)
                                .height(IntrinsicSize.Min)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(3.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                            )
                            Spacer(Modifier.width(12.dp))
                            MarkdownText(
                                markdown = thought,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        if (index < thoughts.size - 1) {
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
        }

        // 3. Answer Content (Texts)
        if (texts.isNotEmpty()) {
            Spacer(Modifier.height(0.dp))
            texts.forEachIndexed { idx, text ->
                if (idx > 0) Spacer(Modifier.height(8.dp))
                MarkdownText(
                    markdown = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        } else if (isGenerating && !hasThoughts) {
            TypingIndicator()
        }
        
        // 4. Typing Indicator (bottom)
        if (isGenerating && texts.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            TypingIndicator()
        }
    }
}

@Composable
fun TypingIndicator(
    dotSize: Dp = 8.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    delayUnit: Int = 300
) {
    val maxOffset = 6f
    
    @Composable
    fun Dot(offset: Int) {
        val transition = rememberInfiniteTransition()
        val yOffset by transition.animateFloat(
            initialValue = 0f,
            targetValue = -maxOffset,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = delayUnit * 4
                    0f at 0 with LinearEasing
                    -maxOffset at delayUnit with LinearEasing
                    0f at delayUnit * 2 with LinearEasing
                },
                repeatMode = RepeatMode.Restart,
                initialStartOffset = StartOffset(offset)
            )
        )
        
        Box(
            modifier = Modifier
                .offset(y = yOffset.dp)
                .size(dotSize)
                .clip(CircleShape)
                .background(color)
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp, start = 4.dp)
    ) {
        Dot(0)
        Dot(delayUnit)
        Dot(delayUnit * 2)
    }
}
