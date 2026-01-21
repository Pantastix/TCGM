package de.pantastix.project.ai.strategy.ollama

import de.pantastix.project.ai.*
import de.pantastix.project.ai.model.ollama.*
import de.pantastix.project.ai.strategy.AiWorkflowStrategy
import de.pantastix.project.ai.strategy.StrategyRequest
import de.pantastix.project.ai.tool.AgentTool
import kotlinx.serialization.json.*

class SimulatedJsonStrategy : AiWorkflowStrategy {
    override val providerType = AiProviderType.OLLAMA_LOCAL
    // Matches anything not matched by others (fallback)
    override val modelIdRegex = Regex(".*") 

    override fun createUiModel(apiModelData: Any): AiModel {
        val name = if (apiModelData is OllamaModel) apiModelData.name else apiModelData.toString()
        return AiModel(
            id = name,
            displayName = "$name (Simulated)",
            provider = AiProviderType.OLLAMA_LOCAL,
            capabilities = setOf(AiCapability.TEXT_GENERATION)
        )
    }

    override fun createRequest(
        prompt: String,
        chatHistory: List<ChatMessage>,
        config: AiConfig,
        availableTools: List<AgentTool>
    ): StrategyRequest {
        val modelId = config.selectedModelId ?: "unknown"
        val messages = mutableListOf<OllamaChatMessage>()
        chatHistory.forEach { messages.add(OllamaChatMessage(it.role.name.lowercase(), it.content)) }
        if (prompt.isNotBlank()) messages.add(OllamaChatMessage("user", prompt))
        
        // 1. Inject Tool Definitions into System Prompt (Logic from old class)
        // ... (Simplified for brevity, assuming similar logic as before but adapted)
        val systemMessageIndex = messages.indexOfFirst { it.role == "system" }
        val baseSystemPrompt = if (systemMessageIndex != -1) messages[systemMessageIndex].content else "You are a helpful assistant."
        val enhancedSystemPrompt = buildSystemPrompt(baseSystemPrompt, availableTools)
        
        if (systemMessageIndex != -1) {
            messages[systemMessageIndex] = OllamaChatMessage("system", enhancedSystemPrompt)
        } else {
            messages.add(0, OllamaChatMessage("system", enhancedSystemPrompt))
        }

        val body = OllamaChatRequest(
            model = modelId,
            messages = messages,
            stream = false
        )
        return StrategyRequest(body)
    }

    private fun buildSystemPrompt(baseSystemPrompt: String, tools: List<AgentTool>): String {
        if (tools.isEmpty()) return baseSystemPrompt
        val toolsDescription = tools.joinToString("\n") { "- ${it.name}: ${it.description}" }
        return """
            $baseSystemPrompt
            TOOLS:
            $toolsDescription
            Respond with JSON block ```json { "tool": "name", "parameters": {} } ``` if needed.
        """.trimIndent()
    }

    override fun parseResponse(responseBody: String): AiResponse {
        try {
            val response = Json { ignoreUnknownKeys = true }.decodeFromString<OllamaChatResponse>(responseBody)
            val content = response.message?.content ?: return AiResponse.Text("")
            val toolCall = parseToolCall(content)
            return if (toolCall != null) {
                AiResponse.ToolCall(toolCall.first, toolCall.second)
            } else {
                AiResponse.Text(content)
            }
        } catch (e: Exception) {
            return AiResponse.Error("Parse Error")
        }
    }
    
    override fun parseStreamChunk(chunk: String, buffer: StringBuilder): List<AiResponse> = emptyList()

    private fun parseToolCall(content: String): Pair<String, Map<String, Any?>>? {
        val regex = """```json\s*(\{.*\})\s*```""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val match = regex.find(content) ?: return null
        return try {
            val json = Json.decodeFromString<JsonObject>(match.groupValues[1])
            val toolName = json["tool"]?.jsonPrimitive?.content ?: return null
            toolName to emptyMap() // Simplified parsing
        } catch (e: Exception) { null }
    }
}
