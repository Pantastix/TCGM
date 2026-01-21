package de.pantastix.project.ai.provider

import de.pantastix.project.ai.*
import de.pantastix.project.ai.tool.AgentTool
import de.pantastix.project.model.gemini.ModelListResponse
import de.pantastix.project.model.gemini.GenerateContentRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class GeminiCloudService(private val client: HttpClient) : AiService {
    override val providerType = AiProviderType.GEMINI_CLOUD

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
                    // Find strategy that claims this model
                    val strategy = AiModelRegistry.getStrategyForDiscovery(apiModel.name, providerType)
                    strategy?.createUiModel(apiModel)
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
        val apiKey = config.apiKey ?: return AiResponse.Error("Missing API Key")
        val modelId = config.selectedModelId ?: "models/gemini-1.5-flash"
        
        val strategy = AiModelRegistry.resolveStrategy(modelId, providerType)
            ?: return AiResponse.Error("No strategy found for model: $modelId")

        try {
            val requestCtx = strategy.createRequest(prompt, chatHistory, config, availableTools)
            val requestBody = requestCtx.body as? GenerateContentRequest 
                ?: return AiResponse.Error("Invalid request type for Gemini Cloud")

            val safeModelName = if (modelId.startsWith("models/")) modelId else "models/$modelId"
            val url = "https://generativelanguage.googleapis.com/${requestCtx.apiVersion}/$safeModelName:generateContent"

            val response = client.post(url) {
                parameter("key", apiKey)
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }

            if (response.status != HttpStatusCode.OK) {
                return AiResponse.Error("API Error ${response.status}: ${response.bodyAsText()}")
            }

            val responseText = response.bodyAsText()
            println("\n=== [DEBUG] GEMINI RAW RESPONSE ===\n$responseText\n===================================\n")
            return strategy.parseResponse(responseText)

        } catch (e: Exception) {
            return AiResponse.Error("Request Failed: ${e.message}")
        }
    }

    override suspend fun streamResponse(
        prompt: String,
        chatHistory: List<ChatMessage>,
        config: AiConfig,
        availableTools: List<AgentTool>
    ): Flow<AiResponse> = flow {
        val apiKey = config.apiKey ?: run {
            emit(AiResponse.Error("Missing API Key"))
            return@flow
        }
        val modelId = config.selectedModelId ?: "models/gemini-1.5-flash"
        
        val strategy = AiModelRegistry.resolveStrategy(modelId, providerType)
        if (strategy == null) {
            emit(AiResponse.Error("No strategy found for model: $modelId"))
            return@flow
        }

        val requestCtx = strategy.createRequest(prompt, chatHistory, config, availableTools)
        val requestBody = requestCtx.body as? GenerateContentRequest
        if (requestBody == null) {
            emit(AiResponse.Error("Invalid request type"))
            return@flow
        }

        val safeModelName = if (modelId.startsWith("models/")) modelId else "models/$modelId"
        val urlSuffix = requestCtx.urlSuffix ?: ":streamGenerateContent"
        val url = "https://generativelanguage.googleapis.com/${requestCtx.apiVersion}/$safeModelName$urlSuffix?alt=sse"

        var attempt = 0
        val maxAttempts = 3
        
        while (attempt < maxAttempts) {
            try {
                client.preparePost(url) {
                    parameter("key", apiKey)
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }.execute { response ->
                    if (response.status != HttpStatusCode.OK) {
                        throw Exception("API Error ${response.status}: ${response.bodyAsText()}")
                    }

                    val channel = response.bodyAsChannel()
                    val buffer = StringBuilder()
                    val fullRawStream = StringBuilder()
                    val fullAssembledText = StringBuilder()
                    
                    while (!channel.isClosedForRead) {
                        val line = channel.readUTF8Line() ?: break
                        if (line.isNotBlank()) {
                            if (line.startsWith("data: ")) {
                                val jsonPart = line.substring(6)
                                fullRawStream.append(jsonPart).append("\n")
                                
                                // Try to extract text for readable debug log
                                try {
                                    val element = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.parseToJsonElement(jsonPart)
                                    val candidates = element.jsonObject["candidates"]?.jsonArray
                                    val firstCandidate = candidates?.firstOrNull()?.jsonObject
                                    val content = firstCandidate?.get("content")?.jsonObject
                                    val parts = content?.get("parts")?.jsonArray
                                    val firstPart = parts?.firstOrNull()?.jsonObject
                                    val text = firstPart?.get("text")?.jsonPrimitive?.content
                                    
                                    if (text != null) fullAssembledText.append(text)
                                } catch (e: Exception) { /* ignore debug parsing errors */ }
                            }
                            val events = strategy.parseStreamChunk(line, buffer)
                            events.forEach { emit(it) }
                        }
                    }
                    println("\n=== [DEBUG] GEMINI FULL STREAM RAW DATA ===\n$fullRawStream\n")
                    println("=== [DEBUG] ASSEMBLED TEXT ===\n$fullAssembledText\n===========================================\n")
                }
                return@flow // Success
            } catch (e: Exception) {
                attempt++
                if (attempt >= maxAttempts) {
                    emit(AiResponse.Error("Stream Error: ${e.message}"))
                } else {
                    delay(1000L * attempt)
                }
            }
        }
    }
}
