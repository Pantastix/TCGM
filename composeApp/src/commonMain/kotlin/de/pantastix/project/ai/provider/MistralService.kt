package de.pantastix.project.ai.provider

import de.pantastix.project.ai.*
import de.pantastix.project.ai.strategy.mistral.MistralNativeStrategy
import de.pantastix.project.ai.tool.AgentTool
import de.pantastix.project.model.mistral.MistralModelList
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

class MistralService(private val client: HttpClient) : AiService {
    override val providerType = AiProviderType.MISTRAL_CLOUD
    private val baseUrl = "https://api.mistral.ai"
    private val strategy = MistralNativeStrategy()
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    override suspend fun getAvailableModels(config: AiConfig): List<AiModel> {
        val apiKey = config.apiKey ?: return emptyList()
        
        return try {
            val response: MistralModelList = client.get("$baseUrl/v1/models") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
            }.body()
            
            response.data.map { strategy.createUiModel(it) }
        } catch (e: Exception) {
            println("Error fetching Mistral models: ${e.message}")
            emptyList()
        }
    }

    override suspend fun generateResponse(
        prompt: String,
        chatHistory: List<ChatMessage>,
        config: AiConfig,
        availableTools: List<AgentTool>
    ): AiResponse {
        val apiKey = config.apiKey ?: return AiResponse.Error("No API Key")
        val strategyRequest = strategy.createRequest(prompt, chatHistory, config, availableTools)
        
        return try {
            val response: HttpResponse = client.post("$baseUrl${strategyRequest.urlSuffix ?: "/v1/chat/completions"}") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(strategyRequest.body)
                // Force non-streaming for this method
                if (strategyRequest.body is de.pantastix.project.model.mistral.MistralChatRequest) {
                    setBody(strategyRequest.body.copy(stream = false))
                }
            }
            
            val bodyText = response.bodyAsText()
            if (response.status == HttpStatusCode.OK) {
                strategy.parseResponse(bodyText)
            } else {
                println("[MISTRAL ERROR] Status: ${response.status}")
                println("[MISTRAL ERROR] Body: $bodyText")
                AiResponse.Error("Mistral API Fehler (${response.status})")
            }
        } catch (e: Exception) {
            println("[MISTRAL EXCEPTION] ${e.message}")
            e.printStackTrace()
            AiResponse.Error("Mistral Verbindungsfehler")
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
        
        // Create a fresh strategy instance per stream to ensure fresh parser state
        val requestStrategy = de.pantastix.project.ai.strategy.mistral.MistralNativeStrategy()
        val strategyRequest = requestStrategy.createRequest(prompt, chatHistory, config, availableTools)
        val buffer = StringBuilder()

        try {
            client.preparePost("$baseUrl${strategyRequest.urlSuffix ?: "/v1/chat/completions"}") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(strategyRequest.body)
                timeout {
                    requestTimeoutMillis = 60000
                    connectTimeoutMillis = 10000
                }
            }.execute { response ->
                if (response.status != HttpStatusCode.OK) {
                    val errorBody = runBlocking { response.bodyAsText() }
                    println("[MISTRAL STREAM ERROR] Status: ${response.status}")
                    println("[MISTRAL STREAM ERROR] Body: $errorBody")
                    emit(AiResponse.Error("Mistral Stream Fehler (${response.status})"))
                    return@execute
                }
                
                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break
                    if (line.isBlank()) continue
                    
                    // DEBUG: Print raw chunks
                    if (line.startsWith("data: ") && line != "data: [DONE]") {
                         // println("[MISTRAL RAW] $line") 
                    }
                    
                    val responses = requestStrategy.parseStreamChunk(line, buffer)
                    responses.forEach { emit(it) }
                }
            }
        } catch (e: Exception) {
            println("[MISTRAL STREAM EXCEPTION] ${e.message}")
            e.printStackTrace()
            emit(AiResponse.Error("Mistral Stream Unterbrochen"))
        }
    }
}
