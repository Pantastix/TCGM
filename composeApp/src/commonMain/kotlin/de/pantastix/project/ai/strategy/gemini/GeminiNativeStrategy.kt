package de.pantastix.project.ai.strategy.gemini

import de.pantastix.project.ai.*
import de.pantastix.project.ai.strategy.AiWorkflowStrategy
import de.pantastix.project.ai.strategy.StrategyRequest
import de.pantastix.project.ai.tool.AgentTool
import de.pantastix.project.model.gemini.*
import de.pantastix.project.model.gemini.AiModel as ApiModel
import kotlinx.serialization.json.*

/**
 * Strategy for Native Gemini models (Flash, Pro).
 * FIXED: 
 * - Returns Deltas in stream to prevent doubling.
 * - Handles thoughts correctly in history without triggering 400 Bad Request.
 */
class GeminiNativeStrategy : AiWorkflowStrategy {
    override val providerType = AiProviderType.GEMINI_CLOUD
    override val modelIdRegex = Regex("""^(models/)?gemini-(3|1\.5|2\.0).*""", RegexOption.IGNORE_CASE)

    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = false // CRITICAL: Do not send null fields like thought_signature
    }

    override fun createUiModel(apiModelData: Any): de.pantastix.project.ai.AiModel {
        val apiModel = apiModelData as ApiModel
        return de.pantastix.project.ai.AiModel(
            id = apiModel.name,
            displayName = apiModel.displayName,
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
        val isGemini3 = config.selectedModelId?.contains("gemini-3", ignoreCase = true) == true
        val contents = mutableListOf<Content>()
        
        contents.addAll(chatHistory.map { msg ->
            val role = when (msg.role) {
                ChatRole.USER -> "user"
                ChatRole.TOOL -> "function"
                else -> "model"
            }

            val parts = mutableListOf<Part>()

            // 1. Add Thought (as separate part for Gemini 2.0+ if it's not a signature for a tool)
            if (!msg.thoughtSignature.isNullOrBlank() && msg.toolCall == null) {
                parts.add(Part(text = msg.thoughtSignature, thought = true))
            }

            // 2. Add content/tool data
            if (msg.toolCall != null) {
                parts.add(Part(
                    functionCall = FunctionCall(
                        name = msg.toolCall.name, 
                        args = mapToJsonObject(msg.toolCall.args)
                    ),
                    thoughtSignature = msg.thoughtSignature // CRITICAL: Link to functionCall part
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

        val generationConfig = if (isGemini3) {
            GenerationConfig(thinkingConfig = ThinkingConfig(includeThoughts = true))
        } else null
        
        val systemContent = config.systemInstruction?.let { 
            Content(role = "system", parts = listOf(Part(text = it)))
        }

        return StrategyRequest(
            body = GenerateContentRequest(contents, tools, toolConfig, systemContent, generationConfig),
            apiVersion = if (isGemini3) "v1alpha" else "v1beta" 
        )
    }

    override fun parseResponse(responseBody: String): AiResponse {
        try {
            val response = json.decodeFromString<GenerateContentResponse>(responseBody)
            val candidate = response.candidates?.firstOrNull() ?: return AiResponse.Error("No candidates")
            val part = candidate.content.parts.firstOrNull()
            
            // Extract thought from specific field OR from parts marked as thought
            val thought = candidate.content.thought ?: candidate.content.parts.find { it.thought }?.text
            val signature = part?.thoughtSignature // Try to get from first part
            
            if (part?.functionCall != null) {
                val args = part.functionCall.args?.mapValues {
                    if (it.value is JsonPrimitive) it.value.jsonPrimitive.content else it.value.toString()
                } ?: emptyMap()
                return AiResponse.ToolCall(part.functionCall.name, args, thought, signature)
            }
            
            return AiResponse.Text(part?.text ?: "", thought, signature)
        } catch (e: Exception) {
            return AiResponse.Error("Parse Error: ${e.message}")
        }
    }

    override fun parseStreamChunk(chunk: String, buffer: StringBuilder): List<AiResponse> {
        if (!chunk.startsWith("data: ")) return emptyList()
        val jsonStr = chunk.substring(6).trim()
        if (jsonStr == "[DONE]") return emptyList()

        try {
            // Robust parsing: extract thoughtSignature manually if JSON decoder misses it due to camelCase
            val element = json.parseToJsonElement(jsonStr)
            val parts = element.jsonObject["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
            val firstPart = parts?.firstOrNull()?.jsonObject
            
            val manualSignature = firstPart?.get("thoughtSignature")?.jsonPrimitive?.content 
                ?: firstPart?.get("thought_signature")?.jsonPrimitive?.content

            val response = json.decodeFromString<GenerateContentResponse>(jsonStr)
            val candidate = response.candidates?.firstOrNull()
            val part = candidate?.content?.parts?.firstOrNull() ?: return emptyList()
            
            val signature = part.thoughtSignature ?: manualSignature
            
            if (part.functionCall != null) {
                val args = part.functionCall.args?.mapValues {
                    if (it.value is JsonPrimitive) it.value.jsonPrimitive.content else it.value.toString()
                } ?: emptyMap()
                
                return listOf(AiResponse.ToolCall(
                    toolName = part.functionCall.name,
                    parameters = args,
                    thought = null,
                    thoughtSignature = signature
                ))
            }
            
            if (part.text != null) {
                val textChunk = part.text
                if (textChunk.contains("Calling tool:") || textChunk.contains("default_api")) return emptyList()

                return if (part.thought) {
                    listOf(AiResponse.Text(content = "", thought = textChunk, thoughtSignature = signature))
                } else {
                    listOf(AiResponse.Text(content = textChunk, thought = null, thoughtSignature = signature))
                }
            }
            
            // If we have a signature but no text/call yet, still report it
            if (signature != null) {
                return listOf(AiResponse.Text(content = "", thought = null, thoughtSignature = signature))
            }

        } catch (e: Exception) { }
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
