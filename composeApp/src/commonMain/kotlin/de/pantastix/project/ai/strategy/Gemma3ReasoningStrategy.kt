package de.pantastix.project.ai.strategy

import de.pantastix.project.ai.AiResponse
import de.pantastix.project.ai.model.OllamaChatMessage
import de.pantastix.project.ai.model.OllamaChatRequest
import de.pantastix.project.ai.model.OllamaChatResponse
import de.pantastix.project.ai.tool.AgentTool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonPrimitive

class Gemma3ReasoningStrategy : AiWorkflowStrategy {

    override fun createRequest(
        modelId: String,
        messages: List<OllamaChatMessage>,
        tools: List<AgentTool>
    ): OllamaChatRequest {
        // 1. Inject Tool Definitions + Reasoning Instruction into System Prompt
        val systemMessageIndex = messages.indexOfFirst { it.role == "system" }
        val baseSystemPrompt = if (systemMessageIndex != -1) messages[systemMessageIndex].content else "You are a helpful assistant."
        
        val enhancedSystemPrompt = buildSystemPrompt(baseSystemPrompt, tools)
        
        val newMessages = messages.toMutableList()
        if (systemMessageIndex != -1) {
            newMessages[systemMessageIndex] = OllamaChatMessage("system", enhancedSystemPrompt)
        } else {
            newMessages.add(0, OllamaChatMessage("system", enhancedSystemPrompt))
        }

        return OllamaChatRequest(
            model = modelId,
            messages = newMessages,
            stream = false
            // No native 'tools'
        )
    }

    private fun buildSystemPrompt(baseSystemPrompt: String, tools: List<AgentTool>): String {
        val toolsDescription = if (tools.isNotEmpty()) {
            tools.joinToString("\n") { "- ${it.name}: ${it.description}. Parameters: ${it.parameterSchemaJson}" }
        } else "None"
        
        return """
            $baseSystemPrompt
            
            AVAILABLE TOOLS:
            $toolsDescription
            
            INSTRUCTIONS:
            1. First, you MUST think step-by-step about the user's request. Output your thoughts in a <think>...</think> block.
            2. Analyze if you need to use a tool to answer the request.
            3. If you need a tool, output a JSON block in the format:
            ```json
            { "tool": "tool_name", "parameters": { ... } }
            ```
            4. If no tool is needed, provide your final answer directly after the thought block. 
            
            Example:
            User: "How much is Charizard worth?"
            Model:
            <think>
            The user is asking for the value of a card. I should check the inventory database for 'Charizard' to find its price.
            The 'search_cards' tool seems appropriate for this.
            </think>
            ```json
            { "tool": "search_cards", "parameters": { "query": "Charizard" } }
            ```
        """.trimIndent()
    }

    override fun parseResponse(response: OllamaChatResponse): AiResponse {
        val content = response.message?.content ?: return AiResponse.Text("")
        
        // Extract <think> block
        val thinkRegex = """<think>(.*?)</think>""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val thinkMatch = thinkRegex.find(content)
        val thought = thinkMatch?.groupValues?.get(1)?.trim()
        
        // Remove <think> block from content for further processing
        val contentWithoutThought = content.replace(thinkRegex, "").trim()
        
        val toolCall = parseToolCall(contentWithoutThought)
        return if (toolCall != null) {
            AiResponse.ToolCall(toolCall.first, toolCall.second, thought)
        } else {
            AiResponse.Text(contentWithoutThought, thought)
        }
    }

    private fun parseToolCall(content: String): Pair<String, Map<String, Any?>>? {
        val regex = """```json\s*(\{.*?\})\s*```""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val match = regex.find(content) ?: return null
        
        return try {
            val jsonString = match.groupValues[1]
            val json = Json.decodeFromString<JsonObject>(jsonString)
            val toolName = json["tool"]?.jsonPrimitive?.content ?: return null
            val params = json["parameters"]?.jsonObject?.let { obj ->
                obj.mapValues { (_, value) -> 
                    when (value) {
                        is JsonPrimitive -> value.content
                        else -> value.toString()
                    }
                }
            } ?: emptyMap()
            
            toolName to params
        } catch (e: Exception) {
            null
        }
    }
}
