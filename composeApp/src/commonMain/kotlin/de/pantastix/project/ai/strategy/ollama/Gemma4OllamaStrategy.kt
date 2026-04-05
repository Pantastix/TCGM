package de.pantastix.project.ai.strategy.ollama

import de.pantastix.project.ai.*
import de.pantastix.project.ai.strategy.AiWorkflowStrategy
import de.pantastix.project.ai.strategy.StrategyRequest
import de.pantastix.project.ai.tool.AgentTool
import kotlinx.serialization.json.*

/**
 * Dedicated Strategy for Gemma 4 models running locally (Ollama).
 * Uses the native "Markup Protocol" with tokens:
 * - <|turn>...<turn|> for roles
 * - <|channel>thought...<channel|> for reasoning
 * - <|tool_call>call:name{args}<tool_call|> for tools
 */
class Gemma4OllamaStrategy : AiWorkflowStrategy {
    override val providerType = AiProviderType.OLLAMA_LOCAL
    override val modelIdRegex = Regex("""gemma-4.*""", RegexOption.IGNORE_CASE)

    private val json = Json { ignoreUnknownKeys = true }

    override fun createUiModel(apiModelData: Any): de.pantastix.project.ai.AiModel {
        // apiModelData is usually the model name string from Ollama
        val name = apiModelData.toString()
        return de.pantastix.project.ai.AiModel(
            id = name,
            displayName = "Gemma 4 (Local)",
            provider = AiProviderType.OLLAMA_LOCAL,
            capabilities = setOf(AiCapability.NATIVE_TOOL_CALLING, AiCapability.TEXT_GENERATION)
        )
    }

    override fun createRequest(
        prompt: String,
        chatHistory: List<ChatMessage>,
        config: AiConfig,
        availableTools: List<AgentTool>
    ): StrategyRequest {
        val fullPrompt = buildString {
            // 1. System Prompt with Tool Declarations
            append("<|turn>system\n")
            append(config.systemInstruction ?: "You are a helpful assistant.")
            append("\n<|think|>\n") // Activate thinking
            
            if (availableTools.isNotEmpty()) {
                append("\nAVAILABLE TOOLS:\n")
                availableTools.forEach { tool ->
                    append("<|tool>declaration:${tool.name}${tool.parameterSchemaJson}<tool|>\n")
                }
            }
            append("<turn|>\n")

            // 2. Chat History
            chatHistory.forEach { msg ->
                val role = when (msg.role) {
                    ChatRole.USER -> "user"
                    ChatRole.ASSISTANT -> "model"
                    ChatRole.TOOL -> "user" // Tool responses are feedback to the model
                    ChatRole.SYSTEM -> "system"
                }
                append("<|turn>$role\n")
                
                // Add Thought if present
                if (!msg.thought.isNullOrBlank()) {
                    append("<|channel>thought\n${msg.thought}<channel|>\n")
                }
                
                if (msg.role == ChatRole.TOOL) {
                    append("<|tool_response>${msg.content}<tool_response|>\n")
                } else if (msg.toolCall != null) {
                    val argsJson = mapToJsonObject(msg.toolCall.args).toString()
                    append("<|tool_call>call:${msg.toolCall.name}$argsJson<tool_call|>\n")
                } else {
                    append(msg.content)
                }
                append("<turn|>\n")
            }

            // 3. Current User Prompt
            if (prompt.isNotBlank()) {
                append("<|turn>user\n$prompt<turn|>\n")
            }
            
            // 4. Trigger Model Response
            append("<|turn>model\n")
        }

        // We wrap this in a simple request body that the OllamaService understands
        // Usually, for local raw models, we send just the 'prompt' field
        val body = buildJsonObject {
            put("model", config.selectedModelId ?: "gemma-4")
            put("prompt", fullPrompt)
            put("stream", true)
            put("raw", true) // Tells Ollama NOT to apply its own template
            
            // Recommended sampling for Gemma 4
            put("options", buildJsonObject {
                put("temperature", 1.0)
                put("top_p", 0.95)
                put("top_k", 64)
            })
        }

        return StrategyRequest(body = body, apiVersion = "native-tokens")
    }

    override fun parseResponse(responseBody: String): AiResponse {
        // Note: For Ollama, responses are usually JSON objects with a 'response' or 'content' field
        return try {
            val element = json.parseToJsonElement(responseBody).jsonObject
            val text = element["response"]?.jsonPrimitive?.content ?: ""
            parseGemma4Tokens(text)
        } catch (e: Exception) {
            AiResponse.Error("Parse Error: ${e.message}")
        }
    }

    private var isThinking = false
    private val thoughtStart = "<|channel>thought"
    private val thoughtEnd = "<channel|>"
    private val toolCallStart = "<|tool_call>call:"
    private val toolCallEnd = "<tool_call|>"

