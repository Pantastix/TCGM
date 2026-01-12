package de.pantastix.project.ai.strategy

import de.pantastix.project.ai.AiResponse
import de.pantastix.project.ai.model.*
import de.pantastix.project.ai.tool.AgentTool
import kotlinx.serialization.json.*

class NativeOllamaStrategy : AiWorkflowStrategy {

    override fun createRequest(
        modelId: String,
        messages: List<OllamaChatMessage>,
        tools: List<AgentTool>
    ): OllamaChatRequest {
        val ollamaTools = tools.map { tool ->
            OllamaTool(
                function = OllamaFunction(
                    name = tool.name,
                    description = tool.description,
                    parameters = convertToSchema(tool.parameterSchemaJson)
                )
            )
        }

        return OllamaChatRequest(
            model = modelId,
            messages = messages,
            stream = false,
            tools = if (ollamaTools.isNotEmpty()) ollamaTools else null
        )
    }

    override fun parseResponse(response: OllamaChatResponse): AiResponse {
        val msg = response.message
        if (msg?.tool_calls != null && msg.tool_calls.isNotEmpty()) {
            val call = msg.tool_calls.first()
            
            // Convert JsonElement arguments to Map<String, Any?> for AiResponse
            // We assume arguments are simple primitives for now
            val args = call.function.arguments.mapValues { (_, v) ->
                if (v is JsonPrimitive) v.content else v.toString()
            }
            
            return AiResponse.ToolCall(
                toolName = call.function.name,
                parameters = args
            )
        }

        return AiResponse.Text(msg?.content ?: "")
    }

    private fun convertToSchema(simpleJson: String): JsonObject {
        return try {
            val simpleObj = Json.decodeFromString<JsonObject>(simpleJson)
            val properties = buildJsonObject {
                simpleObj.entries.forEach { (key, value) ->
                    val desc = if (value is JsonPrimitive) value.content else value.toString()
                    put(key, buildJsonObject {
                        put("type", "string")
                        put("description", desc)
                    })
                }
            }
            
            buildJsonObject {
                put("type", "object")
                put("properties", properties)
                putJsonArray("required") {
                    simpleObj.keys.forEach { add(it) }
                }
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {})
            }
        }
    }
}