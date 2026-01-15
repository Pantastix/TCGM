package de.pantastix.project.ai.provider

import de.pantastix.project.ai.*
import de.pantastix.project.ai.model.gemini.*
import de.pantastix.project.ai.tool.AgentTool
import de.pantastix.project.model.gemini.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

class GeminiCloudService(private val client: HttpClient) : AiService {
    override val providerType = AiProviderType.GEMINI_CLOUD

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val modelGroups: List<GeminiModelGroup> = listOf(
        Gemini3Flash(),
        Gemma()
    )

    override suspend fun getAvailableModels(config: AiConfig): List<de.pantastix.project.ai.AiModel> {
        val apiKey = config.apiKey ?: return emptyList()
        return try {
            val response = client.get("https://generativelanguage.googleapis.com/v1beta/models") {
                parameter("key", apiKey)
            }
            val listResponse = response.body<ModelListResponse>()

            listResponse.models
                .filter { it.supportedGenerationMethods.contains("generateContent") }
                .mapNotNull { apiModel ->
                    modelGroups.firstOrNull { it.regex.matches(apiModel.name) }?.createAiModel(apiModel)
                }
                .sortedByDescending { it.displayName }
        } catch (e: Exception) {
            println("Error fetching Gemini models: ${e.message}")
            emptyList()
        }
    }

    override suspend fun generateResponse(
        prompt: String,
        chatHistory: List<ChatMessage>,
        config: AiConfig,
        availableTools: List<AgentTool>
    ): AiResponse {
        val apiKey = config.apiKey ?: return AiResponse.Error("API Key missing")
        val modelId = config.selectedModelId ?: "models/gemini-2.0-flash" // Fallback
        val isGemma3 = modelId.contains("gemma-3", ignoreCase = true)
        val isGemini3 = modelId.contains("gemini-3", ignoreCase = true)

        val contents = mutableListOf<Content>()

        if (isGemma3) {
            // --- GEMMA 3 SIMULATED WORKFLOW ---
            // 1. Convert ChatHistory to Text-Only Content
            contents.addAll(chatHistory.map {
                val role = when (it.role) {
                    ChatRole.USER -> "user"
                    ChatRole.TOOL -> "user" // Treat tool results as user/system input
                    else -> "model"
                }
                val text = if (it.role == ChatRole.TOOL) "Tool Result: ${it.content}" else it.content
                Content(role = role, parts = listOf(Part(text = text)))
            })

            // Add current prompt
            if (prompt.isNotBlank()) {
                contents.add(Content(role = "user", parts = listOf(Part(text = prompt))))
            }

            // Inject System Instruction as User Message
            val toolsDescription = if (availableTools.isNotEmpty()) {
                availableTools.joinToString("\n") { "- ${it.name}: ${it.description}. Parameters: ${it.parameterSchemaJson}" }
            } else "None"

            val instructionText = """
                You are a helpful assistant for a Pokémon TCG application.
                
                AVAILABLE TOOLS:
                $toolsDescription
                
                PROTOCOL:
                1. THOUGHT PROCESS: Start every response with a <think>...</think> block. Analyze the user's request step-by-step.
                2. ACTION:
                   - If you need data (prices, inventory, stats), output a JSON tool call immediately after the </think> tag.
                   - If you can answer directly, output the final text answer immediately after the </think> tag.
                
                FORMATS:
                
                [Tool Call]
                <think>
                Reasoning here...
                </think>
                ```json
                { "tool": "tool_name", "parameters": { "param": "value" } }
                ```
                
                [Text Response]
                <think>
                Reasoning here...
                </think>
                Your final answer here.
            """.trimIndent()

            contents.add(0, Content(role = "user", parts = listOf(Part(text = instructionText))))
            contents.add(1, Content(role = "model", parts = listOf(Part(text = "Understood. I will always think step-by-step in a <think> block first, and then output either a JSON tool call or a text response."))))

        } else {
            // --- STANDARD GEMINI NATIVE WORKFLOW ---
        // 1. Chat History Konvertierung (Standard Gemini Workflow)
        contents.addAll(chatHistory.map { msg ->
            val role = when (msg.role) {
                ChatRole.USER -> "user"
                ChatRole.TOOL -> "user" // Tool responses are treated as user input in the new API
                else -> "model"
            }

                val parts = mutableListOf<Part>()

                if (msg.toolCall != null) {
                    // Reconstruct Function Call
                    val jsonArgs = mapToJsonObject(msg.toolCall.args)
                    parts.add(Part(functionCall = FunctionCall(name = msg.toolCall.name, args = jsonArgs), thoughtSignature = msg.thoughtSignature))
                } else if (msg.toolResponse != null) {
                    // Reconstruct Function Response
                    val jsonResponse = try {
                        json.decodeFromString<JsonObject>(msg.toolResponse.result)
                    } catch (e: Exception) {
                        buildJsonObject { put("result", msg.toolResponse.result) }
                    }
                    parts.add(Part(functionResponse = FunctionResponse(name = msg.toolResponse.name, response = jsonResponse)))
                } else {
                    // Text Content
                    parts.add(Part(text = msg.content, thoughtSignature = msg.thoughtSignature))
                }
                Content(role = role, parts = parts)
            })

            // Add current prompt
            if (prompt.isNotBlank()) {
                contents.add(Content(role = "user", parts = listOf(Part(text = prompt))))
            }
        }

        // 2. Prepare Tools & Config
        var tools: List<Tool>? = null
        val generationConfig: GenerationConfig? = if (isGemini3) {
            GenerationConfig(thinkingConfig = ThinkingConfig(includeThoughts = true))
        } else {
            null
        }

        if (!isGemma3 && availableTools.isNotEmpty()) {
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
            }
        }

