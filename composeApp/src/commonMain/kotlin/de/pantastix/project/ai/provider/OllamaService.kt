package de.pantastix.project.ai.provider

import de.pantastix.project.ai.*
import de.pantastix.project.ai.model.ollama.*
import de.pantastix.project.ai.strategy.AiWorkflowStrategy
import de.pantastix.project.ai.strategy.ollama.GptOssStrategy
import de.pantastix.project.ai.strategy.ollama.NativeOllamaStrategy
import de.pantastix.project.ai.strategy.ollama.SimulatedJsonStrategy
import de.pantastix.project.ai.tool.AgentTool
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.*

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class OllamaService(private val client: HttpClient) : AiService {
    override val providerType = AiProviderType.OLLAMA_LOCAL

    private val localStrategies = listOf(
        GptOssStrategy(),
        NativeOllamaStrategy(),
        SimulatedJsonStrategy()
    )

    override suspend fun getAvailableModels(config: AiConfig): List<AiModel> {
        val host = config.hostUrl ?: "http://localhost:11434"
        return try {
            val response = client.get("$host/api/tags")
            val tagsResponse = response.body<OllamaTagsResponse>()
            
            tagsResponse.models.mapNotNull { ollamaModel ->
                val strategy = localStrategies.find { it.modelIdRegex.matches(ollamaModel.name) } 
                    ?: localStrategies.last()
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
        val strategy = localStrategies.find { it.modelIdRegex.matches(modelId) } ?: localStrategies.last()
        val requestCtx = strategy.createRequest(prompt, chatHistory, config, availableTools)

        return try {
            val response = client.post("$host/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(requestCtx.body)
            }
            val responseBody = response.bodyAsText()
            println("\n[OLLAMA RAW OUTPUT] $responseBody\n")
            strategy.parseResponse(responseBody)
        } catch (e: Exception) {
            println("[OLLAMA ERROR] Request failed: ${e.message}")
            e.printStackTrace()
            AiResponse.Error("Ollama Error: ${e.message}")
        }
    }
    
    override suspend fun streamResponse(
        prompt: String,
        chatHistory: List<ChatMessage>,
        config: AiConfig,
        availableTools: List<AgentTool>
    ): Flow<AiResponse> = flow {
        val host = config.hostUrl ?: "http://localhost:11434"
        val modelId = config.selectedModelId ?: run { emit(AiResponse.Error("No model selected")); return@flow }
        val strategy = localStrategies.find { it.modelIdRegex.matches(modelId) } ?: localStrategies.last()
        
        val requestCtx = strategy.createRequest(prompt, chatHistory, config, availableTools)
        val buffer = StringBuilder()

        try {
            client.preparePost("$host/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(requestCtx.body)
            }.execute { response ->
                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break
                    if (line.isBlank()) continue
                    
                    val parsedChunks = strategy.parseStreamChunk(line, buffer)
                    parsedChunks.forEach { emit(it) }
                }
            }
        } catch (e: Exception) {
            emit(AiResponse.Error("Ollama Stream Error: ${e.message}"))
        }
    }
}
