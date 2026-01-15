package de.pantastix.project.ai.model.gemini

import de.pantastix.project.ai.AiModel
import de.pantastix.project.model.gemini.AiModel as ApiModel

interface GeminiModelGroup {
    val regex: Regex
    fun createAiModel(apiModel: ApiModel): AiModel
}
