package de.pantastix.project.ai.strategy

import de.pantastix.project.ai.*
import de.pantastix.project.ai.tool.AgentTool
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Defines how a specific AI model family handles requests and responses.
 * This encapsulates the differences between "Simulated Reasoners" (Gemma 3) 
 * and "Native Tool Users" (Gemini Flash/Pro).
 */
interface AiWorkflowStrategy {
    val providerType: AiProviderType
    val modelIdRegex: Regex

    /**
     * Creates a UI-ready AiModel object from the raw API model data.
     */
    fun createUiModel(apiModelData: Any): AiModel

    /**
     * Prepares the request body for the API call.
     * Returns a wrapper containing the request object and any specific configuration needed.
     */
    fun createRequest(
        prompt: String,
        chatHistory: List<ChatMessage>,
        config: AiConfig,
        availableTools: List<AgentTool>
    ): StrategyRequest

    /**
     * Parses a complete non-streaming response.
     */
    fun parseResponse(responseBody: String): AiResponse

    /**
     * Parses a single chunk from a streaming response.
     * @param chunk The raw text chunk (usually a JSON line from SSE).
     * @param buffer A stateful buffer to handle split tokens or incomplete JSON.
     * @return A list of parsed AiResponse events (Text, ToolCall, etc.) found in this chunk.
     */
    fun parseStreamChunk(chunk: String, buffer: StringBuilder): List<AiResponse>
}

data class StrategyRequest(
    val body: Any, // The serializable request object (e.g., GenerateContentRequest)
    val urlSuffix: String? = null, // e.g., ":streamGenerateContent"
    val apiVersion: String = "v1beta" // "v1beta", "v1alpha", etc.
)
