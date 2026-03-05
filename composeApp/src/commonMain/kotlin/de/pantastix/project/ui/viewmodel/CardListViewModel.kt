package de.pantastix.project.ui.viewmodel

import androidx.annotation.RequiresApi
import de.pantastix.project.ai.AiConfig
import de.pantastix.project.ai.AiProviderType
import de.pantastix.project.ai.ChatRole
import de.pantastix.project.ai.ChatMessage
import de.pantastix.project.ai.ToolResponseData
import de.pantastix.project.ai.provider.GeminiCloudService
import de.pantastix.project.ai.provider.OllamaService
import de.pantastix.project.ai.provider.MistralService
import de.pantastix.project.ai.provider.ClaudeService
import de.pantastix.project.coroutines.ioDispatcher
import de.pantastix.project.model.Ability
import de.pantastix.project.model.Attack
import de.pantastix.project.model.PokemonCard
import de.pantastix.project.model.PokemonCardInfo
import de.pantastix.project.model.SetInfo
import de.pantastix.project.model.GradedCopy
import de.pantastix.project.model.PortfolioSnapshot
import de.pantastix.project.model.PortfolioSnapshotItem
import de.pantastix.project.model.api.*
import de.pantastix.project.model.gemini.*
import de.pantastix.project.platform.getSystemLanguage
import de.pantastix.project.platform.setAppLanguage
import de.pantastix.project.repository.CardRepository
import de.pantastix.project.repository.SettingsRepository
import de.pantastix.project.repository.SupabaseCardRepository
import de.pantastix.project.service.GeminiService
import de.pantastix.project.service.TcgApiService
import de.pantastix.project.service.UpdateChecker
import de.pantastix.project.ui.screens.PriceSchema
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import java.io.File
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException
import kotlin.system.exitProcess
import kotlin.time.ExperimentalTime

enum class AppLanguage(val code: String, val displayName: String) {
    GERMAN("de", "Deutsch"),
    ENGLISH("en", "English")
}

enum class CardLanguage(val code: String, val displayName: String) {
    GERMAN("de", "Deutsch"),
    ENGLISH("en", "English"),
    FRENCH("fr", "Français"),
    SPANISH("es", "Español"),
    ITALIAN("it", "Italiano"),
    PORTUGUESE("pt", "Português"),
    JAPANESE("jp", "Japanisch")
}

sealed class FilterCondition {
    data class ByName(val nameQuery: String) : FilterCondition()
    data class ByLanguage(val languageCode: String) : FilterCondition()
    data class ByNumericValue(
        val attribute: NumericAttribute,
        val comparison: ComparisonType,
        val value: Double
    ) : FilterCondition()
}

enum class NumericAttribute { OWNED_COPIES, CURRENT_PRICE }
enum class ComparisonType { EQUAL, GREATER_THAN, LESS_THAN }

data class Sort(val sortBy: String, val ascending: Boolean)

data class BulkUpdateProgress(
    val inProgress: Boolean = false,
    val processed: Int = 0,
    val total: Int = 0,
    val currentStepMessage: String? = null
)

data class AiProviderStatus(
    val type: AiProviderType,
    val isConfigured: Boolean,
    val label: String,
    val apiKey: String = "",
    val hostUrl: String = "",
    val availableModels: List<String> = emptyList(),
    val selectedModel: String = ""
)

data class UiState(
    val isInitialized: Boolean = false,
    val cardInfos: List<PokemonCardInfo> = emptyList(),
    val sets: List<SetInfo> = emptyList(),
    val selectedCardDetails: PokemonCard? = null,
    val apiCardDetails: TcgDexCardResponse? = null,
    val englishApiCardDetails: TcgDexCardResponse? = null,
    val searchedCardLanguage: CardLanguage? = null,
    val isLoading: Boolean = false,
    val loadingMessage: String? = null,
    val error: String? = null,
    val appLanguage: AppLanguage = AppLanguage.GERMAN,
    
    // Supabase
    val supabaseUrl: String = "",
    val supabaseKey: String = "",
    val isSupabaseConnected: Boolean = false,
    val syncPromptMessage: String? = null,
    val disconnectPromptMessage: String? = null,
    
    // Updates
    val updateInfo: UpdateInfo? = null,
    val setsUpdateWarning: String? = null,
    
    // Filters & Sort
    val filters: List<FilterCondition> = emptyList(),
    val sort: Sort = Sort("nameLocal", true),
    
    // Editing & Bulk
    val editingCardApiDetails: TcgDexCardResponse? = null,
    val isEditingDetailsLoading: Boolean = false,
    val bulkUpdateProgress: BulkUpdateProgress = BulkUpdateProgress(),
    val canBulkUpdatePrices: Boolean = false,
    
    // AI - NEW STRUCTURE
    val selectedAiProvider: AiProviderType = AiProviderType.GEMINI_CLOUD,
    val aiProviders: Map<AiProviderType, AiProviderStatus> = mapOf(
        AiProviderType.GEMINI_CLOUD to AiProviderStatus(AiProviderType.GEMINI_CLOUD, false, "Google Gemini"),
        AiProviderType.OLLAMA_LOCAL to AiProviderStatus(AiProviderType.OLLAMA_LOCAL, false, "Ollama (Local)", hostUrl = "http://localhost:11434"),
        AiProviderType.MISTRAL_CLOUD to AiProviderStatus(AiProviderType.MISTRAL_CLOUD, false, "Mistral AI"),
        AiProviderType.CLAUDE_CLOUD to AiProviderStatus(AiProviderType.CLAUDE_CLOUD, false, "Anthropic Claude")
    ),
    
    // AI - Chat State
    val chatMessages: List<Content> = emptyList(),
    val chatInput: String = "",
    val isChatLoading: Boolean = false,
    val currentThought: String? = null,

    // Portfolio Monitor
    val portfolioSnapshots: List<PortfolioSnapshot> = emptyList(),
    val selectedSnapshotDate: String? = null,
    val selectedSnapshotItems: List<PortfolioSnapshotItem> = emptyList(),
    val isPortfolioLoading: Boolean = false,

    // Set Overview
    val setProgressList: List<de.pantastix.project.model.SetProgress> = emptyList(),
    val cardsBySet: List<PokemonCardInfo> = emptyList()
)

