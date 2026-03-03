package de.pantastix.project.ai.strategy.ollama

import de.pantastix.project.ai.*
import de.pantastix.project.ai.model.ollama.*
import de.pantastix.project.ai.strategy.AiWorkflowStrategy
import de.pantastix.project.ai.strategy.StrategyRequest
import de.pantastix.project.ai.tool.AgentTool
import kotlinx.serialization.json.*

/**
 * Strategy for standard Ollama models (Llama 3, Mistral, Gemma 3) that support native tools.
 */
class NativeOllamaStrategy : AiWorkflowStrategy {
    override val providerType = AiProviderType.OLLAMA_LOCAL
    
    // Matches "llama-3...", "mistral..."
    override val modelIdRegex = Regex("""^(llama-3|mistral|gemma-3).*""", RegexOption.IGNORE_CASE)

    override fun createUiModel(apiModelData: Any): AiModel {
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
        
        if (config.systemInstruction != null) {
            messages.add(OllamaChatMessage("system", config.systemInstruction))
        }

        chatHistory.forEach {
            val role = it.role.name.lowercase()
            val content = if (it.role == ChatRole.TOOL) {
                it.toolResponse?.result ?: it.content
            } else {
                it.content
            }

            val toolCalls = if (it.role == ChatRole.ASSISTANT && it.toolCall != null) {
                listOf(OllamaToolCall(OllamaToolCallFunction(
                    name = it.toolCall.name,
                    arguments = convertMapToJson(it.toolCall.args)
                )))
            } else null

            messages.add(OllamaChatMessage(
                role = role,
                content = content,
                tool_calls = toolCalls
            ))
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
            stream = true,
            tools = if (ollamaTools.isNotEmpty()) ollamaTools else null
        )
        
        return StrategyRequest(body)
    }

    override fun parseResponse(responseBody: String): AiResponse {
        val json = Json { ignoreUnknownKeys = true }
        return try {
            val response = json.decodeFromString<OllamaChatResponse>(responseBody)
            val msg = response.message ?: return AiResponse.Error("Empty message in response")
            
            if (msg.tool_calls != null && msg.tool_calls.isNotEmpty()) {
                val call = msg.tool_calls.first()
                val args = call.function.arguments.mapValues { it.value.toString().trim('"') }
                return AiResponse.ToolCall(call.function.name, args, null)
            }

            AiResponse.Text(msg.content, null)
        } catch (e: Exception) {
            AiResponse.Error("Ollama Parse Error: ${e.message}")
        }
    }

    override fun parseStreamChunk(chunk: String, buffer: StringBuilder): List<AiResponse> {
        val json = Json { ignoreUnknownKeys = true }
        val results = mutableListOf<AiResponse>()
        
        chunk.split("\n").filter { it.isNotBlank() }.forEach { line ->
            try {
                val response = json.decodeFromString<OllamaChatResponse>(line)
                val msg = response.message ?: return@forEach
                
                // IMPORTANT: In Ollama /api/chat with stream: true, content contains just the DELTA (new token)
                if (msg.content.isNotBlank()) {
                    results.add(AiResponse.Text(msg.content, null))
                }
                
                if (!msg.tool_calls.isNullOrEmpty()) {
                    val call = msg.tool_calls.first()
                    results.add(AiResponse.ToolCall(
                        toolName = call.function.name,
                        parameters = call.function.arguments.mapValues { it.value.toString().trim('"') },
                        thought = null
                    ))
                }
            } catch (e: Exception) {
                // ignore
            }
        }
        return results
    }

    private fun convertMapToJson(map: Map<String, Any?>): Map<String, JsonElement> {
        return map.mapValues { (_, value) ->
            when (value) {
                is String -> JsonPrimitive(value)
                is Number -> JsonPrimitive(value)
                is Boolean -> JsonPrimitive(value)
                null -> JsonNull
                else -> JsonPrimitive(value.toString())
            }
        }
    }

    private fun convertToSchema(simpleJson: String): Map<String, JsonElement> {
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
                putJsonArray("required") { simpleObj.keys.forEach { add(it) } }
            }
        } catch (e: Exception) { buildJsonObject {} }
    }
}
