package de.pantastix.project.ai.strategy.gemini

import de.pantastix.project.ai.*
import de.pantastix.project.ai.strategy.AiWorkflowStrategy
import de.pantastix.project.ai.strategy.StrategyRequest
import de.pantastix.project.ai.tool.AgentTool
import de.pantastix.project.model.gemini.*
import de.pantastix.project.model.gemini.AiModel as ApiModel
import kotlinx.serialization.json.*

/**
 * Strategy for Gemma 3 models which do not support native function calling.
 * We simulate reasoning and tool usage via prompt engineering (XML tags).
 */
class Gemma3ReasoningStrategy : AiWorkflowStrategy {
    override val providerType = AiProviderType.GEMINI_CLOUD
    // Matches "models/gemma-3..." or "gemma-3..."
    override val modelIdRegex = Regex("""^(models/)?gemma[-:]?3.*""", RegexOption.IGNORE_CASE)
    
    private val json = Json {
        ignoreUnknownKeys = true 
        isLenient = true
    }

    override fun createUiModel(apiModelData: Any): de.pantastix.project.ai.AiModel {
        val apiModel = apiModelData as ApiModel
        val name = apiModel.name
        val sizeRegex = Regex("""(\d+b)""", RegexOption.IGNORE_CASE)
        val match = sizeRegex.find(name)
        val size = match?.value?.lowercase()

        val displayName = if (size != null) "Gemma 3 ($size)" else apiModel.displayName.ifEmpty { "Gemma 3" }

        return de.pantastix.project.ai.AiModel(
            id = name,
            displayName = displayName,
            provider = AiProviderType.GEMINI_CLOUD,
            capabilities = setOf(AiCapability.TEXT_GENERATION) // Technically only text, we simulate tools
        )
    }

    override fun createRequest(
        prompt: String,
        chatHistory: List<ChatMessage>,
        config: AiConfig,
        availableTools: List<AgentTool>
    ): StrategyRequest {
        val contents = mutableListOf<Content>()

        // 1. Convert History
        contents.addAll(chatHistory.map { msg ->
            val role = when (msg.role) {
                ChatRole.USER, ChatRole.TOOL -> "user"
                else -> "model"
            }
            // For Gemma, tool outputs are just user messages
            val text = if (msg.role == ChatRole.TOOL) "Tool Result: ${msg.content}" else msg.content
            Content(role = role, parts = listOf(Part(text = text)))
        })

        // 2. Add current prompt
        if (prompt.isNotBlank()) {
            contents.add(Content(role = "user", parts = listOf(Part(text = prompt))))
        }

        // 3. Inject System Prompt (Simulated via first user message)
        val toolsDescription = if (availableTools.isNotEmpty()) {
            availableTools.joinToString("\n") { "- ${it.name}: ${it.description}. Parameters: ${it.parameterSchemaJson}" }
        } else "None"

        val instructionText = """
            You are a helpful assistant for a Pokémon TCG application.
            
            AVAILABLE TOOLS:
            $toolsDescription
            
            PROTOCOL:
            1. THOUGHT PROCESS: Start every response with a <think>...</think> block. Analyze the user's request step-by-step.
            2. ACTION:
               - If you need data (prices, inventory, stats), output a JSON tool call inside <tool>...</tool> tags.
               - If you can answer directly, output the final text answer after the </think> tag.
            
            FORMATS:
            
            [Tool Call]
            <think>
            Reasoning here...
            </think>
            <tool>
            { "tool": "tool_name", "parameters": { "param": "value" } }
            </tool>
            
            [Text Response]
            <think>
            Reasoning here...
            </think>
            Your final answer here.
        """.trimIndent()

        // Prepend instructions
        contents.add(0, Content(role = "user", parts = listOf(Part(text = instructionText))))
        contents.add(1, Content(role = "model", parts = listOf(Part(text = "Understood. I will use <think> for reasoning and <tool> for function calls."))))

        val request = GenerateContentRequest(
            contents = contents,
            tools = null, // No native tools for Gemma 3
            generationConfig = null
        )

        return StrategyRequest(
            body = request,
            apiVersion = "v1beta"
        )
    }

    override fun parseResponse(responseBody: String): AiResponse {
        try {
            val response = json.decodeFromString<GenerateContentResponse>(responseBody)
            val text = response.candidates?.firstOrNull()?.content?.parts?.joinToString("") { it.text ?: "" } ?: ""
            return parseGemmaText(text)
        } catch (e: Exception) {
            return AiResponse.Error("Parsing Error: ${e.message}")
        }
    }

