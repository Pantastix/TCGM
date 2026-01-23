package de.pantastix.project.ai.strategy.gemini

import de.pantastix.project.ai.*
import de.pantastix.project.ai.strategy.AiWorkflowStrategy
import de.pantastix.project.ai.strategy.StrategyRequest
import de.pantastix.project.ai.tool.AgentTool
import de.pantastix.project.model.gemini.*
import de.pantastix.project.model.gemini.AiModel as ApiModel
import kotlinx.serialization.json.*

/**
 * Strategy for Native Gemini models (Flash, Pro) that support:
 * - Native Function Calling
 * - Native "Thinking" (if enabled in config)
 */
class GeminiNativeStrategy : AiWorkflowStrategy {
    override val providerType = AiProviderType.GEMINI_CLOUD
    
    // Matches "gemini-3-flash", "gemini-1.5-pro", etc.
    override val modelIdRegex = Regex("""^(models/)?gemini-(3|1\.5|2\.0).*""", RegexOption.IGNORE_CASE)

    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
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
        val isLegacy = !isGemini3 // 1.5 and 2.0 use v1beta, 3.0 usually v1alpha (check docs, assuming v1beta for now unless specified)

        val contents = mutableListOf<Content>()
        
        // Convert Chat History
        contents.addAll(chatHistory.map { msg ->
            val role = when (msg.role) {
                ChatRole.USER -> "user"
                ChatRole.TOOL -> "function"
                else -> "model"
            }

            val parts = mutableListOf<Part>()

            if (msg.toolCall != null) {
                val jsonArgs = mapToJsonObject(msg.toolCall.args)
                parts.add(Part(functionCall = FunctionCall(name = msg.toolCall.name, args = jsonArgs), thoughtSignature = msg.thoughtSignature))
            } else if (msg.toolResponse != null) {
                val jsonResponse = try {
                    json.decodeFromString<JsonObject>(msg.toolResponse.result)
                } catch (e: Exception) {
                    buildJsonObject { put("result", msg.toolResponse.result) }
                }
                parts.add(Part(functionResponse = FunctionResponse(name = msg.toolResponse.name, response = jsonResponse)))
            } else {
                parts.add(Part(text = msg.content, thoughtSignature = msg.thoughtSignature))
            }
            Content(role = role, parts = parts)
        })

        // Add User Prompt
        if (prompt.isNotBlank()) {
            contents.add(Content(role = "user", parts = listOf(Part(text = prompt))))
        }

        // Configure Tools
        var tools: List<Tool>? = null
        var toolConfig: ToolConfig? = null
        if (availableTools.isNotEmpty()) {
            val functionDeclarations = availableTools.mapNotNull { tool ->
                tool.schema?.let { schema ->
                    FunctionDeclaration(
                        name = tool.name,
                        description = tool.description,
                        parameters = schema
                    )
                }
            }
            if (functionDeclarations.isNotEmpty()) {
                tools = listOf(Tool(functionDeclarations = functionDeclarations))
                toolConfig = ToolConfig(functionCallingConfig = FunctionCallingConfig(mode = "AUTO"))
            }
        }

        // Configure Generation (Thinking)
        // Only Gemini 3.0 supports "thinkingConfig" natively usually
        val generationConfig = if (isGemini3) {
            GenerationConfig(thinkingConfig = ThinkingConfig(includeThoughts = true))
        } else {
            null
        }
        
        val systemContent = config.systemInstruction?.let { 
            Content(role = "system", parts = listOf(Part(text = it)))
        }

        val request = GenerateContentRequest(
            contents = contents,
            tools = tools,
            toolConfig = toolConfig,
            generationConfig = generationConfig,
            systemInstruction = systemContent
        )

        return StrategyRequest(
            body = request,
            apiVersion = if (isGemini3) "v1alpha" else "v1beta" 
        )
    }

    override fun parseResponse(responseBody: String): AiResponse {
        try {
            val response = json.decodeFromString<GenerateContentResponse>(responseBody)
            val candidate = response.candidates?.firstOrNull() ?: return AiResponse.Error("No candidates")
            val part = candidate.content.parts.firstOrNull()
            
            val thought = candidate.content.thought
            
            if (part?.functionCall != null) {
                val args = part.functionCall.args?.mapValues {
                    if (it.value is JsonPrimitive) it.value.jsonPrimitive.content else it.value.toString()
                } ?: emptyMap()
                return AiResponse.ToolCall(part.functionCall.name, args, thought)
            }
            
            return AiResponse.Text(part?.text ?: "", thought)
        } catch (e: Exception) {
            return AiResponse.Error("Parse Error: ${e.message}")
        }
    }

    private var accumulatedText = ""
    private var accumulatedThought = ""
    
    override fun parseStreamChunk(chunk: String, buffer: StringBuilder): List<AiResponse> {
        if (!chunk.startsWith("data: ")) return emptyList()
        val jsonStr = chunk.substring(6).trim()
        if (jsonStr == "[DONE]") return emptyList()

        try {
            val response = json.decodeFromString<GenerateContentResponse>(jsonStr)
            val candidate = response.candidates?.firstOrNull()
            val part = candidate?.content?.parts?.firstOrNull()
            
            // Native Thought (if supported by model/API version)
            val newThought = candidate?.content?.thought
            if (!newThought.isNullOrEmpty()) {
                accumulatedThought += newThought
                return listOf(AiResponse.Text(accumulatedText, accumulatedThought))
            }
            
            if (part != null) {
                // IMPORTANT: Extract thought signature from function call chunk if present
                val thoughtSig = part.thoughtSignature
                
                if (part.functionCall != null) {
                     val args = part.functionCall.args?.mapValues {
                        if (it.value is JsonPrimitive) it.value.jsonPrimitive.content else it.value.toString()
                    } ?: emptyMap()
                    
                    return listOf(AiResponse.ToolCall(
                        toolName = part.functionCall.name,
                        parameters = args,
                        thought = accumulatedThought.takeIf { it.isNotEmpty() },
                        thoughtSignature = thoughtSig
                    ))
                }
                
                if (part.text != null) {
                    val textChunk = part.text
                    // FILTER: Ignore leaked tool call descriptions from Gemini 3 Flash
                    if (textChunk.contains("Calling tool:") || 
                        textChunk.contains("default_api") || 
                        textChunk.contains("<ctrl") || 
                        accumulatedText.endsWith("Calling tool:") // Handle split chunks
                    ) {
                        return emptyList()
                    }

                    if (part.thought) {
                        accumulatedThought += textChunk
                    } else {
                        accumulatedText += textChunk
                    }
                    
                    return listOf(AiResponse.Text(
                        content = accumulatedText, 
                        thought = accumulatedThought.takeIf { it.isNotEmpty() },
                        thoughtSignature = thoughtSig
                    ))
                }
            }
        } catch (e: Exception) {
            // ignore
        }
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
