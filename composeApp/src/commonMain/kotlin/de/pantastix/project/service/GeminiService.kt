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

            listResponse.models.mapNotNull { model ->
                val family = de.pantastix.project.ai.AiModelRegistry.resolveFamily(model.name, de.pantastix.project.ai.ModelCategory.GEMINI_CLOUD)
                if (family != null && family.filter(model.name) && model.supportedGenerationMethods.contains("generateContent")) {
                    model to family
                } else null
            }.sortedWith(Comparator { (modelA, famA), (modelB, famB) ->
                if (famA.id == famB.id && famA.modelComparator != null) {
                    famA.modelComparator.compare(modelA.name, modelB.name)
                } else {
                    // Fallback or inter-family sort (could be improved but alphabetical display name is fine for now)
                    modelA.displayName.compareTo(modelB.displayName)
                }
            }).map { it.first }
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
            
            val response = client.post("https://generativelanguage.googleapis.com/v1beta/$safeModelName:generateContent") {
                parameter("key", apiKey)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            val responseBody = response.bodyAsText()
            println("\n[AI OUTPUT] $responseBody\n")
            
            json.decodeFromString(GenerateContentResponse.serializer(), responseBody)
            
            json.decodeFromString(GenerateContentResponse.serializer(), responseBody)
        } catch (e: Exception) {
            println("Error generating content: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}