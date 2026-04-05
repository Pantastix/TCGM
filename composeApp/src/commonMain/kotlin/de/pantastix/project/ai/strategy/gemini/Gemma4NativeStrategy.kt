package de.pantastix.project.ai.strategy.gemini

import de.pantastix.project.ai.*
import de.pantastix.project.ai.strategy.AiWorkflowStrategy
import de.pantastix.project.ai.strategy.StrategyRequest
import de.pantastix.project.ai.tool.AgentTool
import de.pantastix.project.model.gemini.*
import de.pantastix.project.model.gemini.AiModel as ApiModel
import kotlinx.serialization.json.*

/**
 * Strategy for Gemma 4 Cloud models.
 * Supports:
 * - Native Function Calling (Gemini API compatible)
 * - Native Thinking Field ("thought": true in JSON parts)
 * - Thinking Activation: <|think|> in system prompt
 */
class Gemma4NativeStrategy : AiWorkflowStrategy {
    override val providerType = AiProviderType.GEMINI_CLOUD
    override val modelIdRegex = Regex("""^(models/)?gemma-4.*""", RegexOption.IGNORE_CASE)

    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = false
    }

    override fun createUiModel(apiModelData: Any): de.pantastix.project.ai.AiModel {
        val apiModel = apiModelData as ApiModel
        val name = apiModel.name
        val sizeRegex = Regex("""(\d+b)""", RegexOption.IGNORE_CASE)
        val match = sizeRegex.find(name)
        val size = match?.value?.lowercase() ?: "Unknown"

        return de.pantastix.project.ai.AiModel(
            id = name,
            displayName = "Gemma 4 ($size)",
            provider = AiProviderType.GEMINI_CLOUD,
            capabilities = setOf(AiCapability.NATIVE_TOOL_CALLING, AiCapability.TEXT_GENERATION)
        )
    }

    override fun createRequest(
        prompt: String,
        chatHistory: List<ChatMessage>,
        config: AiConfig,
        availableTools: List<AgentTool>
    ): StrategyRequest {
        val contents = mutableListOf<Content>()
        
        contents.addAll(chatHistory.map { msg ->
            val role = when (msg.role) {
                ChatRole.USER -> "user"
                ChatRole.TOOL -> "function"
                else -> "model"
            }

            val parts = mutableListOf<Part>()

            // Add Thought if present in history
            if (!msg.thought.isNullOrBlank()) {
                parts.add(Part(text = msg.thought, thought = true))
            }

            if (msg.toolCall != null) {
                parts.add(Part(
                    functionCall = FunctionCall(
                        name = msg.toolCall.name, 
                        args = mapToJsonObject(msg.toolCall.args)
                    ),
                    thoughtSignature = msg.thoughtSignature
                ))
            } else if (msg.toolResponse != null) {
                val jsonResponse = try {
                    json.decodeFromString<JsonObject>(msg.toolResponse.result)
                } catch (e: Exception) {
                    buildJsonObject { put("result", msg.toolResponse.result) }
                }
                parts.add(Part(functionResponse = FunctionResponse(name = msg.toolResponse.name, response = jsonResponse)))
            } else {
                if (msg.content.isNotBlank()) {
                    parts.add(Part(text = msg.content))
                }
            }
            Content(role = role, parts = parts)
        })

        if (prompt.isNotBlank()) {
            contents.add(Content(role = "user", parts = listOf(Part(text = prompt))))
        }

        var tools: List<Tool>? = null
        var toolConfig: ToolConfig? = null
        if (availableTools.isNotEmpty()) {
            val decls = availableTools.mapNotNull { tool ->
                tool.schema?.let { FunctionDeclaration(name = tool.name, description = tool.description, parameters = it) }
            }
            if (decls.isNotEmpty()) {
                tools = listOf(Tool(functionDeclarations = decls))
                toolConfig = ToolConfig(functionCallingConfig = FunctionCallingConfig(mode = "AUTO"))
            }
        }

        // Activate thinking with <|think|> and recommended sampling
        val baseSystemInstruction = config.systemInstruction ?: "You are a helpful assistant."
        val systemInstructionWithThink = "<|think|>\n$baseSystemInstruction"
        
        val systemContent = Content(role = "system", parts = listOf(Part(text = systemInstructionWithThink)))

        val generationConfig = GenerationConfig(
            temperature = 1.0,
            topP = 0.95,
            topK = 64
        )

        return StrategyRequest(
            body = GenerateContentRequest(contents, tools, toolConfig, systemContent, generationConfig),
            apiVersion = "v1beta"
        )
    }

    override fun parseResponse(responseBody: String): AiResponse {
        try {
            val response = json.decodeFromString<GenerateContentResponse>(responseBody)
            val candidate = response.candidates?.firstOrNull() ?: return AiResponse.Error("No candidates")
            
            // 1. Collect all explicit thoughts (native thinking or marked parts)
            val nativeThought = candidate.content.thought
            val partsMarkedAsThought = candidate.content.parts
                .filter { it.thought }
                .joinToString("\n") { it.text ?: "" }
                .trim()
                
            var combinedThought = when {
                !nativeThought.isNullOrBlank() -> nativeThought
                partsMarkedAsThought.isNotBlank() -> partsMarkedAsThought
                else -> null
            }

            // 2. Find Tool Call
            val toolPart = candidate.content.parts.find { it.functionCall != null }
            val signature = toolPart?.thoughtSignature ?: candidate.content.parts.firstOrNull()?.thoughtSignature

            if (toolPart?.functionCall != null) {
                val args = toolPart.functionCall.args?.mapValues {
                    if (it.value is JsonPrimitive) it.value.jsonPrimitive.content else it.value.toString()
                } ?: emptyMap()
                
                // Heuristic: any other text parts that are NOT marked as thought
                val otherText = candidate.content.parts
                    .filter { it.text != null && !it.thought && it.functionCall == null && it.functionResponse == null }
                    .joinToString("\n") { it.text!! }
                    .trim()

                val finalThought = if (combinedThought == null && otherText.isNotBlank()) otherText else combinedThought
                
                // Final Check: Extra stripping for Gemma specific tokens if they still leaked in
                val (taggedThought, cleanThought) = extractGemma4Thought(finalThought ?: "")
                return AiResponse.ToolCall(toolPart.functionCall.name, args, taggedThought ?: cleanThought.takeIf { it.isNotBlank() }, signature)
            }
            
            // 3. Regular Text Response
            val fullText = candidate.content.parts
                .filter { it.text != null && !it.thought }
                .joinToString("\n") { it.text!! }
                .trim()

            val (taggedThought, cleanText) = extractGemma4Thought(fullText)
            val finalThought = if (combinedThought.isNullOrBlank()) taggedThought else combinedThought

            return AiResponse.Text(cleanText, finalThought, signature)
        } catch (e: Exception) {
            return AiResponse.Error("Parse Error: ${e.message}")
        }
    }

    private fun extractGemma4Thought(text: String): Pair<String?, String> {
        val thoughtRegex = Regex("""<\|channel>thought\n?(.*?)<channel\|>""", RegexOption.DOT_MATCHES_ALL)
        val match = thoughtRegex.find(text)
        val thought = match?.groupValues?.get(1)?.trim()
        val content = text.replace(thoughtRegex, "").trim()
        return thought to content
    }

    override fun parseStreamChunk(chunk: String, buffer: StringBuilder): List<AiResponse> {
        if (!chunk.startsWith("data: ")) return emptyList()
        val jsonStr = chunk.substring(6).trim()
        if (jsonStr == "[DONE]") return emptyList()

        try {
            val element = json.parseToJsonElement(jsonStr)
            val candidatesArray = element.jsonObject["candidates"]?.jsonArray
            val firstCandidateObj = candidatesArray?.firstOrNull()?.jsonObject
            val contentObj = firstCandidateObj?.get("content")?.jsonObject
            
            val events = mutableListOf<AiResponse>()

            // A. Handle native thinking field
            val nativeThought = contentObj?.get("thought")?.jsonPrimitive?.content
            if (!nativeThought.isNullOrBlank()) {
                events.add(AiResponse.Text(content = "", thought = nativeThought))
            }

            // B. Handle parts
            val partsArray = contentObj?.get("parts")?.jsonArray
            partsArray?.forEach { partElement ->
                val partObj = partElement.jsonObject
                
                // 1. Tool Call
                val functionCall = partObj["functionCall"]?.jsonObject
                if (functionCall != null) {
                    val name = functionCall["name"]?.jsonPrimitive?.content ?: ""
                    val args = functionCall["args"]?.jsonObject?.mapValues {
                        if (it.value is JsonPrimitive) it.value.jsonPrimitive.content else it.value.toString()
                    } ?: emptyMap()
                    
                    // Heuristic: pending text in buffer is thought
                    if (buffer.isNotBlank()) {
                        events.add(AiResponse.Text(content = "", thought = buffer.toString().trim()))
                        buffer.setLength(0)
                    }
                    
                    events.add(AiResponse.ToolCall(name, args))
                    return@forEach
                }

                // 2. Text / Thought
                val text = partObj["text"]?.jsonPrimitive?.content ?: return@forEach
                val isThought = partObj["thought"]?.jsonPrimitive?.booleanOrNull ?: false
                
                if (isThought) {
                    events.add(AiResponse.Text(content = "", thought = text))
                } else {
                    // Manual Token Check (Gemma 4 fallbacks)
                    if (text.contains("<|channel>thought")) {
                        // This logic is simplified for streaming; complex token splitting 
                        // could be added if tokens actually appear in native parts.
                        // For now, we trust "thought": true as seen in the logs.
                        events.add(AiResponse.Text(content = "", thought = text.replace("<|channel>thought", "").replace("<channel|>", "").trim()))
                    } else {
                        events.add(AiResponse.Text(content = text))
                    }
                }
            }
            return events
        } catch (e: Exception) {}
        return emptyList()
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
