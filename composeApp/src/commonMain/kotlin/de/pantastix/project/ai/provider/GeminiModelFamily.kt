package de.pantastix.project.ai.provider

import de.pantastix.project.ai.AiCapability
import de.pantastix.project.ai.AiModel
import de.pantastix.project.ai.AiProviderType
import de.pantastix.project.model.gemini.AiModel as ApiModel

interface GeminiModelFamily {
    val regex: Regex
    fun createAiModel(apiModel: ApiModel): AiModel
}

class GeminiFlashFamily : GeminiModelFamily {
    override val regex = Regex("""^(models/)?gemini-\d+(\.\d+)?-(flash|pro)(-[a-zA-Z0-9]+)?""")

    override fun createAiModel(apiModel: ApiModel): AiModel {
        return AiModel(
            id = apiModel.name,
            displayName = apiModel.displayName.takeIf { it.isNotEmpty() } ?: apiModel.name,
            provider = AiProviderType.GEMINI_CLOUD,
            capabilities = setOf(AiCapability.NATIVE_TOOL_CALLING, AiCapability.TEXT_GENERATION)
        )
    }
}

class GemmaFamily : GeminiModelFamily {
    // Matches "models/gemma-3-..." or "gemma3:..."
    override val regex = Regex("""^(models/)?gemma[-:]?3.*""", RegexOption.IGNORE_CASE)

    override fun createAiModel(apiModel: ApiModel): AiModel {
        // User wants to group them, but we need unique IDs for the dropdown.
        // We will format the display name to be clear.
        // e.g. "Gemma 3 (27b-it)"
        
        return AiModel(
            id = apiModel.name,
            displayName = formatDisplayName(apiModel),
            provider = AiProviderType.GEMINI_CLOUD,
            capabilities = setOf(AiCapability.TEXT_GENERATION) // Assuming no tools for Gemma yet?
        )
    }

    private fun formatDisplayName(apiModel: ApiModel): String {
        val name = apiModel.name
        // Try to extract parameter size
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