@OptIn(ExperimentalTime::class)
class CardListViewModel(
    private val localCardRepository: CardRepository,
    private val settingsRepository: SettingsRepository,
    private val apiService: TcgApiService,
    private val geminiService: GeminiService,
    private val geminiCloudService: GeminiCloudService,
    private val ollamaService: OllamaService,
    private val mistralService: MistralService,
    private val claudeService: ClaudeService,
    private val toolRegistry: de.pantastix.project.ai.tool.ToolRegistry,
    private val migrationManager: de.pantastix.project.ai.migration.MigrationManager,
    private val typeService: de.pantastix.project.service.TypeService,
    private val viewModelScope: CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher)
) {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val availableLanguages = MutableStateFlow(CardLanguage.entries.map { it.code })

    // Tools Registry
    private val registeredTools = mutableListOf<de.pantastix.project.ai.tool.AgentTool>()

    // Internal Management
    private var remoteCardRepository: CardRepository? = null
    private val activeCardRepository: CardRepository
        get() = remoteCardRepository ?: localCardRepository

    private var cardInfosCollectionJob: Job? = null
    private var setsCollectionJob: Job? = null
    
    fun dismissSetsUpdateWarning() {
        _uiState.update { it.copy(setsUpdateWarning = null) }
    }

    fun initialize() {
        viewModelScope.launch {
            _uiState.update { it.copy(loadingMessage = "Initialisiere Sprache...") }
            initializeLanguage()
            
            initializeAi()

            _uiState.update { it.copy(loadingMessage = "Prüfe Cloud-Verbindung...") }
            initializeSupabaseConnection()

            // Initial Type Loading
            typeService.setRepository(activeCardRepository)
            typeService.refresh()

            val isFirstLaunch = activeCardRepository.isSetStorageEmpty()

            val success = if (isFirstLaunch) {
                handleFirstLaunchSetLoading()
            } else {
                handleSubsequentLaunchSetLoading()
            }

            if (success) {
                _uiState.update { it.copy(loadingMessage = "Lade Kartensammlung...") }
                startBackgroundDataListeners()
                checkBulkUpdateEligibility()
                val update = UpdateChecker.checkForUpdate()
                _uiState.update {
                    it.copy(
                        isInitialized = true,
                        updateInfo = update,
                        isLoading = false,
                        loadingMessage = null
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, loadingMessage = null) }
            }
        }
    }

    // --- AI Integration ---

    private suspend fun initializeAi() {
        val savedProviderStr = settingsRepository.getSetting("ai_provider")
        val provider = try { AiProviderType.valueOf(savedProviderStr ?: "") } catch(e: Exception) { AiProviderType.GEMINI_CLOUD }
        _uiState.update { it.copy(selectedAiProvider = provider) }

        // Initialize each provider from settings
        AiProviderType.entries.forEach { type ->
            val prefix = type.name.lowercase()
            val apiKey = settingsRepository.getSetting("${prefix}_api_key") ?: ""
            val hostUrl = settingsRepository.getSetting("${prefix}_host_url") ?: (if (type == AiProviderType.OLLAMA_LOCAL) "http://localhost:11434" else "")
            val selectedModel = settingsRepository.getSetting("${prefix}_selected_model") ?: ""
            
            updateProviderStatus(type) { it.copy(
                apiKey = apiKey,
                hostUrl = hostUrl,
                selectedModel = selectedModel,
                isConfigured = apiKey.isNotBlank() || (type == AiProviderType.OLLAMA_LOCAL && hostUrl.isNotBlank())
            )}
            
            // Initial model refresh
            if (apiKey.isNotBlank() || (type == AiProviderType.OLLAMA_LOCAL && hostUrl.isNotBlank())) {
                refreshModelsForProvider(type)
            }
        }

        reinitializeTools()
    }

    private fun updateProviderStatus(type: AiProviderType, update: (AiProviderStatus) -> AiProviderStatus) {
        _uiState.update { state ->
            val currentProviders = state.aiProviders.toMutableMap()
            val currentStatus = currentProviders[type] ?: AiProviderStatus(type, false, type.name)
            currentProviders[type] = update(currentStatus)
            state.copy(aiProviders = currentProviders)
        }
    }

    private fun reinitializeTools() {
        registeredTools.clear()
        registeredTools.addAll(toolRegistry.getAvailableTools(activeCardRepository, apiService))
    }

    fun setAiProvider(provider: AiProviderType) {
        viewModelScope.launch {
            settingsRepository.saveSetting("ai_provider", provider.name)
            _uiState.update { it.copy(selectedAiProvider = provider) }
            val status = uiState.value.aiProviders[provider]
            if (status != null && status.availableModels.isEmpty() && status.isConfigured) {
                refreshModelsForProvider(provider)
            }
        }
    }

    fun updateAiProviderSettings(type: AiProviderType, apiKey: String? = null, hostUrl: String? = null) {
        viewModelScope.launch {
            val prefix = type.name.lowercase()
            if (apiKey != null) {
                settingsRepository.saveSetting("${prefix}_api_key", apiKey)
                updateProviderStatus(type) { it.copy(apiKey = apiKey, isConfigured = apiKey.isNotBlank()) }
            }
            if (hostUrl != null) {
                settingsRepository.saveSetting("${prefix}_host_url", hostUrl)
                updateProviderStatus(type) { it.copy(hostUrl = hostUrl, isConfigured = hostUrl.isNotBlank()) }
            }
            refreshModelsForProvider(type)
        }
    }

    private fun refreshModelsForProvider(type: AiProviderType) {
        viewModelScope.launch {
            val status = uiState.value.aiProviders[type] ?: return@launch
            setLoading(true, "Lade Modelle für ${status.label}...")
            
            val config = AiConfig(apiKey = status.apiKey, hostUrl = status.hostUrl)
            val service = when(type) {
                AiProviderType.GEMINI_CLOUD -> geminiCloudService
                AiProviderType.OLLAMA_LOCAL -> ollamaService
                AiProviderType.MISTRAL_CLOUD -> mistralService
                AiProviderType.CLAUDE_CLOUD -> claudeService
            }
            
            if (service == null) {
                // If service not implemented yet, just clear models or use dummy
                setLoading(false)
                return@launch
            }

            try {
                val rawModels = service.getAvailableModels(config).map { it.id }
                val category = when(type) {
                    AiProviderType.GEMINI_CLOUD -> de.pantastix.project.ai.ModelCategory.GEMINI_CLOUD
                    AiProviderType.OLLAMA_LOCAL -> de.pantastix.project.ai.ModelCategory.OLLAMA_LOCAL
                    AiProviderType.MISTRAL_CLOUD -> de.pantastix.project.ai.ModelCategory.MISTRAL_CLOUD
                    AiProviderType.CLAUDE_CLOUD -> de.pantastix.project.ai.ModelCategory.CLAUDE_CLOUD
                }

                val sortedModels = rawModels.mapNotNull { modelName ->
                    val family = de.pantastix.project.ai.AiModelRegistry.resolveFamily(modelName, category)
                    if (family != null && family.filter(modelName)) {
                        modelName to family
                    } else null
                }.sortedWith(Comparator { (modelA, famA), (modelB, famB) ->
                    if (famA.id == famB.id && famA.modelComparator != null) {
                        famA.modelComparator.compare(modelA, modelB)
                    } else {
                        modelA.compareTo(modelB)
                    }
                }).map { it.first }

                updateProviderStatus(type) { it.copy(availableModels = sortedModels) }
                
                if (status.selectedModel !in sortedModels && sortedModels.isNotEmpty()) {
                    selectModelForProvider(type, sortedModels.first())
                }
            } catch (e: Exception) {
                println("Error refreshing models for $type: ${e.message}")
            }
            setLoading(false)
        }
    }

    fun selectModelForProvider(type: AiProviderType, modelName: String) {
        viewModelScope.launch {
            settingsRepository.saveSetting("${type.name.lowercase()}_selected_model", modelName)
            updateProviderStatus(type) { it.copy(selectedModel = modelName) }
        }
    }

    fun selectUnifiedModel(modelName: String) {
        viewModelScope.launch {
            // Find which provider this model belongs to
            val providerType = uiState.value.aiProviders.values.find { it.availableModels.contains(modelName) }?.type
                ?: uiState.value.selectedAiProvider // Fallback
            
            setAiProvider(providerType)
            selectModelForProvider(providerType, modelName)
        }
    }

    fun onChatInputChanged(newInput: String) {
        _uiState.update { it.copy(chatInput = newInput) }
    }

    fun clearChat() {
        _uiState.update { it.copy(chatMessages = emptyList(), currentThought = null, error = null) }
    }

    fun sendMessage() {
        val input = uiState.value.chatInput
        if (input.isBlank() || uiState.value.isChatLoading) return

        viewModelScope.launch {
            val userMessage = Content(role = "user", parts = listOf(Part(text = input)))
            _uiState.update {
                it.copy(
                    chatMessages = it.chatMessages + userMessage,
                    chatInput = "",
                    isChatLoading = true
                )
            }
            processAILoop()
        }
    }

    private suspend fun processAILoop() {
        val providerType = uiState.value.selectedAiProvider
        val providerStatus = uiState.value.aiProviders[providerType] ?: return
        
        val service = when(providerType) {
            AiProviderType.GEMINI_CLOUD -> geminiCloudService
            AiProviderType.OLLAMA_LOCAL -> ollamaService
            AiProviderType.MISTRAL_CLOUD -> mistralService
            AiProviderType.CLAUDE_CLOUD -> claudeService
        }
        
        val systemPrompt = """
            Du bist Poké-Agent, ein hilfreicher Assistent für Pokémon-Karten-Sammler.
            Deine Aufgabe ist es, Fragen zur Sammlung des Nutzers zu beantworten, indem du die bereitgestellten Tools nutzt.
            
            WICHTIGE REGELN:
            1. Nutze IMMER 'search_sets' oder 'search_cards', bevor du Fakten behauptest. Rate nicht.
            2. Wenn du über eine spezifische Karte sprichst und im Tool-Ergebnis eine 'imageUrl' siehst, binde sie als Markdown-Bild ein: ![Kartenname](imageUrl).
            3. Wenn du nach dem Wert oder der Anzahl fragst, nutze 'get_inventory_stats' (für alles oder pro Set) oder 'search_cards' (für Einzelkarten).
            4. Formatiere Preise immer als Währung (z.B. "12,50 €").
        """.trimIndent()
        
        val config = AiConfig(
            apiKey = providerStatus.apiKey,
            hostUrl = providerStatus.hostUrl,
            selectedModelId = providerStatus.selectedModel,
            systemInstruction = systemPrompt
        )

        if (!providerStatus.isConfigured) {
            _uiState.update { it.copy(isChatLoading = false, error = "Provider ${providerStatus.label} ist nicht konfiguriert.") }
            return
        }

        val tools = registeredTools.toList()
        
        var currentHistory = uiState.value.chatMessages.map { content ->
            val role = when (content.role) {
                "user" -> ChatRole.USER
                "model" -> ChatRole.ASSISTANT
                "function" -> ChatRole.TOOL
                else -> ChatRole.SYSTEM
            }
            
            val textPart = content.parts.find { it.text != null }?.text ?: ""
            val toolCallPart = content.parts.find { it.functionCall != null }?.functionCall
            val toolResponsePart = content.parts.find { it.functionResponse != null }?.functionResponse
            
            // New logic: Separate thought (reasoning) and thoughtSignature (technical ID)
            val thoughtPart = content.parts.find { it.thought }?.text ?: content.thought
            val signaturePart = content.parts.find { it.thoughtSignature != null }?.thoughtSignature
            
            ChatMessage(
                role = role,
                content = textPart,
                thought = thoughtPart,
                thoughtSignature = signaturePart,
                toolCall = toolCallPart?.let { de.pantastix.project.ai.ToolCallData(it.name, it.args ?: emptyMap()) },
                toolResponse = toolResponsePart?.let { ToolResponseData(name = it.name, result = it.response.toString()) }
            )

        }

        var loopCount = 0
        val maxLoops = 10

        while (loopCount < maxLoops) {
             println("[AI LOOP] Starting iteration ${loopCount + 1}/$maxLoops")
             val lastMsg = currentHistory.lastOrNull()
             val promptToUse = if (loopCount == 0 && lastMsg?.role == ChatRole.USER) lastMsg.content else ""
             val historyToUse = if (loopCount == 0) currentHistory.dropLast(1) else currentHistory
             
             var receivedToolCall: de.pantastix.project.ai.AiResponse.ToolCall? = null
             var isFirstChunk = true
             var fullTextAccumulator = ""
             var fullThoughtAccumulator = ""
             var fullThoughtSignatureAccumulator = ""

             try {
                 service.streamResponse(promptToUse, historyToUse, config, tools).collect { response ->
                     when (response) {
                         is de.pantastix.project.ai.AiResponse.Text -> {
                             fullTextAccumulator += response.content
                             response.thought?.let { fullThoughtAccumulator += it }
                             response.thoughtSignature?.let { fullThoughtSignatureAccumulator = it } // Signature is usually one-off
                             
                             val newContent = Content(
                                 role = "model",
                                 parts = listOfNotNull(
                                     if (fullTextAccumulator.isNotBlank()) Part(text = fullTextAccumulator) else null,
                                     if (fullThoughtSignatureAccumulator.isNotBlank()) Part(thoughtSignature = fullThoughtSignatureAccumulator) else null
                                 ),
                                 thought = fullThoughtAccumulator.ifBlank { null }
                             )
                             
                             _uiState.update { state ->
                                 val newMessages = if (isFirstChunk) {
                                     state.chatMessages + newContent
                                 } else {
                                     state.chatMessages.dropLast(1) + newContent
                                 }
                                 
                                 state.copy(
                                     chatMessages = newMessages,
                                     currentThought = fullThoughtAccumulator.ifBlank { null },
                                     isChatLoading = true
                                 )
                             }
                             isFirstChunk = false
                         }
                         is de.pantastix.project.ai.AiResponse.ToolCall -> {
                             response.thought?.let { fullThoughtAccumulator += it }
                             response.thoughtSignature?.let { fullThoughtSignatureAccumulator = it }
                             _uiState.update { it.copy(currentThought = fullThoughtAccumulator.ifBlank { null }) }
                             receivedToolCall = response
                         }
                         is de.pantastix.project.ai.AiResponse.Error -> {
                              println("[AI ERROR CALLBACK] ${response.message}")
                              _uiState.update { it.copy(error = response.message) }
                         }
                     }
                 }
             } catch (e: Exception) {
                 println("[AI LOOP EXCEPTION] ${e.message}")
                 e.printStackTrace()
                 _uiState.update { it.copy(isChatLoading = false, error = e.message) }
                 break
             }
             
             if (receivedToolCall != null) {
                 val toolCallRes = receivedToolCall!!
                 println("[AI EXECUTING TOOL] ${toolCallRes.toolName} with args: ${toolCallRes.parameters}")
                 
                 // 1. Add tool call to history (CRITICAL: preserve thoughtSignature)
                 val toolCallMsg = ChatMessage(
                     role = ChatRole.ASSISTANT, 
                     content = fullTextAccumulator,
                     thoughtSignature = fullThoughtSignatureAccumulator.ifBlank { toolCallRes.thoughtSignature },
                     toolCall = de.pantastix.project.ai.ToolCallData(name = toolCallRes.toolName, args = toolCallRes.parameters)
                 )
                 currentHistory = currentHistory + toolCallMsg
                 
                 // 2. Add tool call to UI state (Replace last message if we already showed streaming thoughts)
                 val toolCallContent = Content(
                     role = "model",
                     parts = listOfNotNull(
                         if (fullTextAccumulator.isNotBlank()) Part(text = fullTextAccumulator) else null,
                         Part(
                            functionCall = FunctionCall(
                                name = toolCallRes.toolName,
                                args = mapToJsonObject(toolCallRes.parameters)
                            ),
                            thoughtSignature = fullThoughtSignatureAccumulator.ifBlank { toolCallRes.thoughtSignature }
                         )
                     ),
                     thought = fullThoughtAccumulator.ifBlank { null }
                 )
                 
                 _uiState.update { state ->
                     val newMessages = if (isFirstChunk) {
                         state.chatMessages + toolCallContent
                     } else {
                         state.chatMessages.dropLast(1) + toolCallContent
                     }
                     state.copy(chatMessages = newMessages)
                 }
                 
                 val tool = tools.find { it.name == toolCallRes.toolName }
                 if (tool != null) {
                     val toolResult = tool.execute(toolCallRes.parameters)
                     
                     // 3. Add tool response to history
                     currentHistory = currentHistory + ChatMessage(
                         role = ChatRole.TOOL, 
                         content = toolResult, 
                         thoughtSignature = toolCallMsg.thoughtSignature, // CRITICAL: Link response to same ID
                         toolResponse = ToolResponseData(name = toolCallRes.toolName, result = toolResult)
                     )
                     
                     // 4. Add tool response to UI state
                     val toolResponseContent = Content(
                         role = "function",
                         parts = listOf(Part(
                             functionResponse = FunctionResponse(
                                 name = toolCallRes.toolName,
                                 response = try { 
                                     Json.decodeFromString<JsonObject>(toolResult) 
                                 } catch(e: Exception) { 
                                     buildJsonObject { put("result", toolResult) } 
                                 }
                             ),
                             thoughtSignature = toolCallMsg.thoughtSignature
                         ))
                     )
                     _uiState.update { it.copy(chatMessages = it.chatMessages + toolResponseContent) }
                     
                     loopCount++
                 } else {
                     _uiState.update { it.copy(isChatLoading = false, error = "Tool not found: ${toolCallRes.toolName}") }
                     break
                 }
             } else {
                 if (fullTextAccumulator.isNotBlank()) {
                     println("[AI FINAL ANSWER] $fullTextAccumulator")
                 }
                 _uiState.update { it.copy(isChatLoading = false, currentThought = null) }
                 break 
             }
             
             if (loopCount >= maxLoops) {
                 println("[AI LOOP LIMIT] Max iterations ($maxLoops) reached. Stopping.")
                 _uiState.update { it.copy(isChatLoading = false, error = "Limit für automatische Korrekturen erreicht.") }
             }
        }
    }

    private fun mapToJsonObject(map: Map<String, Any?>): JsonObject {
        return buildJsonObject {
            map.forEach { (k, v) ->
                when (v) {
                    is String -> put(k, v)
                    is Number -> put(k, v)
                    is Boolean -> put(k, v)
                    is JsonElement -> put(k, v)
                    null -> put(k, JsonNull)
                    else -> put(k, v.toString())
                }
            }
        }
    }

    // --- Bulk Update ---

    private fun checkBulkUpdateEligibility() {
        viewModelScope.launch {
            val allCardInfos = activeCardRepository.getCardInfos().first()
            val hasUpdatableCards = allCardInfos.any { cardInfo ->
                !cardInfo.selectedPriceSource.isNullOrBlank() &&
                        cardInfo.selectedPriceSource != "CUSTOM" &&
                        !isUpdatedToday(cardInfo.lastPriceUpdate)
            }
            _uiState.update { it.copy(canBulkUpdatePrices = hasUpdatableCards) }
        }
    }

    fun startBulkPriceUpdate() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    bulkUpdateProgress = BulkUpdateProgress(inProgress = true, currentStepMessage = "Prüfe Sammlung...")
                )
            }

            val allCardInfos = activeCardRepository.getCardInfos().first()
            val infosToUpdate = allCardInfos.filter {
                !it.selectedPriceSource.isNullOrBlank() &&
                        it.selectedPriceSource != "CUSTOM" &&
                        !isUpdatedToday(it.lastPriceUpdate)
            }

            if (infosToUpdate.isEmpty()) {
                _uiState.update { it.copy(isLoading = false, bulkUpdateProgress = BulkUpdateProgress(inProgress = false)) }
                return@launch
            }

            _uiState.update {
                it.copy(
                    bulkUpdateProgress = it.bulkUpdateProgress.copy(
                        total = infosToUpdate.size,
                        currentStepMessage = "Lade Details für ${infosToUpdate.size} Karten..."
                    )
                )
            }

            val cardsToUpdate = infosToUpdate.mapNotNull {
                activeCardRepository.getFullCardDetails(it.id)
            }

            cardsToUpdate.forEachIndexed { index, card ->
                _uiState.update {
                    it.copy(
                        bulkUpdateProgress = it.bulkUpdateProgress.copy(currentStepMessage = "Aktualisiere: ${card.nameLocal}")
                    )
                }
                refreshSingleCardPrice(card)
                _uiState.update { it.copy(bulkUpdateProgress = it.bulkUpdateProgress.copy(processed = index + 1)) }
            }

            delay(500L)

            createDailySnapshot()
            _uiState.update { it.copy(isLoading = false, bulkUpdateProgress = BulkUpdateProgress(inProgress = false)) }
            checkBulkUpdateEligibility()
        }
    }

    private fun isUpdatedToday(timestamp: String?): Boolean {
        if (timestamp == null) return false
        return try {
            val updateDate = ZonedDateTime.parse(timestamp).toLocalDate()
            updateDate.isEqual(LocalDate.now())
        } catch (e: DateTimeParseException) {
            false
        }
    }

    private suspend fun refreshSingleCardPrice(card: PokemonCard) {
        val priceSource = card.selectedPriceSource ?: return
        val localId = card.localId.split(" / ").firstOrNull()?.trim() ?: return

        val apiDetails = apiService.getCardDetails(card.setId, localId, card.language)
        val newPrice = apiDetails?.let { extractPriceFromDetails(it, priceSource) }

        if (newPrice != null) {
            activeCardRepository.updateCardUserData(
                cardId = card.id!!,
                ownedCopies = card.ownedCopies,
                notes = card.notes,
                currentPrice = newPrice,
                lastPriceUpdate = Clock.System.now().toString(),
                selectedPriceSource = card.selectedPriceSource
            )
        }
        delay(200)
    }

    // --- Card Details & Editing ---

    fun fetchPriceDetailsForEditing(card: PokemonCard) {
        viewModelScope.launch {
            _uiState.update { it.copy(isEditingDetailsLoading = true, editingCardApiDetails = null) }
            val localId = card.localId.split(" / ").firstOrNull()?.trim()
            if (localId.isNullOrBlank()) {
                _uiState.update { it.copy(isEditingDetailsLoading = false, error = "Konnte Karten-ID nicht extrahieren.") }
                return@launch
            }
            val details = apiService.getCardDetails(card.setId, localId, card.language)
            _uiState.update { it.copy(isEditingDetailsLoading = false, editingCardApiDetails = details) }
        }
    }

    fun clearEditingDetails() {
        _uiState.update { it.copy(editingCardApiDetails = null, isEditingDetailsLoading = false) }
    }

    fun refreshCardPrice(card: PokemonCard) {
        viewModelScope.launch {
            val priceSource = card.selectedPriceSource
            if (priceSource.isNullOrBlank() || priceSource == "CUSTOM") return@launch

            _uiState.update { it.copy(isLoading = true) }

            val localId = card.localId.split(" / ").firstOrNull()?.trim()
            if (localId.isNullOrBlank()) {
                _uiState.update { it.copy(isLoading = false, error = "Konnte Karten-ID nicht extrahieren.") }
                return@launch
            }

            val apiDetails = apiService.getCardDetails(card.setId, localId, card.language)
            val newPrice = apiDetails?.let { extractPriceFromDetails(it, priceSource) }

            if (newPrice != null) {
                activeCardRepository.updateCardUserData(
                    cardId = card.id!!,
                    ownedCopies = card.ownedCopies,
                    notes = card.notes,
                    currentPrice = newPrice,
                    lastPriceUpdate = Clock.System.now().toString(),
                    selectedPriceSource = card.selectedPriceSource
                )
                selectCard(card.id)
            } else {
                _uiState.update { it.copy(error = "Preis konnte nicht aktualisiert werden.") }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun extractPriceFromDetails(details: TcgDexCardResponse, source: String): Double? {
        val pricing = details.pricing?.cardmarket ?: return null
        val isHolo = source.endsWith("-holo")
        val schemaName = source.removeSuffix("-holo").uppercase()
        val schema = PriceSchema.entries.find { it.name == schemaName } ?: return null

        return if (isHolo) {
            when (schema) {
                PriceSchema.TREND -> pricing.`trend-holo`
                PriceSchema.AVG1 -> pricing.`avg1-holo`
                PriceSchema.AVG7 -> pricing.`avg7-holo`
                PriceSchema.AVG30 -> pricing.`avg30-holo`
                PriceSchema.LOW -> pricing.`low-holo`
            }
        } else {
            when (schema) {
                PriceSchema.TREND -> pricing.trend
                PriceSchema.AVG1 -> pricing.avg1
                PriceSchema.AVG7 -> pricing.avg7
                PriceSchema.AVG30 -> pricing.avg30
                PriceSchema.LOW -> pricing.low
            }
        }
    }

    fun fetchCardDetailsFromApi(setId: String, localId: String, language: CardLanguage) {
        setLoading(true, "Suche Karte...")
        _uiState.update { it.copy(error = null, apiCardDetails = null, englishApiCardDetails = null) }
        val detailsJob = viewModelScope.async { apiService.getCardDetails(setId, localId, language.code) }
        fetchAndProcessCardDetails(detailsJob, language)
    }

    fun fetchCardDetailsByNameAndNumber(cardName: String, cardNumberInput: String, language: CardLanguage) {
        viewModelScope.launch {
            setLoading(true, "Suche Karte...")
            _uiState.update { it.copy(error = null, apiCardDetails = null, englishApiCardDetails = null) }

            val parts = cardNumberInput.split("/")
            if (parts.size != 2) {
                _uiState.update { it.copy(error = "Ungültiges Format. 'Nummer/Setgröße' verwenden.") }
                setLoading(false)
                return@launch
            }
            val localId = parts[0].trim()
            val officialCount = parts[1].trim().toIntOrNull()

            if (officialCount == null) {
                _uiState.update { it.copy(error = "Ungültige Setgröße.") }
                setLoading(false)
                return@launch
            }

            val potentialSets = activeCardRepository.getSetsByOfficialCount(officialCount)
            if (potentialSets.isEmpty()) {
                _uiState.update { it.copy(error = "Keine Sets gefunden.") }
                setLoading(false)
                return@launch
            }

            val bestMatchJob = async {
                val cardDetailJobs = potentialSets.map { set ->
                    async { apiService.getCardDetails(set.setId, localId, language.code) }
                }
                val foundCards = cardDetailJobs.awaitAll().filterNotNull()
                if (foundCards.isEmpty()) {
                    _uiState.update { it.copy(error = "Karte nicht gefunden.") }
                    return@async null
                }
                val normalizedInputName = cardName.replace(Regex("[\\s-]" ), "").lowercase()
                foundCards.find {
                    it.name.replace(Regex("[\\s-]" ), "").lowercase().contains(normalizedInputName)
                }
            }

            fetchAndProcessCardDetails(bestMatchJob, language)
        }
    }
    
    private fun fetchAndProcessCardDetails(detailsJob: Deferred<TcgDexCardResponse?>, language: CardLanguage) {
        viewModelScope.launch {
            val details = detailsJob.await()
            if (details == null) {
                _uiState.update { it.copy(error = "Karte nicht gefunden.", isLoading = false) }
                return@launch
            }
            if (language == CardLanguage.ENGLISH) {
                _uiState.update {
                    it.copy(apiCardDetails = details, englishApiCardDetails = details, searchedCardLanguage = language, isLoading = false)
                }
            } else {
                val englishDetailsJob = async {
                    apiService.getCardDetails(details.set.id, details.localId, CardLanguage.ENGLISH.code)
                }
                val englishDetails = englishDetailsJob.await()
                _uiState.update {
                    it.copy(apiCardDetails = details, englishApiCardDetails = englishDetails, searchedCardLanguage = language, isLoading = false)
                }
            }
        }
    }

    fun confirmAndSaveCard(
        cardDetails: TcgDexCardResponse, languageCode: String, abbreviation: String?, price: Double?,
        cardMarketLink: String, ownedCopies: Int, notes: String?, selectedPriceSource: String?,
        gradedCopies: List<de.pantastix.project.model.GradedCopy> = emptyList()
    ) {
        viewModelScope.launch {
            setLoading(true)
            val englishCardDetails = uiState.value.englishApiCardDetails

            if (englishCardDetails == null && languageCode != CardLanguage.ENGLISH.code) {
                _uiState.update { it.copy(error = "Konnte englische Kartendetails nicht abrufen.") }
                setLoading(false)
                return@launch
            }

            val existingCardInfo = activeCardRepository.findCardByTcgDexId(cardDetails.id, languageCode)
            if (existingCardInfo != null) {
                val fullExistingCard = activeCardRepository.getFullCardDetails(existingCardInfo.id)
                activeCardRepository.updateCardUserData(
                    cardId = existingCardInfo.id,
                    ownedCopies = existingCardInfo.ownedCopies + ownedCopies,
                    notes = notes ?: fullExistingCard?.notes,
                    currentPrice = price ?: existingCardInfo.currentPrice,
                    lastPriceUpdate = if (price != null) Clock.System.now().toString() else existingCardInfo.lastPriceUpdate,
                    selectedPriceSource = selectedPriceSource ?: existingCardInfo.selectedPriceSource,
                    gradedCopies = (fullExistingCard?.gradedCopies ?: emptyList()) + gradedCopies
                )
            } else {
                saveNewCard(
                    cardDetails, englishCardDetails ?: cardDetails, abbreviation, price, languageCode,
                    cardMarketLink, ownedCopies, notes, selectedPriceSource, gradedCopies
                )
            }
            createDailySnapshot()
            setLoading(false)
            resetApiCardDetails()
        }
    }

    fun selectCard(cardId: Long) {
        viewModelScope.launch {
            setLoading(true)
            val details = activeCardRepository.getFullCardDetails(cardId)
            _uiState.update { it.copy(selectedCardDetails = details) }
            setLoading(false)
        }
    }

    fun updateCard(cardId: Long, ownedCopies: Int, notes: String?, currentPrice: Double?, selectedPriceSource: String?, gradedCopies: List<de.pantastix.project.model.GradedCopy>) {
        viewModelScope.launch {
            setLoading(true)
            activeCardRepository.updateCardUserData(cardId, ownedCopies, notes, currentPrice, if (currentPrice != null) Clock.System.now().toString() else null, selectedPriceSource, gradedCopies)
            loadCardInfos()
            createDailySnapshot()
            selectCard(cardId)
            setLoading(false)
        }
    }
    
    fun deleteSelectedCard() {
        viewModelScope.launch {
            uiState.value.selectedCardDetails?.id?.let { cardId ->
                setLoading(true)
                activeCardRepository.deleteCardById(cardId)
                loadCardInfos()
                clearSelectedCard()
                setLoading(false)
            }
        }
    }

    private suspend fun saveNewCard(
        localCardDetails: TcgDexCardResponse, englishCardDetails: TcgDexCardResponse, abbreviation: String?,
        price: Double?, languageCode: String, marketLink: String, ownedCopies: Int, notes: String?, selectedPriceSource: String?,
        gradedCopies: List<de.pantastix.project.model.GradedCopy> = emptyList()
    ) {
        val completeImageUrl = localCardDetails.image?.let { "$it/high.jpg" }

        val newCard = PokemonCard(
            id = null,
            tcgDexCardId = localCardDetails.id,
            nameLocal = localCardDetails.name,
            nameEn = englishCardDetails.name,
            language = languageCode,
            imageUrl = completeImageUrl,
            cardMarketLink = marketLink,
            ownedCopies = ownedCopies,
            notes = notes,
            setName = localCardDetails.set.name,
            localId = "${localCardDetails.localId} / ${localCardDetails.set.cardCount?.official ?: '?'}",
            currentPrice = price,
            lastPriceUpdate = if (price != null) Clock.System.now().toString() else null,
            selectedPriceSource = selectedPriceSource,
            rarity = localCardDetails.rarity,
            hp = localCardDetails.hp,
            types = localCardDetails.types ?: emptyList(),
            illustrator = localCardDetails.illustrator,
            stage = localCardDetails.stage,
            retreatCost = localCardDetails.retreat,
            regulationMark = localCardDetails.regulationMark,
            abilities = localCardDetails.abilities?.map { Ability(it.name, it.type, it.effect) } ?: emptyList(),
            attacks = localCardDetails.attacks?.mapNotNull {
                it.name?.let { name -> Attack(it.cost, name, it.effect, it.damage) }
            } ?: emptyList(),
            setId = localCardDetails.set.id,
            variantsJson = localCardDetails.variants?.let { Json.encodeToString(it) },
            legalJson = localCardDetails.legal?.let { Json.encodeToString(it) },
            gradedCopies = gradedCopies
        )

        activeCardRepository.insertFullPokemonCard(newCard)
        if (!abbreviation.isNullOrBlank()) {
            activeCardRepository.updateSetAbbreviation(newCard.setId, abbreviation)
        }
        loadCardInfos()
    }

    // --- Settings & Supabase ---

    private suspend fun initializeSupabaseConnection() {
        val url = settingsRepository.getSetting("supabase_url") ?: ""
        val key = settingsRepository.getSetting("supabase_key") ?: ""
        _uiState.update { it.copy(supabaseUrl = url, supabaseKey = key) }
        if (url.isNotBlank() && key.isNotBlank()) connectToSupabase(url, key)
    }

    private suspend fun connectToSupabase(url: String, key: String) {
        try {
            val supabase = createSupabaseClient(url, key) { install(Postgrest) }
            supabase.postgrest.from("PokemonCardEntity").select { limit(1) }
            
            // Run migrations
            try {
                migrationManager.migrateToLatest(supabase.postgrest)
            } catch (e: Exception) {
                println("Cloud Migration Error during auto-connect: ${e.message}")
            }

            remoteCardRepository = SupabaseCardRepository(supabase.postgrest)
            typeService.setRepository(remoteCardRepository!!)
            typeService.refresh()
            
            _uiState.update { it.copy(isSupabaseConnected = true) }
            reinitializeTools()
        } catch (e: Exception) {
            remoteCardRepository = null
            typeService.setRepository(localCardRepository)
            _uiState.update { it.copy(isSupabaseConnected = false) }
        }
    }

    fun connectNewToSupabase(url: String, key: String) {
        viewModelScope.launch {
            setLoading(true, "Verbinde mit Supabase...")
            try {
                val supabase = createSupabaseClient(url, key) { install(Postgrest) }
                supabase.postgrest.from("PokemonCardEntity").select { limit(1) }
                
                // Connection successful, now run migrations before saving settings
                setLoading(true, "Synchronisiere Cloud-Datenbank...")
                try {
                    migrationManager.migrateToLatest(supabase.postgrest)
                } catch (e: Exception) {
                    println("Migration error on new connection: ${e.message}")
                }

                settingsRepository.saveSetting("supabase_url", url)
                settingsRepository.saveSetting("supabase_key", key)
                remoteCardRepository = SupabaseCardRepository(supabase.postgrest)
                typeService.setRepository(remoteCardRepository!!)
                typeService.refresh()

                _uiState.update { it.copy(isSupabaseConnected = true, supabaseKey = key, supabaseUrl = url) }
                reinitializeTools()
                loadSets().join()
                loadCardInfos()
                checkForSync()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Verbindung fehlgeschlagen: ${e.message}") }
            }
            setLoading(false)
        }
    }

    fun disconnectFromSupabase() {
        if (remoteCardRepository != null) {
            _uiState.update { it.copy(disconnectPromptMessage = "Cloud-Sammlung herunterladen? Sonst sind Daten nur in der Cloud.") }
        }
    }

    fun dismissDisconnectPrompt() { _uiState.update { it.copy(disconnectPromptMessage = null) } }

    fun confirmDisconnect(migrateData: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(disconnectPromptMessage = null) }
            if (migrateData && remoteCardRepository != null) {
                setLoading(true, "Migriere Daten...")
                val setsToMigrate = remoteCardRepository!!.fetchAllSetsOnce()
                val cardsToMigrate = remoteCardRepository!!.getCardInfos().first().mapNotNull { remoteCardRepository!!.getFullCardDetails(it.id) }
                localCardRepository.clearAllData()
                localCardRepository.syncSets(setsToMigrate)
                cardsToMigrate.forEach { localCardRepository.insertFullPokemonCard(it) }
            }
            setLoading(true, "Trenne Verbindung...")
            settingsRepository.saveSetting("supabase_url", "")
            settingsRepository.saveSetting("supabase_key", "")
            remoteCardRepository = null
            typeService.setRepository(localCardRepository)
            typeService.refresh()

            _uiState.update { it.copy(isSupabaseConnected = false, supabaseUrl = "", supabaseKey = "") }
            reinitializeTools()
            loadSets().join()
            loadCardInfos()
            setLoading(false)
        }
    }

    private suspend fun checkForSync() {
        val localCards = localCardRepository.getCardInfos().first()
        if (localCards.isNotEmpty() && remoteCardRepository != null) {
            val remoteCards = remoteCardRepository!!.getCardInfos().first()
            if (remoteCards.isEmpty()) {
                _uiState.update { it.copy(syncPromptMessage = "${localCards.size} lokale Karten hochladen?") }
            }
        }
    }

    fun syncLocalToSupabase() {
        viewModelScope.launch {
            setLoading(true)
            dismissSyncPrompt()
            val localCards = localCardRepository.getCardInfos().first()
            val remoteCards = remoteCardRepository?.getCardInfos()?.first() ?: emptyList()
            val remoteCardKeys = remoteCards.map { "${it.tcgDexCardId}-${it.language}" }.toSet()

            localCards.forEach { localCardInfo ->
                val fullLocalCard = localCardRepository.getFullCardDetails(localCardInfo.id)
                if (fullLocalCard != null) {
                    val key = "${fullLocalCard.tcgDexCardId}-${fullLocalCard.language}"
                    if (key !in remoteCardKeys) remoteCardRepository?.insertFullPokemonCard(fullLocalCard)
                }
            }
            loadCardInfos()
            setLoading(false)
        }
    }
    
    fun dismissSyncPrompt() = _uiState.update { it.copy(syncPromptMessage = null) }

    // --- Loading & Filters ---

    private suspend fun handleFirstLaunchSetLoading(): Boolean {
        for (attempt in 1..3) {
            _uiState.update { it.copy(loadingMessage = if (attempt > 1) "Lade Sets (Versuch $attempt/3)..." else "Lade Sets...") }
            val sets = apiService.getAllSets(uiState.value.appLanguage.code)
            if (sets.isNotEmpty()) {
                activeCardRepository.syncSets(sets)
                return true
            }
            if (attempt < 3) delay(5000)
        }
        _uiState.update { it.copy(error = "Konnte Sets nicht laden. Bitte Internet prüfen.") }
        return false
    }

    private suspend fun handleSubsequentLaunchSetLoading(): Boolean {
        _uiState.update { it.copy(loadingMessage = "Lade Set-Informationen") }
        val sets = apiService.getAllSets(uiState.value.appLanguage.code)
        if (sets.isNotEmpty()) {
            activeCardRepository.syncSets(sets)
            _uiState.update { it.copy(setsUpdateWarning = null) }
        } else {
            _uiState.update { it.copy(setsUpdateWarning = "Offline-Modus: Sets evtl. veraltet.") }
        }
        return true
    }

    fun startUpdate(downloadUrl: String) {
        val resourcesDir = System.getProperty("compose.application.resources.dir") ?: return
        val updaterJar = File(resourcesDir, "updater.jar")
        if (!updaterJar.exists()) {
            _uiState.update { it.copy(error = "Updater nicht gefunden.") }
            return
        }
        try {
            val fileName = downloadUrl.substringAfterLast('/')
            ProcessBuilder("java", "-jar", updaterJar.absolutePath, downloadUrl, fileName).start()
            exitProcess(0)
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Fehler beim Starten des Updaters.") }
        }
    }
    
    fun dismissUpdateDialog() { _uiState.update { it.copy(updateInfo = null) } }

    private suspend fun initializeLanguage() {
        var savedLangCode = settingsRepository.getSetting("language")
        if (savedLangCode == null) {
            val systemLangCode = getSystemLanguage()
            val defaultLanguage = AppLanguage.entries.find { it.code == systemLangCode } ?: AppLanguage.ENGLISH
            settingsRepository.saveSetting("language", defaultLanguage.code)
            savedLangCode = defaultLanguage.code
        }
        val language = AppLanguage.entries.find { it.code == savedLangCode } ?: AppLanguage.GERMAN
        setAppLanguage(language.code)
        _uiState.update { it.copy(appLanguage = language) }
    }

    fun setAppLanguage(language: AppLanguage) {
        viewModelScope.launch {
            setLoading(true, "Sprache ändern...")
            settingsRepository.saveSetting("language", language.code)
            setAppLanguage(language.code)
            _uiState.update { it.copy(appLanguage = language) }
            loadSets(language).join()
            setLoading(false)
        }
    }

    private fun loadCardInfos() {
        cardInfosCollectionJob?.cancel()
        cardInfosCollectionJob = activeCardRepository.getCardInfos()
            .onEach { cards -> _uiState.update { it.copy(cardInfos = cards) } }
            .launchIn(viewModelScope)
    }

    private fun loadSets(language: AppLanguage? = null): Job {
        setsCollectionJob?.cancel()
        setsCollectionJob = viewModelScope.launch {
            val currentLanguage = language ?: uiState.value.appLanguage
            val sets = apiService.getAllSets(currentLanguage.code)
            if (sets.isNotEmpty()) activeCardRepository.syncSets(sets)
            activeCardRepository.getAllSets().onEach { setsFromDb ->
                _uiState.update { it.copy(sets = setsFromDb.sortedByDescending { it.releaseDate }) }
            }.launchIn(viewModelScope)
        }
        return setsCollectionJob!!
    }

    private fun startBackgroundDataListeners() {
        collectCardInfos()
        listenForSetUpdates()
    }

    private fun listenForSetUpdates() {
        setsCollectionJob?.cancel()
        setsCollectionJob = activeCardRepository.getAllSets()
            .onEach { setsFromDb ->
                _uiState.update { it.copy(sets = setsFromDb.sortedBy { it.id }) }
            }
            .launchIn(viewModelScope)
    }

    fun addFilter(filter: FilterCondition) {
        if (_uiState.value.filters.size >= 3) return
        _uiState.update { it.copy(filters = it.filters + filter) }
        collectCardInfos()
    }

    fun removeFilter(filter: FilterCondition) {
        _uiState.update { it.copy(filters = it.filters - filter) }
        collectCardInfos()
    }

    fun updateSort(sort: Sort) {
        _uiState.update { it.copy(sort = sort) }
        collectCardInfos()
    }

    private fun collectCardInfos() {
        cardInfosCollectionJob?.cancel()
        cardInfosCollectionJob = viewModelScope.launch {
            activeCardRepository.getCardInfos()
                .map { list ->
                    var filteredList = list
                    _uiState.value.filters.forEach { filter ->
                        filteredList = filteredList.filter { cardInfo ->
                            when (filter) {
                                is FilterCondition.ByName -> cardInfo.nameLocal.contains(filter.nameQuery, ignoreCase = true)
                                is FilterCondition.ByLanguage -> cardInfo.language.equals(filter.languageCode, ignoreCase = true)
                                is FilterCondition.ByNumericValue -> {
                                    val cardValue = when (filter.attribute) {
                                        NumericAttribute.OWNED_COPIES -> cardInfo.ownedCopies.toDouble()
                                        NumericAttribute.CURRENT_PRICE -> cardInfo.currentPrice ?: 0.0
                                    }
                                    when (filter.comparison) {
                                        ComparisonType.EQUAL -> cardValue == filter.value
                                        ComparisonType.GREATER_THAN -> cardValue > filter.value
                                        ComparisonType.LESS_THAN -> cardValue < filter.value
                                    }
                                }
                            }
                        }
                    }
                    val sort = _uiState.value.sort
                    when (sort.sortBy) {
                        "nameLocal" -> if (sort.ascending) filteredList.sortedBy { it.nameLocal } else filteredList.sortedByDescending { it.nameLocal }
                        "setName" -> if (sort.ascending) filteredList.sortedBy { it.setName } else filteredList.sortedByDescending { it.setName }
                        "currentPrice" -> if (sort.ascending) filteredList.sortedBy { it.currentPrice } else filteredList.sortedByDescending { it.currentPrice }
                        "ownedCopies" -> if (sort.ascending) filteredList.sortedBy { it.ownedCopies } else filteredList.sortedByDescending { it.ownedCopies }
                        "language" -> if (sort.ascending) filteredList.sortedBy { it.language } else filteredList.sortedByDescending { it.language }
                        else -> filteredList
                    }
                }
                .collect { cardInfos -> _uiState.update { it.copy(cardInfos = cardInfos) } }
        }
    }

    private fun setLoading(isLoading: Boolean, message: String? = null) =
        _uiState.update { it.copy(isLoading = isLoading, loadingMessage = message) }
    
    fun clearError() = _uiState.update { it.copy(error = null) }
    fun clearSelectedCard() = _uiState.update { it.copy(selectedCardDetails = null) }
    fun resetApiCardDetails() {
        _uiState.update { it.copy(apiCardDetails = null, englishApiCardDetails = null, searchedCardLanguage = null) }
    }

    fun translateType(localTypeName: String): String {
        return typeService.translate(localTypeName, uiState.value.appLanguage.code)
    }

    fun loadPortfolioSnapshots() {
        viewModelScope.launch {
            _uiState.update { it.copy(isPortfolioLoading = true) }
            val snapshots = activeCardRepository.getAllSnapshots()
            _uiState.update { it.copy(portfolioSnapshots = snapshots, isPortfolioLoading = false) }
            
            // Auto-select latest snapshot if available
            if (snapshots.isNotEmpty() && uiState.value.selectedSnapshotDate == null) {
                selectSnapshot(snapshots.last().date)
            }
        }
    }

    fun selectSnapshot(date: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPortfolioLoading = true, selectedSnapshotDate = date) }
            val items = activeCardRepository.getSnapshotItems(date)
            _uiState.update { it.copy(selectedSnapshotItems = items, isPortfolioLoading = false) }
        }
    }

    // --- Set Overview ---

    fun loadSetProgressList() {
        viewModelScope.launch {
            setLoading(true, "Lade Set-Übersicht...")
            val progressList = activeCardRepository.getSetProgressList()
            _uiState.update { it.copy(setProgressList = progressList, isLoading = false) }
        }
    }

    fun loadCardsBySet(setId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, cardsBySet = emptyList()) }
            val cards = activeCardRepository.getCardsBySet(setId)
            _uiState.update { it.copy(cardsBySet = cards, isLoading = false) }
        }
    }

    private suspend fun createDailySnapshot() {
        try {
            val allCards = activeCardRepository.getCardInfos().first().mapNotNull { 
                activeCardRepository.getFullCardDetails(it.id)
            }
            
            if (allCards.isEmpty()) return

            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
            
            var totalRawValue = 0.0
            var totalGradedValue = 0.0
            var cardCount = 0

            val snapshotItems = allCards.map { card: PokemonCard ->
                val rawValue = (card.currentPrice ?: 0.0) * card.ownedCopies
                val gradedValue = card.gradedCopies.sumOf { it.value * it.count }
                
                totalRawValue += rawValue
                totalGradedValue += gradedValue
                cardCount += card.ownedCopies + card.gradedCopies.sumOf { it.count }

                PortfolioSnapshotItem(
                    date = today,
                    cardId = card.id!!,
                    nameLocal = card.nameLocal,
                    setName = card.setName,
                    imageUrl = card.imageUrl,
                    rawPrice = card.currentPrice,
                    rowCount = card.ownedCopies,
                    gradedCopiesJson = if (card.gradedCopies.isNotEmpty()) Json.encodeToString(card.gradedCopies) else null
                )
            }

            val snapshot = PortfolioSnapshot(
                date = today,
                totalValue = totalRawValue + totalGradedValue,
                totalRawValue = totalRawValue,
                totalGradedValue = totalGradedValue,
                cardCount = cardCount,
                updatedAt = Clock.System.now().toString()
            )

            activeCardRepository.savePortfolioSnapshot(snapshot, snapshotItems)
            println("Portfolio Snapshot created for $today. Total Value: ${snapshot.totalValue}€")
        } catch (e: Exception) {
            println("Error creating portfolio snapshot: ${e.message}")
        }
    }
}
