package de.pantastix.project.ai.provider

import de.pantastix.project.ai.*
import de.pantastix.project.ai.tool.AgentTool
import io.ktor.client.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ClaudeService(private val client: HttpClient) : AiService {
    override val providerType = AiProviderType.CLAUDE_CLOUD

    override suspend fun getAvailableModels(config: AiConfig): List<AiModel> {
        return emptyList()
    }

    override suspend fun generateResponse(
        prompt: String,
        chatHistory: List<ChatMessage>,
        config: AiConfig,
        availableTools: List<AgentTool>
    ): AiResponse {
        return AiResponse.Error("Claude Service not yet implemented")
    }

    override suspend fun streamResponse(
        prompt: String,
        chatHistory: List<ChatMessage>,
        config: AiConfig,
        availableTools: List<AgentTool>
    ): Flow<AiResponse> = flow {
        emit(AiResponse.Error("Claude Service not yet implemented"))
    }
}