        val request = GenerateContentRequest(
            contents = contents,
            tools = tools,
            generationConfig = generationConfig
            // systemInstruction can be added here if needed for Gemini
        )

                return try {
                    val safeModelName = if (modelId.startsWith("models/")) modelId else "models/$modelId"
                    val apiVersion = if (isGemini3) "v1alpha" else "v1beta"
        
                    val response = client.post("https://generativelanguage.googleapis.com/$apiVersion/$safeModelName:generateContent") {
                        parameter("key", apiKey)
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }
                    val responseBody = response.bodyAsText()

            if (response.status != HttpStatusCode.OK) {
                println("Gemini API Error: $responseBody")
                return AiResponse.Error("API Error (${response.status}): $responseBody")
            }

            val geminiResponse = json.decodeFromString(GenerateContentResponse.serializer(), responseBody)

            val candidate = geminiResponse.candidates?.firstOrNull()
            
            // --- UPDATED RESPONSE PARSING FOR GEMINI 3 / THINKING ---
            
            var collectedText = ""
            var collectedThought = candidate?.content?.thought // Try to get from explicit field
            var foundToolCall: AiResponse.ToolCall? = null
            var capturedThoughtSignature: String? = null
            
            val textParts = mutableListOf<String>()

            // Iterate over ALL parts to find text, thought signatures and tool calls
            println("Candidate Thought (Field): ${candidate?.content?.thought?.take(50)}...")
            
            candidate?.content?.parts?.forEachIndexed { index, part ->
                println("Part $index: text=${part.text?.take(50)}..., thoughtSig=${part.thoughtSignature != null}, funcCall=${part.functionCall?.name}")
                
                if (part.thoughtSignature != null) {
                    capturedThoughtSignature = part.thoughtSignature
                }
                
                if (part.functionCall != null) {
                    val args = part.functionCall.args?.mapValues {
                        if (it.value is JsonPrimitive) it.value.jsonPrimitive.content else it.value.toString()
                    } ?: emptyMap()
                    
                    foundToolCall = AiResponse.ToolCall(
                        toolName = part.functionCall.name,
                        parameters = args,
                        thought = null, // Will be set later
                        thoughtSignature = null // Will be set later
                    )
                }
                
                if (part.text != null) {
                    textParts.add(part.text)
                }
            }
            
            // Heuristic for Text/Thought Split
            if (collectedThought == null && textParts.isNotEmpty()) {
                if (foundToolCall != null) {
                    // If tool call, usually the text preceding it is the thought/reasoning
                     collectedThought = textParts.joinToString("")
                } else if (textParts.size > 1) {
                    // If multiple parts and no tool call, assume first is thought (if explicit thought field was missing)
                    // This is speculative but common in streaming/chunked responses for thinking models
                    collectedThought = textParts.first()
                    collectedText = textParts.drop(1).joinToString("")
                } else {
                    // Single part, no tool call -> It's the content
                    collectedText = textParts.joinToString("")
                }
            } else {
                 collectedText = textParts.joinToString("")
            }
            
            println("--- RAW MODEL OUTPUT ---\nText: $collectedText\nThought: $collectedThought\n(Signature: $capturedThoughtSignature)\n------------------------")

            if (isGemma3) {
                 // ... (Keep existing Gemma parsing logic - reusing collectedText) ...
                val textContent = collectedText + (collectedThought ?: "") // Combine for legacy regex parsing if needed
                val thinkRegex = """<think>(.*?)</think>""".toRegex(RegexOption.DOT_MATCHES_ALL)
                val thinkMatch = thinkRegex.find(textContent)
                val thought = thinkMatch?.groupValues?.get(1)?.trim()
                val contentWithoutThought = textContent.replace(thinkRegex, "").trim()
                val codeBlockRegex = """```(?:json)?\s*(\{.*?\})\s*```""".toRegex(RegexOption.DOT_MATCHES_ALL)
                var jsonMatch = codeBlockRegex.find(contentWithoutThought)
                if (jsonMatch == null) {
                    val rawJsonRegex = """(\{[\s\S]*"tool"[\s\S]*\})""".toRegex()
                    jsonMatch = rawJsonRegex.find(contentWithoutThought)
                }
                if (jsonMatch != null) {
                    val jsonString = jsonMatch.groupValues[1]
                    try {
                        val jsonElement = json.parseToJsonElement(jsonString) as JsonObject
                        val toolName = jsonElement["tool"]?.jsonPrimitive?.content
                        val params = jsonElement["parameters"]?.let { it as? JsonObject }?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap()
                        if (toolName != null) AiResponse.ToolCall(toolName, params, thought, capturedThoughtSignature) else AiResponse.Text(contentWithoutThought, thought, capturedThoughtSignature)
                    } catch (e: Exception) { AiResponse.Text(contentWithoutThought, thought, capturedThoughtSignature) }
                } else { AiResponse.Text(contentWithoutThought, thought, capturedThoughtSignature) }
            } else {
                 if (foundToolCall != null) {
                    foundToolCall!!.copy(
                        thought = collectedThought?.takeIf { it.isNotBlank() },
                        thoughtSignature = capturedThoughtSignature
                    )
                } else {
                    AiResponse.Text(
                        content = collectedText,
                        thought = collectedThought, // Now correctly separated
                        thoughtSignature = capturedThoughtSignature
                    )
                }
            }

        } catch (e: Exception) {
            println("Gemini Error: ${e.message}")
            e.printStackTrace()
            AiResponse.Error("Gemini Error: ${e.message}")
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