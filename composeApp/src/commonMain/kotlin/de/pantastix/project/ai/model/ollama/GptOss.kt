package de.pantastix.project.ai.model.ollama

import de.pantastix.project.ai.AiCapability
import de.pantastix.project.ai.AiModel
import de.pantastix.project.ai.AiProviderType

class GptOss : OllamaModelGroup {
    override fun matches(name: String): Boolean = name.contains("gpt-oss", ignoreCase = true)

    override fun createAiModel(ollamaModel: OllamaModel): AiModel {
        return AiModel(
            id = ollamaModel.name,
            displayName = "GPT-OSS",
            provider = AiProviderType.OLLAMA_LOCAL,
            capabilities = setOf(AiCapability.TEXT_GENERATION)
        )
    }
}
