package de.pantastix.project.ai.tool

import de.pantastix.project.repository.CardRepository

class ToolRegistry {
    /**
     * Returns all available tools configured with the provided [repository].
     * This allows tools to switch between local and remote data sources dynamically.
     */
    fun getAvailableTools(
        repository: CardRepository, 
        apiService: de.pantastix.project.service.TcgApiService,
        onActionProposed: (de.pantastix.project.ui.viewmodel.PendingChatAction) -> Unit
    ): List<AgentTool> {
        return listOf(
            SearchCardsTool(repository),
            GetInventoryStatsTool(repository),
            SearchSetsTool(repository),
            GetMissingCardsTool(repository, apiService),
            UpdateCardQuantityTool(repository, onActionProposed)
        )
    }
}
