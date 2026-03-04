package de.pantastix.project.model.mistral

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class MistralModelList(
    val data: List<MistralModelCard>
)

@Serializable
data class MistralModelCard(
    val id: String,
    val capabilities: MistralCapabilities? = null,
    val name: String? = null,
    val description: String? = null
)

@Serializable
data class MistralCapabilities(
    @SerialName("completion_chat") val completionChat: Boolean = false,
    @SerialName("function_calling") val functionCalling: Boolean = false,
    val vision: Boolean = false
)

@Serializable
data class MistralChatRequest(
    val model: String,
    val messages: List<MistralMessage>,
    val temperature: Float? = null,
    @SerialName("top_p") val topP: Float? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val stream: Boolean = false,
    @SerialName("safe_prompt") val safePrompt: Boolean = false,
    @SerialName("random_seed") val randomSeed: Int? = null,
    val tools: List<MistralTool>? = null,
    @SerialName("tool_choice") val toolChoice: String? = null,
    @SerialName("response_format") val responseFormat: MistralResponseFormat? = null
)

@Serializable
data class MistralMessage(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<MistralToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null
)

@Serializable
data class MistralTool(
    val type: String = "function",
    val function: MistralFunction
)

@Serializable
data class MistralFunction(
    val name: String,
    val description: String? = null,
    val parameters: JsonObject
)

@Serializable
data class MistralToolCall(
    val id: String,
    val type: String = "function",
    val function: MistralFunctionCall
)

@Serializable
data class MistralFunctionCall(
    val name: String,
    val arguments: String // JSON string
)

@Serializable
data class MistralResponseFormat(
    val type: String // "text" or "json_object"
)

@Serializable
data class MistralChatResponse(
    val id: String,
    val model: String,
    val choices: List<MistralChoice>,
    val usage: MistralUsage
)

@Serializable
data class MistralChoice(
    val index: Int,
    val message: MistralMessage,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class MistralUsage(
    @SerialName("prompt_tokens") val promptTokens: Int,
    @SerialName("completion_tokens") val completionTokens: Int,
    @SerialName("total_tokens") val totalTokens: Int
)

@Serializable
data class MistralChunkResponse(
    val id: String,
    val model: String,
    val choices: List<MistralChunkChoice>
)

@Serializable
data class MistralChunkChoice(
    val index: Int,
    val delta: MistralChunkDelta,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class MistralChunkDelta(
    val role: String? = null,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<MistralToolCall>? = null
)
