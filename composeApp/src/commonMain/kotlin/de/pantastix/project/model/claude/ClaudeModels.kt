package de.pantastix.project.model.claude

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class ClaudeModelList(
    val data: List<ClaudeModelInfo>
)

@Serializable
data class ClaudeModelInfo(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class ClaudeMessageRequest(
    val model: String,
    val messages: List<ClaudeMessage>,
    val system: String? = null,
    @SerialName("max_tokens") val maxTokens: Int = 4096,
    val metadata: JsonObject? = null,
    @SerialName("stop_sequences") val stopSequences: List<String>? = null,
    val stream: Boolean = false,
    val temperature: Float? = null,
    @SerialName("top_p") val topP: Float? = null,
    @SerialName("top_k") val topK: Int? = null,
    val tools: List<ClaudeTool>? = null,
    @SerialName("tool_choice") val toolChoice: JsonElement? = null,
    val thinking: ClaudeThinkingConfig? = null
)

@Serializable
data class ClaudeThinkingConfig(
    val type: String = "enabled", // "enabled" or "disabled"
    @SerialName("budget_tokens") val budgetTokens: Int
)

@Serializable
data class ClaudeMessage(
    val role: String, // "user" or "assistant"
    val content: List<ClaudeContentBlock>
)

@Serializable
data class ClaudeContentBlock(
    val type: String, // "text", "image", "tool_use", "tool_result", "thinking"
    val text: String? = null,
    val id: String? = null,
    val name: String? = null,
    val input: JsonObject? = null,
    @SerialName("tool_use_id") val toolUseId: String? = null,
    val content: List<ClaudeContentBlock>? = null, // for tool_result or thinking
    val is_error: Boolean? = null,
    val signature: String? = null,
    val thinking: String? = null
)

@Serializable
data class ClaudeTool(
    val name: String,
    val description: String? = null,
    val input_schema: JsonObject
)

@Serializable
data class ClaudeMessageResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<ClaudeContentBlock>,
    val model: String,
    @SerialName("stop_reason") val stopReason: String?,
    @SerialName("stop_sequence") val stopSequence: String?,
    val usage: ClaudeUsage
)

@Serializable
data class ClaudeUsage(
    @SerialName("input_tokens") val inputTokens: Int,
    @SerialName("output_tokens") val outputTokens: Int
)

// --- Streaming Events ---

@Serializable
data class ClaudeStreamEvent(
    val type: String,
    val index: Int? = null,
    @SerialName("content_block") val contentBlock: ClaudeContentBlock? = null,
    val delta: ClaudeDelta? = null,
    val message: ClaudeMessageResponse? = null
)

@Serializable
data class ClaudeDelta(
    val type: String? = null,
    val text: String? = null,
    val partial_json: String? = null,
    @SerialName("stop_reason") val stopReason: String? = null,
    val thinking: String? = null,
    val signature: String? = null
)
