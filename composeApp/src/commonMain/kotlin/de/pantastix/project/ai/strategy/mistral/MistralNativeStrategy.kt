package de.pantastix.project.ai.strategy.mistral

import de.pantastix.project.ai.*
import de.pantastix.project.ai.strategy.AiWorkflowStrategy
import de.pantastix.project.ai.strategy.StrategyRequest
import de.pantastix.project.ai.tool.AgentTool
import de.pantastix.project.model.mistral.*
import kotlinx.serialization.json.*

class MistralNativeStrategy : AiWorkflowStrategy {
    override val providerType = AiProviderType.MISTRAL_CLOUD
    override val modelIdRegex = Regex(""".*""", RegexOption.IGNORE_CASE) // General strategy for all Mistral models

    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = false 
    }

    override fun createUiModel(apiModelData: Any): de.pantastix.project.ai.AiModel {
        val apiModel = apiModelData as MistralModelCard
        val hasToolCalling = apiModel.capabilities?.functionCalling ?: false
        
        return de.pantastix.project.ai.AiModel(
            id = apiModel.id,
            displayName = apiModel.name ?: apiModel.id,
            provider = AiProviderType.MISTRAL_CLOUD,
            capabilities = buildSetOf(hasToolCalling)
        )
    }

    private fun buildSetOf(hasToolCalling: Boolean): Set<AiCapability> {
        val caps = mutableSetOf(AiCapability.TEXT_GENERATION)
        if (hasToolCalling) caps.add(AiCapability.NATIVE_TOOL_CALLING)
        return caps
    }

    override fun createRequest(
        prompt: String,
        chatHistory: List<ChatMessage>,
        config: AiConfig,
        availableTools: List<AgentTool>
    ): StrategyRequest {
        val messages = mutableListOf<MistralMessage>()

        // 1. System Prompt
        if (!config.systemInstruction.isNullOrBlank()) {
            messages.add(MistralMessage(role = "system", content = config.systemInstruction))
        }

        // 2. History
        chatHistory.forEach { msg ->
            when (msg.role) {
                ChatRole.USER -> {
                    if (msg.content.isNotBlank()) {
                        messages.add(MistralMessage(role = "user", content = msg.content))
                    }
                }
                ChatRole.ASSISTANT -> {
                    val toolCalls = msg.toolCall?.let {
                        listOf(MistralToolCall(
                            id = msg.thoughtSignature ?: "call_${it.name}",
                            function = MistralFunctionCall(it.name, json.encodeToString(mapToJsonObject(it.args)))
                        ))
                    }
                    // CRITICAL: Assistant message MUST have content OR tool_calls.
                    // If both are empty, Mistral returns 400.
                    val content = msg.content.ifBlank { if (toolCalls == null) " " else null }
                    messages.add(MistralMessage(role = "assistant", content = content, toolCalls = toolCalls))
                }
                ChatRole.TOOL -> {
                    messages.add(MistralMessage(
                        role = "tool", 
                        content = msg.content, 
                        toolCallId = msg.thoughtSignature ?: "call_${msg.toolResponse?.name}",
                        name = msg.toolResponse?.name
                    ))
                }
                else -> {}
            }
        }

        // 3. Current Prompt
        if (prompt.isNotBlank()) {
            messages.add(MistralMessage(role = "user", content = prompt))
        }

        // 4. Tools (with Lowercase Types)
        val mistralTools = if (availableTools.isNotEmpty()) {
            availableTools.mapNotNull { tool ->
                tool.schema?.let { 
                    MistralTool(
                        function = MistralFunction(
                            name = tool.name, 
                            description = tool.description, 
                            parameters = fixSchemaTypes(it)
                        )
                    ) 
                }
            }
        } else null

        return StrategyRequest(
            body = MistralChatRequest(
                model = config.selectedModelId ?: "mistral-small-latest",
                messages = messages,
                tools = mistralTools,
                stream = true
            ),
            urlSuffix = "/v1/chat/completions"
        )
    }

    private fun fixSchemaTypes(schema: de.pantastix.project.model.gemini.Schema): JsonObject {
        return buildJsonObject {
            put("type", schema.type.lowercase())
            schema.description?.let { put("description", it) }
            schema.properties?.let { props ->
                put("properties", buildJsonObject {
                    props.forEach { (name, propSchema) ->
                        put(name, fixSchemaTypes(propSchema))
                    }
                })
            }
            schema.required?.let { req ->
                put("required", buildJsonArray { req.forEach { add(it) } })
            }
            schema.enum?.let { enums ->
                put("enum", buildJsonArray { enums.forEach { add(it) } })
            }
        }
    }

    override fun parseResponse(responseBody: String): AiResponse {
        return try {
            val response = json.decodeFromString<MistralChatResponse>(responseBody)
            val choice = response.choices.firstOrNull() ?: return AiResponse.Error("No choices in response")
            val msg = choice.message
            
            if (msg.toolCalls != null) {
                val call = msg.toolCalls.first()
                val args = try {
                    json.decodeFromString<Map<String, JsonElement>>(call.function.arguments).mapValues {
                        if (it.value is JsonPrimitive) it.value.jsonPrimitive.content else it.value.toString()
                    }
                } catch(e: Exception) { emptyMap() }
                
                return AiResponse.ToolCall(call.function.name, args, thoughtSignature = call.id)
            }
            
            AiResponse.Text(msg.content ?: "")
        } catch (e: Exception) {
            AiResponse.Error("Parse Error: ${e.message}")
        }
    }

    override fun parseStreamChunk(chunk: String, buffer: StringBuilder): List<AiResponse> {
        val line = chunk.trim()
        if (!line.startsWith("data: ")) return emptyList()
        val data = line.substring(6).trim()
        if (data == "[DONE]") return emptyList()

        return try {
            val response = json.decodeFromString<MistralChunkResponse>(data)
            val choice = response.choices.firstOrNull() ?: return emptyList()
            val delta = choice.delta
            
            if (delta.toolCalls != null) {
                val call = delta.toolCalls.first()
                
                // 1. Capture ID if present (usually only in the first chunk)
                if (!call.id.isNullOrBlank()) {
                    // Store ID at the beginning of the buffer using a delimiter
                    // Format: "ID|ARGUMENTS"
                    buffer.setLength(0)
                    buffer.append(call.id).append("|")
                }
                
                // 2. Accumulate arguments
                buffer.append(call.function.arguments)
                
                // 3. Check for finish
                if (choice.finishReason == "tool_calls") {
                    val content = buffer.toString()
                    val parts = content.split("|", limit = 2)
                    val toolId = parts.getOrNull(0) ?: "call_unknown"
                    val allArgs = parts.getOrNull(1) ?: ""
                    
                    val fullArgs = try {
                        json.decodeFromString<Map<String, JsonElement>>(allArgs).mapValues {
                            if (it.value is JsonPrimitive) it.value.jsonPrimitive.content else it.value.toString()
                        }
                    } catch(e: Exception) { 
                        println("[MISTRAL PARSE ERROR] Args: $allArgs")
                        emptyMap() 
                    }
                    
                    return listOf(AiResponse.ToolCall(call.function.name, fullArgs, thoughtSignature = toolId))
                }
                emptyList()
            } else if (delta.content != null) {
                listOf(AiResponse.Text(delta.content))
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
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
}
