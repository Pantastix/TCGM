package de.pantastix.project.ai.provider

import de.pantastix.project.ai.*
import de.pantastix.project.ai.tool.AgentTool
import de.pantastix.project.model.gemini.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class GeminiCloudService(private val client: HttpClient) : AiService {
    override val providerType = AiProviderType.GEMINI_CLOUD

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val modelFamilies = listOf(
        GeminiFlashFamily(),
        GemmaFamily()
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
                    modelFamilies.firstOrNull { it.regex.matches(apiModel.name) }?.createAiModel(apiModel)
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

        // 1. Convert ChatHistory to Gemini Content
        val contents = chatHistory.map {
            val role = when (it.role) {
                ChatRole.USER -> "user"
                ChatRole.TOOL -> "user" // Treat tool results as user/system input
                else -> "model"
            }
            
            val text = if (it.role == ChatRole.TOOL) "Tool Result: ${it.content}" else it.content
            
            Content(
                role = role,
                parts = listOf(Part(text = text))
            )
        }.toMutableList()

        // Add current prompt only if not empty (avoids sending empty user message after tool result)
        if (prompt.isNotBlank()) {
            contents.add(Content(role = "user", parts = listOf(Part(text = prompt))))
        }

        // 2. Prepare Tools or System Instruction
        var tools: List<Tool>? = null
        val systemInstruction: Content? = null // Explicitly null as we inject into contents

        if (isGemma3) {
            // For Gemma 3, we use the simulated reasoning workflow via System Instruction
            // Since "Developer instructions" field might not be supported, we inject it as the first User message.
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
            
            // Inject fake dialogue to stabilize the model
            contents.add(0, Content(role = "user", parts = listOf(Part(text = instructionText))))
            contents.add(1, Content(role = "model", parts = listOf(Part(text = "Understood. I will always think step-by-step in a <think> block first, and then output either a JSON tool call or a text response."))))
        } else {
            // Standard Gemini Native Tool Calling (Placeholder for now as per original code)
             tools = if (availableTools.isNotEmpty()) {
                 null 
            } else null
        }

        val request = GenerateContentRequest(
            contents = contents,
            tools = tools,
            systemInstruction = systemInstruction
        )

        return try {
            val safeModelName = if (modelId.startsWith("models/")) modelId else "models/$modelId"
            
            val response = client.post("https://generativelanguage.googleapis.com/v1beta/$safeModelName:generateContent") {
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
            val part = candidate?.content?.parts?.firstOrNull()
            val textContent = part?.text ?: ""
            
            println("--- RAW MODEL OUTPUT ---\n$textContent\n------------------------")

            if (isGemma3) {
                // Parse Simulated Response for Gemma 3
                val thinkRegex = """<think>(.*?)</think>""".toRegex(RegexOption.DOT_MATCHES_ALL)
                val thinkMatch = thinkRegex.find(textContent)
                val thought = thinkMatch?.groupValues?.get(1)?.trim()
                
                val contentWithoutThought = textContent.replace(thinkRegex, "").trim()
                
                // Check for JSON tool call (Relaxed Regex: 'json' tag is optional)
                val codeBlockRegex = """```(?:json)?\s*(\{.*?\})\s*```""".toRegex(RegexOption.DOT_MATCHES_ALL)
                var jsonMatch = codeBlockRegex.find(contentWithoutThought)
                
                // Fallback: Look for raw JSON if no code block found (starts with { and contains "tool")
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
                        
                        if (toolName != null) {
                             AiResponse.ToolCall(toolName, params, thought)
                        } else {
                             AiResponse.Text(contentWithoutThought, thought)
                        }
                    } catch (e: Exception) {
                        AiResponse.Text(contentWithoutThought, thought)
                    }
                } else {
                    AiResponse.Text(contentWithoutThought, thought)
                }
            } else {
                // Standard Gemini Response Handling
                if (part?.functionCall != null) {
                    val args = part.functionCall.args?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap()
                    AiResponse.ToolCall(
                        toolName = part.functionCall.name,
                        parameters = args
                    )
                } else {
                    AiResponse.Text(textContent)
                }
            }
            
        } catch (e: Exception) {
            println("Gemini Error: ${e.message}")
            AiResponse.Error("Gemini Error: ${e.message}")
        }
    }
}