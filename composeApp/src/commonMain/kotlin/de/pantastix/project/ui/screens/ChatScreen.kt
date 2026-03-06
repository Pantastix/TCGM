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
import androidx.compose.ui.input.key.*
import coil3.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
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
    var providerDropdownExpanded by remember { mutableStateOf(false) }
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

    // Model families for the CURRENT selected provider
    val currentProviderStatus = uiState.aiProviders[uiState.selectedAiProvider]
    val modelFamilies = remember(uiState.selectedAiProvider, currentProviderStatus?.availableModels) {
        val families = mutableMapOf<String, MutableList<String>>()
        val models = currentProviderStatus?.availableModels ?: emptyList()
        val category = when(uiState.selectedAiProvider) {
            AiProviderType.GEMINI_CLOUD -> de.pantastix.project.ai.ModelCategory.GEMINI_CLOUD
            AiProviderType.OLLAMA_LOCAL -> de.pantastix.project.ai.ModelCategory.OLLAMA_LOCAL
            AiProviderType.MISTRAL_CLOUD -> de.pantastix.project.ai.ModelCategory.MISTRAL_CLOUD
            AiProviderType.CLAUDE_CLOUD -> de.pantastix.project.ai.ModelCategory.CLAUDE_CLOUD
        }

        models.forEach { model ->
            val family = de.pantastix.project.ai.AiModelRegistry.resolveFamily(model, category)
            if (family != null && family.filter(model)) {
                families.getOrPut(family.displayName) { mutableListOf() }.add(model)
            } else if (family == null) {
                families.getOrPut("Andere") { mutableListOf() }.add(model)
            }
        }
        
        families.mapValues { (familyName, familyModels) ->
            val familyDef = de.pantastix.project.ai.AiModelRegistry.families.find { it.displayName == familyName }
            if (familyDef?.modelComparator != null) {
                familyModels.sortedWith(familyDef.modelComparator)
            } else {
                familyModels.sortedByDescending { it }
            }
        }
    }

    val currentModelId = currentProviderStatus?.selectedModel ?: ""
    val currentFamilyName = modelFamilies.entries.find { it.value.contains(currentModelId) }?.key ?: "Modell wählen"

    val isReady = currentProviderStatus?.isConfigured == true

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

            // Provider & Model Selectors
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 1. Provider Select
                Box {
                    AssistChip(
                        onClick = { providerDropdownExpanded = true },
                        label = { Text(currentProviderStatus?.label ?: "Provider") },
                        leadingIcon = { Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )

                    DropdownMenu(
                        expanded = providerDropdownExpanded,
                        onDismissRequest = { providerDropdownExpanded = false }
                    ) {
                        uiState.aiProviders.values.forEach { provider ->
                            DropdownMenuItem(
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier.size(8.dp).clip(CircleShape)
                                                .background(if (provider.isConfigured) Color(0xFF4CAF50) else Color.Gray)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(provider.label)
                                    }
                                },
                                onClick = {
                                    viewModel.setAiProvider(provider.type)
                                    providerDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(Modifier.width(8.dp))

                // 2. Model Family Select
                Box {
                    Button(
                        onClick = { modelDropdownExpanded = true },
                        enabled = !uiState.isLoading && modelFamilies.isNotEmpty(),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
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
                        if (modelFamilies.isEmpty()) {
                            DropdownMenuItem(text = { Text("Keine Modelle geladen") }, onClick = {})
                        }
                        modelFamilies.keys.forEach { familyName ->
                            DropdownMenuItem(
                                text = { Text(familyName) },
                                onClick = {
                                    val firstModel = modelFamilies[familyName]?.firstOrNull()
                                    if (firstModel != null) {
                                        viewModel.selectModelForProvider(uiState.selectedAiProvider, firstModel)
                                    }
                                    modelDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(Modifier.width(8.dp))
                
                IconButton(onClick = { showParameterDialog = true }, enabled = currentFamilyName != "Modell wählen") {
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
            thickness = 2.dp,
            color = MaterialTheme.colorScheme.outlineVariant
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
                        Icons.Filled.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "${currentProviderStatus?.label ?: "Dieser Provider"} ist noch nicht konfiguriert.",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(
                        "Bitte hinterlege einen API-Key in den Einstellungen.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                             val isLast = item == groupedMessages.last()
                             val isGenerating = isLast && uiState.isChatLoading
                             
                             when (item) {
                                 is ChatUiItem.User -> UserMessageItem(item.message)
                                 is ChatUiItem.AiGroup -> AiMessageGroupItem(item.messages, isGenerating)
                             }
                        }
                        
                        // NEW: Pending Actions
                        if (uiState.pendingChatActions.isNotEmpty()) {
                            item {
                                PendingActionsCard(
                                    actions = uiState.pendingChatActions,
                                    isLoading = uiState.isLoading,
                                    onConfirm = { viewModel.confirmPendingActions(true) },
                                    onCancel = { viewModel.confirmPendingActions(false) }
                                )
                            }
                        }
                    }
                    
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
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = "Error", tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = uiState.error ?: "",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                IconButton(onClick = { viewModel.clearError() }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- INPUT AREA ---
        if (isReady) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = uiState.chatInput,
                    onValueChange = { viewModel.onChatInputChanged(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                if (event.isShiftPressed) {
                                    // Shift + Enter: Standardverhalten (neue Zeile) zulassen
                                    false
                                } else {
                                    // Enter ohne Shift: Nachricht senden
                                    if (uiState.chatInput.isNotBlank() && !uiState.isChatLoading && currentModelId.isNotBlank()) {
                                        viewModel.sendMessage()
                                    }
                                    true // Event konsumieren
                                }
                            } else {
                                false
                            }
                        },
                    placeholder = { Text(stringResource(MR.strings.chat_input_placeholder)) },
                    maxLines = 4,
                    enabled = !uiState.isChatLoading && currentModelId.isNotBlank(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    shape = RoundedCornerShape(24.dp),
                    trailingIcon = {
                        IconButton(
                            onClick = { viewModel.sendMessage() },
                            enabled = !uiState.isChatLoading && uiState.chatInput.isNotBlank() && currentModelId.isNotBlank(),
                        ) {
                            Icon(Icons.Filled.Send, contentDescription = "Senden")
                        }
                    }
                )
                
                Spacer(Modifier.height(8.dp))
                
                Text(
                    text = stringResource(MR.strings.ai_disclaimer),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
    
    // Parameter Dialog
    if (showParameterDialog) {
        val currentVariants = modelFamilies[currentFamilyName] ?: emptyList()
        AlertDialog(
            onDismissRequest = { showParameterDialog = false },
            title = { Text("Variante für $currentFamilyName") },
            text = {
                Column {
                    if (currentVariants.size > 1) {
                        currentVariants.forEach { variantId ->
                            val displayVariant = variantId.substringAfterLast("/").substringAfterLast(":")
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { 
                                    viewModel.selectModelForProvider(uiState.selectedAiProvider, variantId)
                                }
                            ) {
                                RadioButton(
                                    selected = currentModelId == variantId,
                                    onClick = { viewModel.selectModelForProvider(uiState.selectedAiProvider, variantId) }
                                )
                                Text(displayVariant, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    } else {
                        Text("Aktuelles Modell: $currentModelId", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showParameterDialog = false }) { Text("Schließen") }
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
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            shape = RoundedCornerShape(16.dp, 16.dp, 0.dp, 16.dp),
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
    }
}

@Composable
fun AiMessageGroupItem(messages: List<Content>, isGenerating: Boolean) {
    val thoughts = messages.mapNotNull { it.thought }.filter { it.isNotBlank() }
    val texts = messages.flatMap { it.parts }.mapNotNull { it.text }.filter { it.isNotBlank() }

    if (thoughts.isEmpty() && texts.isEmpty() && !isGenerating) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 32.dp)
    ) {
        var isThoughtVisible by remember { mutableStateOf(false) }
        val hasThoughts = thoughts.isNotEmpty()

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
            }
        }

        if (hasThoughts) {
            AnimatedVisibility(visible = isThoughtVisible) {
                Column {
                    thoughts.forEach { thought ->
                        Row(
                            modifier = Modifier
                                .padding(start = 10.dp, top = 4.dp, bottom = 8.dp)
                                .height(IntrinsicSize.Min)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(2.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                            )
                            Spacer(Modifier.width(12.dp))
                            MarkdownText(
                                markdown = thought,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }

        if (texts.isNotEmpty()) {
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
        
        if (isGenerating && texts.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            TypingIndicator()
        }
    }
}

@Composable
fun PendingActionsCard(
    actions: List<de.pantastix.project.ui.viewmodel.PendingChatAction>,
    isLoading: Boolean = false,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Inventory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Geplante Änderungen",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(Modifier.height(12.dp))
            
            actions.forEach { action ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Card Image
                    Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)).background(Color.Gray.copy(alpha = 0.2f))) {
                        if (action.imageUrl != null) {
                            AsyncImage(
                                model = "${action.imageUrl}/low.jpg",
                                contentDescription = action.cardName,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    
                    Spacer(Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(action.cardName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            if (action.actionType == de.pantastix.project.ui.viewmodel.PendingActionType.ADD) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "NEU",
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        val detailText = if (action.actionType == de.pantastix.project.ui.viewmodel.PendingActionType.ADD) {
                             "Hinzufügen: ${action.newCount} Exemplar(e) (${action.selectedPriceSource})"
                        } else {
                             "${action.currentCount} → ${action.newCount} Exemplare"
                        }
                        Text(
                            detailText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    if (action.actionType == de.pantastix.project.ui.viewmodel.PendingActionType.ADD) {
                         Text(
                            "+${action.newCount}",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold
                        )
                    } else if (action.newCount == 0) {
                        Text(
                            "WIRD ENTFERNT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (action.change > 0) {
                        Text(
                            "+${action.change}",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            "${action.change}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Abbrechen")
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Bestätigen")
                    }
                }
            }
        }
    }
}

@Composable
fun TypingIndicator(
    dotSize: Dp = 6.dp,
    color: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
    delayUnit: Int = 300
) {
    val maxOffset = 4f
    
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
