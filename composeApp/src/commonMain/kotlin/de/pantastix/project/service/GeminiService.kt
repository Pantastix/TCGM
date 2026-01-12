package de.pantastix.project.service

import de.pantastix.project.model.gemini.AiModel
import de.pantastix.project.model.gemini.GenerateContentRequest
import de.pantastix.project.model.gemini.GenerateContentResponse
import de.pantastix.project.model.gemini.ModelListResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.plugin
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json

class GeminiService(private val client: HttpClient) {

    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true 
    }

    suspend fun getAvailableChatModels(apiKey: String): List<AiModel> {
        return try {
            val response = client.get("https://generativelanguage.googleapis.com/v1beta/models") {
                parameter("key", apiKey)
            }
            val listResponse = response.body<ModelListResponse>()

            // Regex für:
            // 1. Gemini: "gemini" + Version (z.B. -1.5, -2.0) + Typ (-flash, -pro) + Optional (-preview/latest)
            // 2. Gemma: "gemma-3" (und Varianten)
            val allowedModelsRegex = Regex("^(models/)?(gemini-\\d+(\\.\\d+)?-(flash|pro)(-[a-zA-Z0-9]+)?|gemma-3.*)")

            listResponse.models.filter { model ->
                val name = model.name.lowercase()
                allowedModelsRegex.matches(name) &&
                model.supportedGenerationMethods.contains("generateContent")
            }.sortedByDescending { it.displayName }
        } catch (e: Exception) {
            println("Error fetching models: ${e.message}")
            emptyList()
        }
    }

    suspend fun generateContent(
        apiKey: String,
        modelName: String,
        request: GenerateContentRequest
    ): GenerateContentResponse? {
        return try {
            // Ensure modelName doesn't have double "models/" prefix if passed incorrectly
            val safeModelName = if (modelName.startsWith("models/")) modelName else "models/$modelName"
            
            // Log Request
            try {
                val requestStr = json.encodeToString(GenerateContentRequest.serializer(), request)
                println("DEBUG GEMINI REQUEST URL: https://generativelanguage.googleapis.com/v1beta/$safeModelName:generateContent")
                println("DEBUG GEMINI REQUEST BODY: $requestStr")
            } catch (e: Exception) {
                println("DEBUG GEMINI: Failed to serialize request for logging: ${e.message}")
            }

            val response = client.post("https://generativelanguage.googleapis.com/v1beta/$safeModelName:generateContent") {
                parameter("key", apiKey)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            val responseBody = response.bodyAsText()
            println("DEBUG GEMINI RESPONSE BODY: $responseBody")
            
            json.decodeFromString(GenerateContentResponse.serializer(), responseBody)
        } catch (e: Exception) {
            println("Error generating content: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}