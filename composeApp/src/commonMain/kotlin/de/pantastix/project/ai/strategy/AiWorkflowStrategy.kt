package de.pantastix.project.ai.strategy

import de.pantastix.project.ai.AiResponse
import de.pantastix.project.ai.model.ollama.*
import de.pantastix.project.ai.tool.AgentTool

interface AiWorkflowStrategy {
    fun createRequest(
        modelId: String,
        messages: List<OllamaChatMessage>,
        tools: List<AgentTool>
    ): OllamaChatRequest

    fun parseResponse(response: OllamaChatResponse): AiResponse
}