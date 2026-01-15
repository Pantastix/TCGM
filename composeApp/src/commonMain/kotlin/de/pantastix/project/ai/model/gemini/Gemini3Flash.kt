package de.pantastix.project.ai.model.gemini

import de.pantastix.project.ai.AiCapability
import de.pantastix.project.ai.AiModel
import de.pantastix.project.ai.AiProviderType
import de.pantastix.project.model.gemini.AiModel as ApiModel

class Gemini3Flash : GeminiModelGroup {
    override val regex = Regex("""^(models/)?gemini-3-flash(-.*)?""", RegexOption.IGNORE_CASE)

    override fun createAiModel(apiModel: ApiModel): AiModel {
        val name = apiModel.name
        
        // Extract variant after gemini-3-flash
        // e.g., models/gemini-3-flash-8b -> 8b
        val variant = name.split("gemini-3-flash-").lastOrNull()?.takeIf { it != name }

        val displayName = if (variant != null) {
            "Gemini 3 Flash ($variant)"
        } else {
            "Gemini 3 Flash"
        }

        return AiModel(
            id = name,
            displayName = displayName,
            provider = AiProviderType.GEMINI_CLOUD,
            capabilities = setOf(AiCapability.NATIVE_TOOL_CALLING, AiCapability.TEXT_GENERATION)
        )
    }
}