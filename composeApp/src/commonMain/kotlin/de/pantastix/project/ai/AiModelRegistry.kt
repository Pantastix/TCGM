package de.pantastix.project.ai

import de.pantastix.project.ai.strategy.*
import de.pantastix.project.ai.strategy.gemini.*
import de.pantastix.project.ai.strategy.ollama.*
import de.pantastix.project.ai.strategy.ollama.GptOssStrategy

enum class ModelCategory {
    GEMINI_CLOUD,
    OLLAMA_LOCAL
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
            if (Regex("""^gpt-oss.*""", RegexOption.IGNORE_CASE).matches(modelId)) {
                return GptOssStrategy()
            }
            // Default Native Ollama
            return NativeOllamaStrategy()
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
        )
    )

    fun resolveFamily(modelId: String, category: ModelCategory): ModelFamilyDefinition? {
        return families.find { it.category == category && it.modelIdPattern.matches(modelId) }
    }
}