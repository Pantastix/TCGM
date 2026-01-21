package de.pantastix.project.ai.strategy.ollama

import de.pantastix.project.ai.*
import de.pantastix.project.ai.model.ollama.*
import de.pantastix.project.ai.strategy.AiWorkflowStrategy
import de.pantastix.project.ai.strategy.StrategyRequest
import de.pantastix.project.ai.tool.AgentTool
import kotlinx.serialization.json.*

class NativeOllamaStrategy : AiWorkflowStrategy {
    override val providerType = AiProviderType.OLLAMA_LOCAL
    // Matches "llama-3...", "mistral...", "gpt-oss..."
    override val modelIdRegex = Regex("""^(llama-3|mistral|gpt-oss).*""", RegexOption.IGNORE_CASE)

    override fun createUiModel(apiModelData: Any): AiModel {
        // Fallback or specific casting if possible
        val name = if (apiModelData is OllamaModel) apiModelData.name else apiModelData.toString()
        return AiModel(
            id = name,
            displayName = name,
            provider = AiProviderType.OLLAMA_LOCAL,
            capabilities = setOf(AiCapability.NATIVE_TOOL_CALLING, AiCapability.TEXT_GENERATION)
        )
    }

    override fun createRequest(
        prompt: String,
        chatHistory: List<ChatMessage>,
        config: AiConfig,
        availableTools: List<AgentTool>
    ): StrategyRequest {
        val modelId = config.selectedModelId ?: "llama-3"
        val messages = mutableListOf<OllamaChatMessage>()
        
        chatHistory.forEach {
            messages.add(OllamaChatMessage(it.role.name.lowercase(), it.content))
        }
        if (prompt.isNotBlank()) {
            messages.add(OllamaChatMessage("user", prompt))
        }

        val ollamaTools = availableTools.map { tool ->
            OllamaTool(
                function = OllamaFunction(
                    name = tool.name,
                    description = tool.description,
                    parameters = convertToSchema(tool.parameterSchemaJson)
                )
            )
        }

        val body = OllamaChatRequest(
            model = modelId,
            messages = messages,
            stream = false, // Streaming not fully implemented in this strategy yet
            tools = if (ollamaTools.isNotEmpty()) ollamaTools else null
        )
        
        return StrategyRequest(body)
    }

    override fun parseResponse(responseBody: String): AiResponse {
        try {
            val response = Json { ignoreUnknownKeys = true }.decodeFromString<OllamaChatResponse>(responseBody)
            val msg = response.message
            
            if (msg?.tool_calls != null && msg.tool_calls.isNotEmpty()) {
                val call = msg.tool_calls.first()
                val args = call.function.arguments.mapValues { (_, v) ->
                    if (v is JsonPrimitive) v.content else v.toString()
                }
                
                return AiResponse.ToolCall(
                    toolName = call.function.name,
                    parameters = args
                )
            }

            val content = msg?.content ?: ""
            // Parse potential <think> tags for reasoning models (e.g. DeepSeek/GPT-OSS)
            val thinkRegex = Regex("""<think>(.*?)</think>""", RegexOption.DOT_MATCHES_ALL)
            val match = thinkRegex.find(content)
            
            return if (match != null) {
                val thought = match.groupValues[1].trim()
                val cleanContent = content.replace(thinkRegex, "").trim()
                AiResponse.Text(cleanContent, thought)
            } else {
                AiResponse.Text(content)
            }
        } catch (e: Exception) {
            return AiResponse.Error("Ollama Parse Error: ${e.message}")
        }
    }

    override fun parseStreamChunk(chunk: String, buffer: StringBuilder): List<AiResponse> {
        // Not implemented for this pass
        return emptyList()
    }

    private fun convertToSchema(simpleJson: String): Map<String, JsonElement> {
        // Simplified schema conversion
         return try {
            val simpleObj = Json.decodeFromString<JsonObject>(simpleJson)
            val properties = buildJsonObject {
                simpleObj.entries.forEach { (key, value) ->
                    val desc = if (value is JsonPrimitive) value.content else value.toString()
                    put(key, buildJsonObject {
                        put("type", "string")
                        put("description", desc)
                    })
                }
            }
            
            buildJsonObject {
                put("type", "object")
                put("properties", properties)
                putJsonArray("required") {
                    simpleObj.keys.forEach { add(it) }
                }
            }
        } catch (e: Exception) {
            buildJsonObject {}
        }
    }
}
