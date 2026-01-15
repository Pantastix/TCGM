package de.pantastix.project.ai.model.ollama

import de.pantastix.project.ai.AiModel

interface OllamaModelGroup {
    fun matches(name: String): Boolean
    fun createAiModel(ollamaModel: OllamaModel): AiModel
}
