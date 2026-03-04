package de.pantastix.project.ai.provider

import de.pantastix.project.ai.*
import de.pantastix.project.ai.strategy.claude.ClaudeNativeStrategy
import de.pantastix.project.ai.tool.AgentTool
import de.pantastix.project.model.claude.ClaudeModelList
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

class ClaudeService(private val client: HttpClient) : AiService {
    override val providerType = AiProviderType.CLAUDE_CLOUD
    private val baseUrl = "https://api.anthropic.com"
    private val apiVersion = "2023-06-01"
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    override suspend fun getAvailableModels(config: AiConfig): List<AiModel> {
        val apiKey = config.apiKey ?: return emptyList()
        val strategy = ClaudeNativeStrategy()
        
        return try {
            val response: ClaudeModelList = client.get("$baseUrl/v1/models") {
                header("x-api-key", apiKey)
                header("anthropic-version", apiVersion)
            }.body()
            
            response.data.map { strategy.createUiModel(it) }
        } catch (e: Exception) {
            println("Error fetching Claude models: ${e.message}")
            emptyList()
        }
    }

    override suspend fun generateResponse(
        prompt: String,
        chatHistory: List<ChatMessage>,
        config: AiConfig,
        availableTools: List<AgentTool>
    ): AiResponse {
        val apiKey = config.apiKey ?: return AiResponse.Error("API Key fehlt")
        val strategy = ClaudeNativeStrategy()
        val strategyRequest = strategy.createRequest(prompt, chatHistory, config, availableTools)
        
        return try {
            val response: HttpResponse = client.post("$baseUrl${strategyRequest.urlSuffix ?: "/v1/messages"}") {
                header("x-api-key", apiKey)
                header("anthropic-version", apiVersion)
                contentType(ContentType.Application.Json)
                setBody(strategyRequest.body)
                // Force non-streaming if needed (though our strategy defaults to stream=true)
                if (strategyRequest.body is de.pantastix.project.model.claude.ClaudeMessageRequest) {
                    setBody(strategyRequest.body.copy(stream = false))
                }
            }
            
            val bodyText = response.bodyAsText()
            if (response.status == HttpStatusCode.OK) {
                strategy.parseResponse(bodyText)
            } else {
                println("[CLAUDE ERROR] Status: ${response.status} - Body: $bodyText")
                AiResponse.Error("Claude API Fehler (${response.status})")
            }
        } catch (e: Exception) {
            println("[CLAUDE EXCEPTION] ${e.message}")
            e.printStackTrace()
            AiResponse.Error("Claude Verbindungsfehler")
        }
    }

    override suspend fun streamResponse(
        prompt: String,
        chatHistory: List<ChatMessage>,
        config: AiConfig,
        availableTools: List<AgentTool>
    ): Flow<AiResponse> = flow {
        val apiKey = config.apiKey
        if (apiKey == null) {
            emit(AiResponse.Error("API Key fehlt"))
            return@flow
        }
        
        val strategy = ClaudeNativeStrategy()
        val strategyRequest = strategy.createRequest(prompt, chatHistory, config, availableTools)
        val buffer = StringBuilder()

        try {
            client.preparePost("$baseUrl${strategyRequest.urlSuffix ?: "/v1/messages"}") {
                header("x-api-key", apiKey)
                header("anthropic-version", apiVersion)
                contentType(ContentType.Application.Json)
                setBody(strategyRequest.body)
                timeout {
                    requestTimeoutMillis = 60000
                    connectTimeoutMillis = 10000
                }
            }.execute { response ->
                if (response.status != HttpStatusCode.OK) {
                    val errorBody = runBlocking { response.bodyAsText() }
                    println("[CLAUDE STREAM ERROR] Status: ${response.status} - Body: $errorBody")
                    emit(AiResponse.Error("Claude Stream Fehler (${response.status})"))
                    return@execute
                }
                
                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break
                    if (line.isBlank()) continue
                    
                    val responses = strategy.parseStreamChunk(line, buffer)
                    responses.forEach { emit(it) }
                }
            }
        } catch (e: Exception) {
            println("[CLAUDE STREAM EXCEPTION] ${e.message}")
            emit(AiResponse.Error("Claude Stream Unterbrochen"))
        }
    }
}
