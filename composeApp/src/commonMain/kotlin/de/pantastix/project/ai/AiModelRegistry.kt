package de.pantastix.project.ai

import de.pantastix.project.ai.strategy.*
import de.pantastix.project.ai.strategy.gemini.*
import de.pantastix.project.ai.strategy.ollama.*
import de.pantastix.project.ai.strategy.mistral.MistralNativeStrategy

enum class ModelCategory {
    GEMINI_CLOUD,
    OLLAMA_LOCAL,
    MISTRAL_CLOUD,
    CLAUDE_CLOUD
}

data class ModelFamilyDefinition(
    val id: String,
    val displayName: String,
    val category: ModelCategory,
    val modelIdPattern: Regex,
    val variantNameExtractor: (String) -> String = { it.substringAfterLast("/") },
    val filter: (String) -> Boolean = { true },
    val modelComparator: Comparator<String>? = null
)

object AiModelRegistry {
    
    // --- STRATEGIES (Execution Logic) ---
    // We instantiate strategies on demand to ensure they are stateless per request (important for streaming accumulators)

    fun resolveStrategy(modelId: String, provider: AiProviderType): AiWorkflowStrategy? {
        if (provider == AiProviderType.GEMINI_CLOUD) {
             // Gemma 4 Specific Strategy (Supports Native Tools + Special Thinking Channel)
             if (Regex("""^(models/)?gemma-4.*""", RegexOption.IGNORE_CASE).matches(modelId)) {
                 return Gemma4NativeStrategy()
             }
             // Gemma 3 Specific Strategy
             if (Regex("""^(models/)?gemma-3.*""", RegexOption.IGNORE_CASE).matches(modelId)) {
                 return Gemma3ReasoningStrategy()
             }
             // Default Gemini Native Strategy (Flash, Pro, etc.)
             if (Regex("""^(models/)?gemini-(3|1\.5|2\.0).*""", RegexOption.IGNORE_CASE).matches(modelId)) {
                 return GeminiNativeStrategy()
             }
        }
        
        if (provider == AiProviderType.OLLAMA_LOCAL) {
            if (Regex("""gemma-4.*""", RegexOption.IGNORE_CASE).matches(modelId)) {
                return Gemma4OllamaStrategy()
            }
            if (Regex("""^gpt-oss.*""", RegexOption.IGNORE_CASE).matches(modelId)) {
                return GptOssStrategy()
            }
            // Default Native Ollama
            return NativeOllamaStrategy()
        }

        if (provider == AiProviderType.MISTRAL_CLOUD) {
            return MistralNativeStrategy()
        }

        if (provider == AiProviderType.CLAUDE_CLOUD) {
            return de.pantastix.project.ai.strategy.claude.ClaudeNativeStrategy()
        }
        
        return null
    }

    fun getStrategyForDiscovery(modelName: String, provider: AiProviderType): AiWorkflowStrategy? {
        // For discovery, we can use temporary instances just to check regex or capabilities
        return resolveStrategy(modelName, provider)
    }

    // --- FAMILIES (UI Grouping & Filtering) ---
    private val gemmaParamComparator = Comparator<String> { a, b ->
        fun getParams(s: String): Int {
            val match = Regex("""(\d+)b""", RegexOption.IGNORE_CASE).find(s)
            return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
        }
        getParams(b).compareTo(getParams(a)) // Descending
    }

