package de.pantastix.project.ai.model.gemini

import de.pantastix.project.ai.AiCapability
import de.pantastix.project.ai.AiModel
import de.pantastix.project.ai.AiProviderType
import de.pantastix.project.model.gemini.AiModel as ApiModel

class Gemma : GeminiModelGroup {
    // Matches "models/gemma-3-..." or "gemma3:..."
    override val regex = Regex("""^(models/)?gemma[-:]?3.*""", RegexOption.IGNORE_CASE)

    override fun createAiModel(apiModel: ApiModel): AiModel {
        return AiModel(
            id = apiModel.name,
            displayName = formatDisplayName(apiModel),
            provider = AiProviderType.GEMINI_CLOUD,
            capabilities = setOf(AiCapability.TEXT_GENERATION)
        )
    }

    private fun formatDisplayName(apiModel: ApiModel): String {
        val name = apiModel.name
        val sizeRegex = Regex("""(\d+b)""", RegexOption.IGNORE_CASE)
        val match = sizeRegex.find(name)
        val size = match?.value?.lowercase()

        val baseName = "Gemma 3"
        return if (size != null) {
            "$baseName ($size)"
        } else {
            apiModel.displayName.takeIf { it.isNotEmpty() } ?: baseName
        }
    }
}
