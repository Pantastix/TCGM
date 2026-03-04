package de.pantastix.project.ai.strategy.claude

import de.pantastix.project.ai.*
import de.pantastix.project.ai.strategy.AiWorkflowStrategy
import de.pantastix.project.ai.strategy.StrategyRequest
import de.pantastix.project.ai.tool.AgentTool
import de.pantastix.project.model.claude.*
import kotlinx.serialization.json.*

class ClaudeNativeStrategy : AiWorkflowStrategy {
    override val providerType = AiProviderType.CLAUDE_CLOUD
    override val modelIdRegex = Regex(""".*""", RegexOption.IGNORE_CASE)

    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = false 
    }

    override fun createUiModel(apiModelData: Any): AiModel {
        val apiModel = apiModelData as ClaudeModelInfo
        return AiModel(
            id = apiModel.id,
            displayName = apiModel.displayName,
            provider = AiProviderType.CLAUDE_CLOUD,
            capabilities = setOf(AiCapability.TEXT_GENERATION, AiCapability.NATIVE_TOOL_CALLING)
        )
    }

    override fun createRequest(
        prompt: String,
        chatHistory: List<ChatMessage>,
        config: AiConfig,
        availableTools: List<AgentTool>
    ): StrategyRequest {
        val messages = mutableListOf<ClaudeMessage>()

        chatHistory.forEach { msg ->
            when (msg.role) {
                ChatRole.USER -> {
                    if (msg.content.isNotBlank()) {
                        messages.add(ClaudeMessage(
                            role = "user",
                            content = listOf(ClaudeContentBlock(type = "text", text = msg.content))
                        ))
                    }
                }
                ChatRole.ASSISTANT -> {
                    val blocks = mutableListOf<ClaudeContentBlock>()
                    
                    // Add reasoning if present
                    if (!msg.thought.isNullOrBlank()) {
                        blocks.add(ClaudeContentBlock(type = "thinking", thinking = msg.thought, signature = msg.thoughtSignature))
                    }
                    
                    // Add text if present
                    if (msg.content.isNotBlank()) {
                        blocks.add(ClaudeContentBlock(type = "text", text = msg.content))
                    }
                    
                    // Add tool calls if present
                    msg.toolCall?.let {
                        blocks.add(ClaudeContentBlock(
                            type = "tool_use",
                            id = msg.thoughtSignature ?: "toolu_${it.name}",
                            name = it.name,
                            input = mapToJsonObject(it.args)
                        ))
                    }
                    
                    if (blocks.isNotEmpty()) {
                        messages.add(ClaudeMessage(role = "assistant", content = blocks))
                    }
                }
                ChatRole.TOOL -> {
                    messages.add(ClaudeMessage(
                        role = "user",
                        content = listOf(ClaudeContentBlock(
                            type = "tool_result",
                            toolUseId = msg.thoughtSignature ?: "",
                            content = listOf(ClaudeContentBlock(type = "text", text = msg.content))
                        ))
                    ))
                }
                else -> {}
            }
        }

        if (prompt.isNotBlank()) {
            messages.add(ClaudeMessage(
                role = "user",
                content = listOf(ClaudeContentBlock(type = "text", text = prompt))
            ))
        }

        val claudeTools = if (availableTools.isNotEmpty()) {
            availableTools.mapNotNull { tool ->
                tool.schema?.let { 
                    ClaudeTool(
                        name = tool.name,
                        description = tool.description,
                        input_schema = fixSchemaTypes(it)
                    )
                }
            }
        } else null

        val modelId = config.selectedModelId ?: "claude-3-5-sonnet-latest"
        val isClaude37 = modelId.contains("3-7")
        
        // Extended Thinking config for 3.7
        val thinkingConfig = if (isClaude37) ClaudeThinkingConfig(budgetTokens = 1024) else null
        // max_tokens must be > budget_tokens
        val maxTokens = if (isClaude37) 16384 else 4096

        return StrategyRequest(
            body = ClaudeMessageRequest(
                model = modelId,
                messages = messages,
                system = config.systemInstruction,
                tools = claudeTools,
                stream = true,
                thinking = thinkingConfig,
                max_tokens = maxTokens
            ),
            urlSuffix = "/v1/messages"
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
            val response = json.decodeFromString<ClaudeMessageResponse>(responseBody)
            val textBlocks = response.content.filter { it.type == "text" }
            val toolBlocks = response.content.filter { it.type == "tool_use" }
            val thoughtBlocks = response.content.filter { it.type == "thinking" }

            val thought = thoughtBlocks.firstOrNull()?.thinking
            val thoughtSignature = thoughtBlocks.firstOrNull()?.signature

            if (toolBlocks.isNotEmpty()) {
                val tool = toolBlocks.first()
                val args = tool.input?.mapValues {
                    if (it.value is JsonPrimitive) it.value.jsonPrimitive.content else it.value.toString()
                } ?: emptyMap()
                return AiResponse.ToolCall(tool.name ?: "", args, thought, thoughtSignature = tool.id)
            }

            AiResponse.Text(textBlocks.joinToString("\n") { it.text ?: "" }, thought, thoughtSignature)
        } catch (e: Exception) {
            AiResponse.Error("Claude Parse Error: ${e.message}")
        }
    }

    // Streaming state
    private var currentToolId: String? = null
    private var currentToolName: String? = null

    override fun parseStreamChunk(chunk: String, buffer: StringBuilder): List<AiResponse> {
        val line = chunk.trim()
        if (!line.startsWith("data: ")) return emptyList()
        val data = line.substring(6).trim()
        
        return try {
            val event = json.decodeFromString<ClaudeStreamEvent>(data)
            when (event.type) {
                "content_block_start" -> {
                    if (event.contentBlock?.type == "tool_use") {
                        currentToolId = event.contentBlock.id
                        currentToolName = event.contentBlock.name
                        buffer.setLength(0)
                    }
                    emptyList()
                }
                "content_block_delta" -> {
                    val delta = event.delta ?: return emptyList()
                    when (delta.type) {
                        "text_delta" -> listOf(AiResponse.Text(delta.text ?: ""))
                        "input_json_delta" -> {
                            buffer.append(delta.partial_json)
                            emptyList()
                        }
                        "thinking_delta" -> listOf(AiResponse.Text("", thought = delta.thinking))
                        "signature_delta" -> listOf(AiResponse.Text("", thoughtSignature = delta.signature))
                        else -> emptyList()
                    }
                }
                "content_block_stop" -> {
                    if (currentToolId != null) {
                        val fullArgs = try {
                            json.decodeFromString<Map<String, JsonElement>>(buffer.toString()).mapValues {
                                if (it.value is JsonPrimitive) it.value.jsonPrimitive.content else it.value.toString()
                            }
                        } catch(e: Exception) { emptyMap() }
                        
                        val response = AiResponse.ToolCall(currentToolName ?: "", fullArgs, thoughtSignature = currentToolId)
                        currentToolId = null
                        currentToolName = null
                        listOf(response)
                    } else {
                        emptyList()
                    }
                }
                else -> emptyList()
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
