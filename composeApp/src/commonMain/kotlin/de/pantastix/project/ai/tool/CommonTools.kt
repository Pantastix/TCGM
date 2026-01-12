package de.pantastix.project.ai.tool

import de.pantastix.project.repository.CardRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class SearchCardsTool(private val repository: CardRepository) : AgentTool {
    override val name = "search_cards"
    override val description = "Sucht Karten im Inventar des Users."
    override val parameterSchemaJson = "{ \"query\": \"String (Name)\" }"

    override suspend fun execute(parameters: Map<String, Any?>): String {
        val query = parameters["query"] as? String ?: return "{ \"error\": \"No query provided\" }"
        val filtered = repository.searchCards(query).take(10)

        return buildJsonObject {
            putJsonArray("cards") {
                filtered.forEach {
                    add(buildJsonObject {
                        put("name", it.nameLocal)
                        put("set", it.setName)
                        put("price", it.currentPrice ?: 0.0)
                        put("copies", it.ownedCopies)
                    })
                }
            }
            put("count", filtered.size)
        }.toString()
    }
}

class GetInventoryStatsTool(private val repository: CardRepository) : AgentTool {
    override val name = "get_inventory_stats"
    override val description = "Gibt Statistiken über die Sammlung."
    override val parameterSchemaJson = "{}"

    override suspend fun execute(parameters: Map<String, Any?>): String {
        val allCards = repository.getCardInfos().first()
        val totalValue = allCards.sumOf { (it.currentPrice ?: 0.0) * it.ownedCopies }
        val totalCopies = allCards.sumOf { it.ownedCopies }

        return buildJsonObject {
            put("total_cards_types", allCards.size)
            put("total_copies", totalCopies)
            put("total_market_value", totalValue)
        }.toString()
    }
}
