package de.pantastix.project.ai.strategy

import de.pantastix.project.ai.AiResponse
import de.pantastix.project.ai.model.ollama.*
import de.pantastix.project.ai.tool.AgentTool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonPrimitive

class SimulatedJsonStrategy : AiWorkflowStrategy {

    override fun createRequest(
        modelId: String,
        messages: List<OllamaChatMessage>,
        tools: List<AgentTool>
    ): OllamaChatRequest {
        // 1. Inject Tool Definitions into System Prompt
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
            // No native 'tools' field here
        )
    }

    private fun buildSystemPrompt(baseSystemPrompt: String, tools: List<AgentTool>): String {
        if (tools.isEmpty()) return baseSystemPrompt
        
        val toolsDescription = tools.joinToString("\n") { 
            "- ${it.name}: ${it.description}. Parameters: ${it.parameterSchemaJson}"
        }
        
        return """
            $baseSystemPrompt
            
            You have access to the following tools:
            $toolsDescription
            
            To use a tool, output ONLY a JSON block in the following format:
            ```json
            {
              "tool": "tool_name",
              "parameters": {
                "param1": "value1"
              }
            }
            ```
            Do not provide any other text if you use a tool.
            If no tool is needed, respond normally.
        """.trimIndent()
    }

    override fun parseResponse(response: OllamaChatResponse): AiResponse {
        val content = response.message?.content ?: return AiResponse.Text("")
        
        val toolCall = parseToolCall(content)
        return if (toolCall != null) {
            AiResponse.ToolCall(toolCall.first, toolCall.second)
        } else {
            AiResponse.Text(content)
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