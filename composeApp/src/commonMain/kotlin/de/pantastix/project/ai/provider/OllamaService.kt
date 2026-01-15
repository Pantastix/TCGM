package de.pantastix.project.ai.provider

import de.pantastix.project.ai.*
import de.pantastix.project.ai.model.ollama.*
import de.pantastix.project.ai.strategy.AiWorkflowStrategy
import de.pantastix.project.ai.strategy.NativeOllamaStrategy
import de.pantastix.project.ai.strategy.Gemma3ReasoningStrategy
import de.pantastix.project.ai.strategy.SimulatedJsonStrategy
import de.pantastix.project.ai.tool.AgentTool
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

class OllamaService(private val client: HttpClient) : AiService {
    override val providerType = AiProviderType.OLLAMA_LOCAL

    private val modelGroups = listOf(
        GptOss()
    )

    override suspend fun getAvailableModels(config: AiConfig): List<AiModel> {
        val host = config.hostUrl ?: "http://localhost:11434"
        return try {
            val response: OllamaTagsResponse = client.get("$host/api/tags").body()
            response.models.mapNotNull { ollamaModel ->
                modelGroups.firstOrNull { it.matches(ollamaModel.name) }?.createAiModel(ollamaModel)
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
        val model = config.selectedModelId ?: return AiResponse.Error("No model selected")

        // Strategy Selection Factory
        val strategy: AiWorkflowStrategy = when {
            model.contains("gemma-3", ignoreCase = true) || model.contains("gemma3", ignoreCase = true) -> Gemma3ReasoningStrategy()
            model.contains("gpt-oss", ignoreCase = true) || model.contains("llama-3.1", ignoreCase = true) -> NativeOllamaStrategy()
            else -> SimulatedJsonStrategy()
        }

        val ollamaMessages = mutableListOf<OllamaChatMessage>()
        
        // Add existing history
        chatHistory.forEach {
            ollamaMessages.add(OllamaChatMessage(it.role.name.lowercase(), it.content))
        }
        
        // Add new prompt if provided and not blank
        if (prompt.isNotBlank()) {
            ollamaMessages.add(OllamaChatMessage("user", prompt))
        }

        // Delegate request creation to strategy
        val request = strategy.createRequest(model, ollamaMessages, availableTools)

        return try {
            val response: OllamaChatResponse = client.post("$host/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()

            println("--- RAW OLLAMA OUTPUT ---\n${response.message?.content}\n-------------------------")

            strategy.parseResponse(response)
        } catch (e: Exception) {
            AiResponse.Error("Ollama Error: ${e.message}")
        }
    }
}