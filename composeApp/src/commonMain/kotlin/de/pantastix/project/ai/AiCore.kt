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
    data class Text(val content: String) : AiResponse()
    data class ToolCall(val toolName: String, val parameters: Map<String, Any?>) : AiResponse()
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
}

data class AiConfig(
    val apiKey: String? = null, // For Gemini
    val hostUrl: String? = null, // For Ollama
    val selectedModelId: String? = null
)

data class ChatMessage(
    val role: ChatRole,
    val content: String
)

enum class ChatRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL // For feedback from simulated or native tools
}
