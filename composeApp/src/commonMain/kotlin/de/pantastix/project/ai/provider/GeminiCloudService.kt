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

        // 1. Convert ChatHistory to Gemini Content
        val contents = chatHistory.map {
            Content(
                role = if (it.role == ChatRole.USER) "user" else "model",
                parts = listOf(Part(text = it.content))
            )
        }.toMutableList()

        // Add current prompt
        contents.add(Content(role = "user", parts = listOf(Part(text = prompt))))

        // 2. Prepare Tools (Simplified mapping for now)
        // Gemini requires a strict Schema object. 
        // Since AgentTool currently provides a JSON string, we would need to parse it.
        // For this iteration, we will skip sending tools if the schema conversion is complex, 
        // or we manually define a schema for known tools for testing.
        // TODO: Implement proper Schema parsing from AgentTool.parameterSchemaJson

        val tools = if (availableTools.isNotEmpty()) {
             // Placeholder: We need to parse schemaJson to Schema object. 
             // For now, we will NOT send tools to avoid runtime crashes until Schema parser is ready.
             // Or we can construct a dummy tool to test connectivity if needed.
             null 
        } else null

        val request = GenerateContentRequest(
            contents = contents,
            tools = tools
        )

        return try {
            val safeModelName = if (modelId.startsWith("models/")) modelId else "models/$modelId"
            
            val response = client.post("https://generativelanguage.googleapis.com/v1beta/$safeModelName:generateContent") {
                parameter("key", apiKey)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            val responseBody = response.bodyAsText()
            val geminiResponse = json.decodeFromString(GenerateContentResponse.serializer(), responseBody)
            
            val candidate = geminiResponse.candidates?.firstOrNull()
            val part = candidate?.content?.parts?.firstOrNull()
            
            if (part?.functionCall != null) {
                val args = part.functionCall.args?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap()
                AiResponse.ToolCall(
                    toolName = part.functionCall.name,
                    parameters = args
                )
            } else {
                AiResponse.Text(part?.text ?: "No response text")
            }
            
        } catch (e: Exception) {
            println("Gemini Error: ${e.message}")
            AiResponse.Error("Gemini Error: ${e.message}")
        }
    }
}