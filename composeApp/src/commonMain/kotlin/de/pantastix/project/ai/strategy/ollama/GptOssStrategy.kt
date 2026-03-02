package de.pantastix.project.ai.strategy.ollama

import de.pantastix.project.ai.*
import de.pantastix.project.ai.model.ollama.*
import de.pantastix.project.ai.strategy.AiWorkflowStrategy
import de.pantastix.project.ai.strategy.StrategyRequest
import de.pantastix.project.ai.tool.AgentTool
import kotlinx.serialization.json.*

/**
 * Strategy for GPT-OSS models via Ollama.
 * Supports:
 * - Native Reasoning (message.thinking)
 * - Native Tool Calling
 * - Live Streaming of thoughts and content
 */
class GptOssStrategy : AiWorkflowStrategy {
    override val providerType = AiProviderType.OLLAMA_LOCAL
    override val modelIdRegex = Regex("""^gpt-oss.*""", RegexOption.IGNORE_CASE)

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
        val modelId = config.selectedModelId ?: "gpt-oss:20b"
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
                thinking = it.thoughtSignature,
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

        val options = buildMap<String, JsonElement> {
            put("think", JsonPrimitive("high"))
            put("temperature", JsonPrimitive(0.1))
        }

        val body = OllamaChatRequest(
            model = modelId,
            messages = messages,
            stream = true, // ENABLE STREAMING FOR LIVE REASONING
            tools = if (ollamaTools.isNotEmpty()) ollamaTools else null,
            options = options
        )
        
        return StrategyRequest(body)
    }

    override fun parseResponse(responseBody: String): AiResponse {
        // Fallback for non-streaming calls or accumulated bodies
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        return try {
            val sanitizedBody = responseBody.replace("}{", "}\n{")
            val lines = sanitizedBody.trim().split("\n").filter { it.isNotBlank() }
            
            var accumulatedContent = ""
            var accumulatedThinking = ""
            var foundToolCall: OllamaToolCall? = null
            
            lines.forEach { line ->
                try {
                    val response = json.decodeFromString<OllamaChatResponse>(line)
                    val msg = response.message ?: return@forEach
                    msg.thinking?.let { accumulatedThinking += it }
                    if (msg.content.isNotBlank()) accumulatedContent += msg.content
                    if (!msg.tool_calls.isNullOrEmpty()) foundToolCall = msg.tool_calls.first()
                } catch (e: Exception) {}
            }
            
            if (foundToolCall != null) {
                return AiResponse.ToolCall(
                    toolName = foundToolCall!!.function.name,
                    parameters = foundToolCall!!.function.arguments.mapValues { it.value.toString().trim('"') },
                    thought = accumulatedThinking.ifBlank { null }
                )
            }
            AiResponse.Text(accumulatedContent, accumulatedThinking.ifBlank { null })
        } catch (e: Exception) {
            AiResponse.Error("GPT-OSS Parse Error: ${e.message}")
        }
    }

    override fun parseStreamChunk(chunk: String, buffer: StringBuilder): List<AiResponse> {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val results = mutableListOf<AiResponse>()
        
        // Ollama stream chunks are often single JSON objects, but can be concatenated
        val sanitized = chunk.replace("}{", "}\n{")
        sanitized.split("\n").filter { it.isNotBlank() }.forEach { line ->
            try {
                val response = json.decodeFromString<OllamaChatResponse>(line)
                val msg = response.message ?: return@forEach
                
                if (msg.thinking != null || msg.content.isNotBlank()) {
                    results.add(AiResponse.Text(msg.content, msg.thinking))
                }
                
                if (!msg.tool_calls.isNullOrEmpty()) {
                    val call = msg.tool_calls.first()
                    results.add(AiResponse.ToolCall(
                        toolName = call.function.name,
                        parameters = call.function.arguments.mapValues { it.value.toString().trim('"') },
                        thought = null // Thought is usually sent in separate chunks before
                    ))
                }
            } catch (e: Exception) {
                // Buffer partial JSONs if necessary (Ollama usually sends full lines, but let's be safe)
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