    override fun parseStreamChunk(chunk: String, buffer: StringBuilder): List<AiResponse> {
        return try {
            val element = json.parseToJsonElement(chunk).jsonObject
            val delta = element["response"]?.jsonPrimitive?.content ?: ""
            buffer.append(delta)
            
            val events = mutableListOf<AiResponse>()
            var current = buffer.toString()
            
            var loop = true
            while (loop) {
                loop = false
                current = buffer.toString()
                
                if (!isThinking) {
                    val tStartIdx = current.indexOf(thoughtStart)
                    val tcStartIdx = current.indexOf(toolCallStart)
                    
                    // HEURISTIC: If we see a tool call start but NO thought tag,
                    // any text currently in the buffer before the tool call is likely reasoning.
                    if (tcStartIdx != -1 && tStartIdx == -1) {
                         val textBefore = current.substring(0, tcStartIdx).trim()
                         if (textBefore.isNotBlank()) {
                             events.add(AiResponse.Text(content = "", thought = textBefore))
                         }
                         
                         val endIdx = current.indexOf(toolCallEnd)
                         if (endIdx != -1) {
                             val fullCall = current.substring(tcStartIdx + toolCallStart.length, endIdx)
                             val toolName = fullCall.substringBefore('{').trim()
                             val paramsJson = "{" + fullCall.substringAfter('{')
                             val params = try {
                                 json.decodeFromString<Map<String, Any?>>(paramsJson)
                             } catch (e: Exception) { emptyMap() }
                             
                             events.add(AiResponse.ToolCall(toolName, params))
                             buffer.delete(0, endIdx + toolCallEnd.length)
                             loop = true
                             continue
                         } else {
                             return events // Wait for more tool call data
                         }
                    }

                    val firstIdx = when {
                        tStartIdx != -1 && tcStartIdx != -1 -> minOf(tStartIdx, tcStartIdx)
                        tStartIdx != -1 -> tStartIdx
                        tcStartIdx != -1 -> tcStartIdx
                        else -> -1
                    }
                    
                    if (firstIdx != -1) {
                        if (firstIdx > 0) {
                            events.add(AiResponse.Text(current.substring(0, firstIdx)))
                            buffer.delete(0, firstIdx)
                        }
                        
                        if (tStartIdx == firstIdx) {
                            isThinking = true
                            buffer.delete(0, thoughtStart.length)
                            if (buffer.startsWith("\n")) buffer.delete(0, 1)
                        } else {
                            val endIdx = current.indexOf(toolCallEnd)
                            if (endIdx != -1) {
                                val fullCall = current.substring(tcStartIdx + toolCallStart.length, endIdx)
                                val toolName = fullCall.substringBefore('{').trim()
                                val paramsJson = "{" + fullCall.substringAfter('{')
                                val params = try {
                                    json.decodeFromString<Map<String, Any?>>(paramsJson)
                                } catch (e: Exception) { emptyMap() }
                                
                                events.add(AiResponse.ToolCall(toolName, params))
                                buffer.delete(0, endIdx + toolCallEnd.length)
                            } else {
                                return events
                            }
                        }
                        loop = true
                    } else {
                        val lastBracket = current.lastIndexOf('<')
                        if (lastBracket != -1 && lastBracket > current.length - 20) {
                            if (lastBracket > 0) {
                                events.add(AiResponse.Text(current.substring(0, lastBracket)))
                                buffer.delete(0, lastBracket)
                            }
                        } else {
                            if (buffer.isNotEmpty()) {
                                events.add(AiResponse.Text(buffer.toString()))
                                buffer.setLength(0)
                            }
                        }
                    }
                } else {
                    val tEndIdx = current.indexOf(thoughtEnd)
                    if (tEndIdx != -1) {
                        events.add(AiResponse.Text("", current.substring(0, tEndIdx)))
                        isThinking = false
                        buffer.delete(0, tEndIdx + thoughtEnd.length)
                        loop = true
                    } else {
                        val lastBracket = current.lastIndexOf('<')
                        if (lastBracket != -1 && lastBracket > current.length - 20) {
                            if (lastBracket > 0) {
                                events.add(AiResponse.Text("", current.substring(0, lastBracket)))
                                buffer.delete(0, lastBracket)
                            }
                        } else {
                            if (buffer.isNotEmpty()) {
                                events.add(AiResponse.Text("", buffer.toString()))
                                buffer.setLength(0)
                            }
                        }
                    }
                }
            }
            events
        } catch (e: Exception) { emptyList() }
    }

    private fun parseGemma4Tokens(text: String): AiResponse {
        val (thought, content) = extractThought(text)
        
        val tcMatch = Regex("""<\|tool_call>call:(.*?)\{(.*?)<tool_call\|>""").find(content)
        if (tcMatch != null) {
            val toolName = tcMatch.groupValues[1].trim()
            val paramsJson = "{" + tcMatch.groupValues[2]
            val params = try {
                json.decodeFromString<Map<String, Any?>>(paramsJson)
            } catch (e: Exception) { emptyMap() }
            return AiResponse.ToolCall(toolName, params, thought)
        }
        
        return AiResponse.Text(content.replace(Regex("""<\|turn>.*?<turn\|>"""), "").trim(), thought)
    }

    private fun extractThought(text: String): Pair<String?, String> {
        val match = Regex("""<\|channel>thought\n?(.*?)<channel\|>""", RegexOption.DOT_MATCHES_ALL).find(text)
        val thought = match?.groupValues?.get(1)?.trim()
        val rest = text.replace(Regex("""<\|channel>thought.*?<channel\|>""", RegexOption.DOT_MATCHES_ALL), "").trim()
        return thought to rest
    }

    private fun mapToJsonObject(map: Map<String, Any?>): JsonObject {
        return buildJsonObject {
            map.forEach { (k, v) ->
                when (v) {
                    is String -> put(k, v)
                    is Number -> put(k, v)
                    is Boolean -> put(k, v)
                    is JsonElement -> put(k, v)
                    null -> put(k, JsonNull)
                    else -> put(k, v.toString())
                }
            }
        }
    }
}
