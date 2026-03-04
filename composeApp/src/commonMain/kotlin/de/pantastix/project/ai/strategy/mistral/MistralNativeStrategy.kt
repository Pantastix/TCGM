package de.pantastix.project.ai.strategy.mistral

import de.pantastix.project.ai.*
import de.pantastix.project.ai.strategy.AiWorkflowStrategy
import de.pantastix.project.ai.strategy.StrategyRequest
import de.pantastix.project.ai.tool.AgentTool
import de.pantastix.project.model.mistral.*
import kotlinx.serialization.json.*

class MistralNativeStrategy : AiWorkflowStrategy {
    override val providerType = AiProviderType.MISTRAL_CLOUD
    override val modelIdRegex = Regex(""".*""", RegexOption.IGNORE_CASE) 

    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = false 
    }

    // Parser State for Streaming
    private var parserState = ParserState.CONTENT
    private enum class ParserState { CONTENT, THINKING }

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

        // 1. Enhanced System Prompt for Reasoning
        val baseSystem = config.systemInstruction ?: "Du bist ein hilfreicher Assistent."
        val reasoningInstruction = "\n\nPROTOCOL: Starte deine Antwort IMMER mit einem <think>...</think> Block, in dem du dein Vorgehen planst."
        messages.add(MistralMessage(role = "system", content = baseSystem + reasoningInstruction))

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
                            id = msg.thoughtSignature ?: "", // Use real ID or empty (which will fail validation correctly)
                            function = MistralFunctionCall(it.name, json.encodeToString(mapToJsonObject(it.args)))
                        ))
                    }
                    
                    val fullContent = buildString {
                        if (!msg.thought.isNullOrBlank()) {
                            append("<think>${msg.thought}</think>\n")
                        }
                        append(msg.content)
                    }
                    
                    val content = fullContent.ifBlank { if (toolCalls == null) " " else null }
                    messages.add(MistralMessage(role = "assistant", content = content, toolCalls = toolCalls))
                }
                ChatRole.TOOL -> {
                    messages.add(MistralMessage(
                        role = "tool", 
                        content = msg.content, 
                        toolCallId = msg.thoughtSignature ?: "",
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

        // 4. Tools
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
            
            val fullText = msg.content ?: ""
            val thinkRegex = Regex("""<think>(.*?)</think>""", RegexOption.DOT_MATCHES_ALL)
            val thinkMatch = thinkRegex.find(fullText)
            val thought = thinkMatch?.groupValues?.get(1)?.trim()
            val content = fullText.replace(thinkRegex, "").trim()
            
            AiResponse.Text(content, thought)
        } catch (e: Exception) {
            AiResponse.Error("Parse Error: ${e.message}")
        }
    }

    override fun parseStreamChunk(chunk: String, buffer: StringBuilder): List<AiResponse> {
        val line = chunk.trim()
        if (!line.startsWith("data: ")) return emptyList()
        val data = line.substring(6).trim()
        if (data == "[DONE]") return emptyList()

        val events = mutableListOf<AiResponse>()

        try {
            val response = json.decodeFromString<MistralChunkResponse>(data)
            val choice = response.choices.firstOrNull() ?: return emptyList()
            val delta = choice.delta
            
            if (delta.toolCalls != null) {
                val call = delta.toolCalls.first()
                if (!call.id.isNullOrBlank()) {
                    buffer.setLength(0)
                    buffer.append(call.id).append("|")
                }
                buffer.append(call.function.arguments)
                
                if (choice.finishReason == "tool_calls") {
                    val content = buffer.toString()
                    val parts = content.split("|", limit = 2)
                    val toolId = parts.getOrNull(0) ?: "call_unknown"
                    val allArgs = parts.getOrNull(1) ?: ""
                    
                    val fullArgs = try {
                        json.decodeFromString<Map<String, JsonElement>>(allArgs).mapValues {
                            if (it.value is JsonPrimitive) it.value.jsonPrimitive.content else it.value.toString()
                        }
                    } catch(e: Exception) { emptyMap() }
                    
                    events.add(AiResponse.ToolCall(call.function.name, fullArgs, thoughtSignature = toolId))
                    buffer.setLength(0)
                }
            } else if (delta.content != null) {
                val newText = delta.content
                buffer.append(newText)
                
                var loop = true
                while (loop) {
                    loop = false
                    val currentBuffer = buffer.toString()
                    
                    when (parserState) {
                        ParserState.CONTENT -> {
                            val thinkIndex = currentBuffer.indexOf("<think>")
                            if (thinkIndex != -1) {
                                if (thinkIndex > 0) {
                                    events.add(AiResponse.Text(currentBuffer.substring(0, thinkIndex)))
                                }
                                parserState = ParserState.THINKING
                                buffer.delete(0, thinkIndex + 7)
                                loop = true
                            } else {
                                // Delta logic
                                val lastOpen = currentBuffer.lastIndexOf('<')
                                if (lastOpen != -1 && lastOpen > currentBuffer.length - 7) {
                                    if (lastOpen > 0) {
                                        events.add(AiResponse.Text(currentBuffer.substring(0, lastOpen)))
                                        buffer.delete(0, lastOpen)
                                    }
                                } else {
                                    if (buffer.isNotEmpty()) {
                                        events.add(AiResponse.Text(buffer.toString()))
                                        buffer.setLength(0)
                                    }
                                }
                            }
                        }
                        ParserState.THINKING -> {
                            val closeIndex = currentBuffer.indexOf("</think>")
                            if (closeIndex != -1) {
                                events.add(AiResponse.Text("", currentBuffer.substring(0, closeIndex)))
                                parserState = ParserState.CONTENT
                                buffer.delete(0, closeIndex + 8)
                                loop = true
                            } else {
                                // Delta logic
                                val lastOpen = currentBuffer.lastIndexOf('<')
                                if (lastOpen != -1 && lastOpen > currentBuffer.length - 8) {
                                    if (lastOpen > 0) {
                                        events.add(AiResponse.Text("", currentBuffer.substring(0, lastOpen)))
                                        buffer.delete(0, lastOpen)
                                    }
                                } else {
                                    if (buffer.isNotEmpty()) {
                                        events.add(AiResponse.Text("", buffer.toString()))
                                        buffer.setLength(0)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {}
        
        return events
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
