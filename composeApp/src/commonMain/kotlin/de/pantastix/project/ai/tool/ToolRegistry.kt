package de.pantastix.project.ai.tool

import de.pantastix.project.repository.CardRepository

class ToolRegistry {
    /**
     * Returns all available tools configured with the provided [repository].
     * This allows tools to switch between local and remote data sources dynamically.
     */
    fun getAvailableTools(repository: CardRepository): List<AgentTool> {
        return listOf(
            SearchCardsTool(repository),
            GetInventoryStatsTool(repository),
            SearchSetsTool(repository),
            UpdateCardQuantityTool(repository)
        )
    }
}