    val families = listOf(
        // GEMINI CLOUD
        ModelFamilyDefinition(
            id = "gemma-4-cloud",
            displayName = "Gemma 4 (Cloud)",
            category = ModelCategory.GEMINI_CLOUD,
            modelIdPattern = Regex("""^(models/)?gemma-4.*""", RegexOption.IGNORE_CASE),
            modelComparator = gemmaParamComparator
        ),
        ModelFamilyDefinition(
            id = "gemini-3-flash",
            displayName = "Gemini 3 Flash",
            category = ModelCategory.GEMINI_CLOUD,
            modelIdPattern = Regex("""^(models/)?(gemini-3-flash|gemini-flash-3).*""", RegexOption.IGNORE_CASE)
        ),
        ModelFamilyDefinition(
            id = "gemma-3-cloud",
            displayName = "Gemma 3 (Cloud)",
            category = ModelCategory.GEMINI_CLOUD,
            modelIdPattern = Regex("""^(models/)?gemma-3.*""", RegexOption.IGNORE_CASE),
            filter = { !it.contains(Regex("""-e\d+b""", RegexOption.IGNORE_CASE)) },
            modelComparator = gemmaParamComparator
        ),
        ModelFamilyDefinition(
            id = "gemini-1.5-flash",
            displayName = "Gemini 1.5 Flash",
            category = ModelCategory.GEMINI_CLOUD,
            modelIdPattern = Regex("""^(models/)?gemini-1\.5-flash.*""", RegexOption.IGNORE_CASE)
        ),
        ModelFamilyDefinition(
            id = "gemini-1.5-pro",
            displayName = "Gemini 1.5 Pro",
            category = ModelCategory.GEMINI_CLOUD,
            modelIdPattern = Regex("""^(models/)?gemini-1\.5-pro.*""", RegexOption.IGNORE_CASE)
        ),
        ModelFamilyDefinition(
            id = "gemini-2.0",
            displayName = "Gemini 2 Flash",
            category = ModelCategory.GEMINI_CLOUD,
            modelIdPattern = Regex("""^(models/)?gemini-2\.0.*""", RegexOption.IGNORE_CASE)
        ),

        // OLLAMA LOCAL
        ModelFamilyDefinition(
            id = "gemma-4-local",
            displayName = "Gemma 4 (Local)",
            category = ModelCategory.OLLAMA_LOCAL,
            modelIdPattern = Regex("""gemma-4.*""", RegexOption.IGNORE_CASE),
            modelComparator = gemmaParamComparator
        ),
        ModelFamilyDefinition(
            id = "gemma-3-local",
            displayName = "Gemma 3 (Local)",
            category = ModelCategory.OLLAMA_LOCAL,
            modelIdPattern = Regex("""gemma-3.*""", RegexOption.IGNORE_CASE),
            filter = { !it.contains(Regex("""-e\d+b""", RegexOption.IGNORE_CASE)) },
            modelComparator = gemmaParamComparator
        ),
        ModelFamilyDefinition(
            id = "gpt-oss-local",
            displayName = "GPT-OSS",
            category = ModelCategory.OLLAMA_LOCAL,
            modelIdPattern = Regex("""gpt-oss(:.*)?""", RegexOption.IGNORE_CASE)
        ),
         ModelFamilyDefinition(
            id = "llama-3",
            displayName = "Llama 3",
            category = ModelCategory.OLLAMA_LOCAL,
            modelIdPattern = Regex("""llama-3(:.*)?""", RegexOption.IGNORE_CASE)
        ),
         ModelFamilyDefinition(
            id = "mistral",
            displayName = "Mistral",
            category = ModelCategory.OLLAMA_LOCAL,
            modelIdPattern = Regex("""mistral(:.*)?""", RegexOption.IGNORE_CASE)
        ),

        // MISTRAL CLOUD
        ModelFamilyDefinition(
            id = "mistral-large",
            displayName = "Mistral Large",
            category = ModelCategory.MISTRAL_CLOUD,
            modelIdPattern = Regex("""mistral-large.*""", RegexOption.IGNORE_CASE)
        ),
        ModelFamilyDefinition(
            id = "mistral-medium",
            displayName = "Mistral Medium",
            category = ModelCategory.MISTRAL_CLOUD,
            modelIdPattern = Regex("""mistral-medium.*""", RegexOption.IGNORE_CASE)
        ),
        ModelFamilyDefinition(
            id = "mistral-small",
            displayName = "Mistral Small",
            category = ModelCategory.MISTRAL_CLOUD,
            modelIdPattern = Regex("""mistral-small.*""", RegexOption.IGNORE_CASE)
        ),
        ModelFamilyDefinition(
            id = "codestral",
            displayName = "Codestral",
            category = ModelCategory.MISTRAL_CLOUD,
            modelIdPattern = Regex("""codestral.*""", RegexOption.IGNORE_CASE)
        ),
        ModelFamilyDefinition(
            id = "mistral-nemo",
            displayName = "Mistral Nemo",
            category = ModelCategory.MISTRAL_CLOUD,
            modelIdPattern = Regex(""".*nemo.*""", RegexOption.IGNORE_CASE)
        ),
        ModelFamilyDefinition(
            id = "pixtral",
            displayName = "Pixtral",
            category = ModelCategory.MISTRAL_CLOUD,
            modelIdPattern = Regex("""pixtral.*""", RegexOption.IGNORE_CASE)
        ),
        ModelFamilyDefinition(
            id = "ministral",
            displayName = "Ministral",
            category = ModelCategory.MISTRAL_CLOUD,
            modelIdPattern = Regex("""ministral.*""", RegexOption.IGNORE_CASE)
        ),

        // CLAUDE CLOUD
        ModelFamilyDefinition(
            id = "claude-3-7-sonnet",
            displayName = "Claude 3.7 Sonnet",
            category = ModelCategory.CLAUDE_CLOUD,
            modelIdPattern = Regex("""claude-3-7-sonnet.*""", RegexOption.IGNORE_CASE)
        ),
        ModelFamilyDefinition(
            id = "claude-3-5-sonnet",
            displayName = "Claude 3.5 Sonnet",
            category = ModelCategory.CLAUDE_CLOUD,
            modelIdPattern = Regex("""claude-3-5-sonnet.*""", RegexOption.IGNORE_CASE)
        ),
        ModelFamilyDefinition(
            id = "claude-3-5-haiku",
            displayName = "Claude 3.5 Haiku",
            category = ModelCategory.CLAUDE_CLOUD,
            modelIdPattern = Regex("""claude-3-5-haiku.*""", RegexOption.IGNORE_CASE)
        ),
        ModelFamilyDefinition(
            id = "claude-3-opus",
            displayName = "Claude 3 Opus",
            category = ModelCategory.CLAUDE_CLOUD,
            modelIdPattern = Regex("""claude-3-opus.*""", RegexOption.IGNORE_CASE)
        ),
        ModelFamilyDefinition(
            id = "claude-3-sonnet",
            displayName = "Claude 3 Sonnet",
            category = ModelCategory.CLAUDE_CLOUD,
            modelIdPattern = Regex("""claude-3-sonnet.*""", RegexOption.IGNORE_CASE)
        ),
        ModelFamilyDefinition(
            id = "claude-3-haiku",
            displayName = "Claude 3 Haiku",
            category = ModelCategory.CLAUDE_CLOUD,
            modelIdPattern = Regex("""claude-3-haiku.*""", RegexOption.IGNORE_CASE)
        )
    )

    fun resolveFamily(modelId: String, category: ModelCategory): ModelFamilyDefinition? {
        return families.find { it.category == category && it.modelIdPattern.matches(modelId) }
    }
}