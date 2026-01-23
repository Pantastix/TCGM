package de.pantastix.project.ai

import de.pantastix.project.ai.tool.AgentTool

enum class AiProviderType {
    GEMINI_CLOUD,
    OLLAMA_LOCAL
}

data class AiModel(
    val id: String,
    val displayName: String,
    val provider: AiProviderType,
    val capabilities: Set<AiCapability> = emptySet()
)

enum class AiCapability {
    NATIVE_TOOL_CALLING,
    TEXT_GENERATION
}

sealed class AiResponse {
    data class Text(val content: String, val thought: String? = null, val thoughtSignature: String? = null) : AiResponse()
    data class ToolCall(val toolName: String, val parameters: Map<String, Any?>, val thought: String? = null, val thoughtSignature: String? = null) : AiResponse()
    data class Error(val message: String) : AiResponse()
}

interface AiService {
    val providerType: AiProviderType

    suspend fun getAvailableModels(config: AiConfig): List<AiModel>
    
    suspend fun generateResponse(
        prompt: String,
        chatHistory: List<ChatMessage>,
        config: AiConfig,
        availableTools: List<AgentTool>
    ): AiResponse

    suspend fun streamResponse(
        prompt: String,
        chatHistory: List<ChatMessage>,
        config: AiConfig,
        availableTools: List<AgentTool>
    ): kotlinx.coroutines.flow.Flow<AiResponse>
}

data class AiConfig(
    val apiKey: String? = null, // For Gemini
    val hostUrl: String? = null, // For Ollama
    val selectedModelId: String? = null,
    val systemInstruction: String? = null
)

data class ChatMessage(
    val role: ChatRole,
    val content: String,
    val thoughtSignature: String? = null,
    val toolCall: ToolCallData? = null,
    val toolResponse: ToolResponseData? = null
)

data class ToolCallData(
    val name: String,
    val args: Map<String, Any?>
)

data class ToolResponseData(
    val name: String,
    val result: String // JSON string result
)

enum class ChatRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL // For feedback from simulated or native tools
}
