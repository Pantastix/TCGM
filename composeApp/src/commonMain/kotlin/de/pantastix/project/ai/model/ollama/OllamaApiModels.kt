package de.pantastix.project.ai.model.ollama

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class OllamaTagsResponse(
    val models: List<OllamaModel>
)

@Serializable
data class OllamaModel(
    val name: String,
    val model: String,
    val details: OllamaModelDetails? = null
)

@Serializable
data class OllamaModelDetails(
    val format: String? = null,
    val family: String? = null,
    val families: List<String>? = null,
    val parameter_size: String? = null,
    val quantization_level: String? = null
)

@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaChatMessage>,
    val stream: Boolean = false,
    val format: String? = null, // "json" if we want to enforce it
    val tools: List<OllamaTool>? = null,
    val options: Map<String, JsonElement>? = null
)

@Serializable
data class OllamaChatMessage(
    val role: String,
    val content: String,
    val tool_calls: List<OllamaToolCall>? = null
)

@Serializable
data class OllamaTool(
    val type: String = "function",
    val function: OllamaFunction
)

@Serializable
data class OllamaFunction(
    val name: String,
    val description: String,
    val parameters: Map<String, JsonElement> // JSON Schema
)

@Serializable
data class OllamaToolCall(
    val function: OllamaToolCallFunction
)

@Serializable
data class OllamaToolCallFunction(
    val name: String,
    val arguments: Map<String, JsonElement>
)

@Serializable
data class OllamaChatResponse(
    val model: String? = null,
    val message: OllamaChatMessage? = null,
    val done: Boolean = false,
    val error: String? = null
)
