package de.pantastix.project.ai.provider

import de.pantastix.project.ai.*
import de.pantastix.project.ai.model.ollama.*
import de.pantastix.project.ai.strategy.AiWorkflowStrategy
import de.pantastix.project.ai.strategy.ollama.NativeOllamaStrategy
import de.pantastix.project.ai.strategy.ollama.SimulatedJsonStrategy
import de.pantastix.project.ai.tool.AgentTool
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class OllamaService(private val client: HttpClient) : AiService {
    override val providerType = AiProviderType.OLLAMA_LOCAL

    // Register local strategies here or use AiModelRegistry if you want global
    private val localStrategies = listOf(
        NativeOllamaStrategy(),
        SimulatedJsonStrategy()
    )

    override suspend fun getAvailableModels(config: AiConfig): List<AiModel> {
        val host = config.hostUrl ?: "http://localhost:11434"
        return try {
            val response = client.get("$host/api/tags")
            val tagsResponse = response.body<OllamaTagsResponse>()
            
            tagsResponse.models.mapNotNull { ollamaModel ->
                // Find matching strategy to convert to UI model
                val strategy = localStrategies.find { it.modelIdRegex.matches(ollamaModel.name) } 
                    ?: localStrategies.last() // Fallback
                strategy.createUiModel(ollamaModel)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun generateResponse(
        prompt: String,
        chatHistory: List<ChatMessage>,
        config: AiConfig,
        availableTools: List<AgentTool>
    ): AiResponse {
        val host = config.hostUrl ?: "http://localhost:11434"
        val modelId = config.selectedModelId ?: return AiResponse.Error("No model selected")

        val strategy = localStrategies.find { it.modelIdRegex.matches(modelId) } 
             ?: localStrategies.last()

        val requestCtx = strategy.createRequest(prompt, chatHistory, config, availableTools)
        val requestBody = requestCtx.body

        return try {
            val response = client.post("$host/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
            strategy.parseResponse(response.bodyAsText())
        } catch (e: Exception) {
            AiResponse.Error("Ollama Error: ${e.message}")
        }
    }
    
    override suspend fun streamResponse(
        prompt: String,
        chatHistory: List<ChatMessage>,
        config: AiConfig,
        availableTools: List<AgentTool>
    ): Flow<AiResponse> = flow {
         emit(generateResponse(prompt, chatHistory, config, availableTools))
    }
}