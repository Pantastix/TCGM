package de.pantastix.project.model.gemini

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ModelListResponse(
    val models: List<AiModel>
)

@Serializable
data class AiModel(
    val name: String, // e.g., "models/gemini-pro"
    val displayName: String,
    val version: String,
    val description: String? = null,
    val supportedGenerationMethods: List<String>
)

// --- Chat Request & Response Models ---

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val tools: List<Tool>? = null,
    val toolConfig: ToolConfig? = null,
    val systemInstruction: Content? = null
)

@Serializable
data class Content(
    val role: String, // "user", "model", "function"
    val parts: List<Part>
)

@Serializable
data class Part(
    val text: String? = null,
    val functionCall: FunctionCall? = null,
    val functionResponse: FunctionResponse? = null
)

@Serializable
data class FunctionCall(
    val name: String,
    val args: JsonObject? = null
)

@Serializable
data class FunctionResponse(
    val name: String,
    val response: JsonObject
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null,
    val promptFeedback: PromptFeedback? = null
)

@Serializable
data class PromptFeedback(
    val blockReason: String? = null,
    val safetyRatings: List<SafetyRating>? = null
)

@Serializable
data class SafetyRating(
    val category: String,
    val probability: String
)

@Serializable
data class Candidate(
    val content: Content,
    val finishReason: String? = null
)

// --- Tool Definitions ---

@Serializable
data class Tool(
    val functionDeclarations: List<FunctionDeclaration>
)

@Serializable
data class FunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: Schema? = null
)

@Serializable
data class Schema(
    val type: String, // "OBJECT", "STRING", "INTEGER", etc.
    val properties: Map<String, Schema>? = null,
    val required: List<String>? = null,
    val description: String? = null,
    val enum: List<String>? = null
)

@Serializable
data class ToolConfig(
    val functionCallingConfig: FunctionCallingConfig? = null
)

@Serializable
data class FunctionCallingConfig(
    val mode: String // "AUTO", "ANY", "NONE"
)