    // Reuse the parsing logic from the old service, but cleaner
    private fun parseGemmaText(fullText: String): AiResponse {
        // Parse <think>
        val thinkRegex = Regex("""<think>(.*?)</think>""", RegexOption.DOT_MATCHES_ALL)
        val thinkMatch = thinkRegex.find(fullText)
        val thought = thinkMatch?.groupValues?.get(1)?.trim()
        val contentWithoutThought = fullText.replace(thinkRegex, "").trim()

        // Parse <tool>
        val toolRegex = Regex("""<tool>\s*(\{[\s\S]*"tool"[\s\S]*\})\s*</tool>""")
        val toolMatch = toolRegex.find(contentWithoutThought)

        if (toolMatch != null) {
            val jsonString = toolMatch.groupValues[1]
            return try {
                val jsonElement = json.parseToJsonElement(jsonString) as JsonObject
                val toolName = jsonElement["tool"]?.jsonPrimitive?.content
                val params = jsonElement["parameters"]?.let { it as? JsonObject }?.mapValues {
                    if (it.value is JsonPrimitive) it.value.jsonPrimitive.content else it.value.toString()
                } ?: emptyMap()

                if (toolName != null) {
                    AiResponse.ToolCall(toolName, params, thought)
                } else {
                    AiResponse.Text(contentWithoutThought, thought)
                }
            } catch (e: Exception) {
                AiResponse.Text(contentWithoutThought, thought)
            }
        }
        
        return AiResponse.Text(contentWithoutThought, thought)
    }

    // Stateful parsing for streaming
    private var state = ParserState.CONTENT
    private var totalText = ""
    private var totalThought = ""
    private var foundToolCall: AiResponse.ToolCall? = null

    private enum class ParserState { CONTENT, THINKING, TOOL }

    override fun parseStreamChunk(chunk: String, buffer: StringBuilder): List<AiResponse> {
        // chunk is expected to be a data: line from SSE
        if (!chunk.startsWith("data: ")) return emptyList()
        val jsonStr = chunk.substring(6).trim()
        if (jsonStr == "[DONE]") return emptyList()

        val events = mutableListOf<AiResponse>()
        
        try {
            val response = json.decodeFromString<GenerateContentResponse>(jsonStr)
            val newText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: return emptyList()
            
            buffer.append(newText)
            
            var loop = true
            while (loop) {
                loop = false
                val currentBuffer = buffer.toString()

                when (state) {
                    ParserState.CONTENT -> {
                        val thinkIndex = currentBuffer.indexOf("<think>")
                        val toolIndex = currentBuffer.indexOf("<tool>")

                        val firstTagIndex = when {
                            thinkIndex != -1 && toolIndex != -1 -> minOf(thinkIndex, toolIndex)
                            thinkIndex != -1 -> thinkIndex
                            toolIndex != -1 -> toolIndex
                            else -> -1
                        }

                        if (firstTagIndex != -1) {
                            if (firstTagIndex > 0) {
                                val content = currentBuffer.substring(0, firstTagIndex)
                                totalText += content
                                events.add(AiResponse.Text(totalText, totalThought.takeIf { it.isNotEmpty() }))
                                buffer.delete(0, firstTagIndex)
                            }

                            if (thinkIndex == firstTagIndex) {
                                state = ParserState.THINKING
                                buffer.delete(0, 7) // <think>
                            } else {
                                state = ParserState.TOOL
                                buffer.delete(0, 6) // <tool>
                            }
                            loop = true
                        } else {
                            // Safe buffer logic
                             val safeLength = currentBuffer.lastIndexOf('<')
                             if (safeLength != -1 && safeLength > currentBuffer.length - 7) {
                                 // Wait for tag completion
                                 if (safeLength > 0) {
                                     val content = currentBuffer.substring(0, safeLength)
                                     totalText += content
                                     events.add(AiResponse.Text(totalText, totalThought.takeIf { it.isNotEmpty() }))
                                     buffer.delete(0, safeLength)
                                 }
                             } else {
                                 if (buffer.isNotEmpty()) {
                                     totalText += buffer.toString()
                                     events.add(AiResponse.Text(totalText, totalThought.takeIf { it.isNotEmpty() }))
                                     buffer.clear()
                                 }
                             }
                        }
                    }
                    ParserState.THINKING -> {
                        val closeIndex = currentBuffer.indexOf("</think>")
                        if (closeIndex != -1) {
                            val thoughtContent = currentBuffer.substring(0, closeIndex)
                            totalThought += thoughtContent
                            events.add(AiResponse.Text(totalText, totalThought))
                            buffer.delete(0, closeIndex + 8)
                            state = ParserState.CONTENT
                            loop = true
                        } else {
                             // Wait until we have </think> or buffer gets too large
                        }
                    }
                    ParserState.TOOL -> {
                         val closeIndex = currentBuffer.indexOf("</tool>")
                         if (closeIndex != -1) {
                             val toolJsonStr = currentBuffer.substring(0, closeIndex)
                             // Attempt to parse tool
                             try {
                                 val jsonElement = json.parseToJsonElement(toolJsonStr) as JsonObject
                                 val toolName = jsonElement["tool"]?.jsonPrimitive?.content
                                 val params = jsonElement["parameters"]?.let { it as? JsonObject }?.mapValues {
                                     if (it.value is JsonPrimitive) it.value.jsonPrimitive.content else it.value.toString()
                                 } ?: emptyMap()
                                 
                                 if (toolName != null) {
                                     foundToolCall = AiResponse.ToolCall(toolName, params, totalThought)
                                     events.add(foundToolCall!!)
                                 }
                             } catch (e: Exception) {
                                 // Ignore malformed tool calls in stream
                             }
                             
                             buffer.delete(0, closeIndex + 7)
                             state = ParserState.CONTENT
                             loop = true
                         }
                    }
                }
            }
        } catch (e: Exception) {
            // ignore chunk errors
        }
        
        return events
    }
}