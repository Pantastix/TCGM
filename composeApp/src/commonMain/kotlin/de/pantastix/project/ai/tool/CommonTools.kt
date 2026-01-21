package de.pantastix.project.ai.tool

import de.pantastix.project.repository.CardRepository
import de.pantastix.project.model.gemini.Schema
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class SearchCardsTool(private val repository: CardRepository) : AgentTool {
    override val name = "search_cards"
    override val description = "Sucht Karten anhand von Name, Typ und Sortierung. Es MUSS mindestens ein Suchfilter (Name oder Typ) angegeben werden."
    override val parameterSchemaJson = """
        {
          "query": "String? (Name, optional, aber empfohlen)",
          "type": "String? (z.B. Fire, Water, optional)",
          "sort": "String? (price_asc, price_desc, name_asc, name_desc, optional)"
        }
    """.trimIndent()

    override val schema = Schema(
        type = "OBJECT",
        properties = mapOf(
            "query" to Schema(
                type = "STRING",
                description = "The name or partial name of the Pokémon card to search for. Must be provided if type is not."
            ),
            "type" to Schema(
                type = "STRING",
                description = "Filter by Pokémon type (e.g., Fire, Water, Psychic). Must be provided if query is not."
            ),
            "sort" to Schema(
                type = "STRING",
                description = "Sort order. Values: price_asc, price_desc, name_asc, name_desc."
            )
        ),
        required = emptyList() // Gemini seems to handle validation better if we handle logic in execute or make them optional in schema but logic strict
    )

    override suspend fun execute(parameters: Map<String, Any?>): String {
        val rawQuery = parameters["query"] as? String
        val rawType = parameters["type"] as? String
        val rawSort = parameters["sort"] as? String

        // Sanitize inputs: treat string "null" as null
        val query = if (rawQuery == "null" || rawQuery.isNullOrBlank()) null else rawQuery
        val type = if (rawType == "null" || rawType.isNullOrBlank()) null else rawType
        val sort = if (rawSort == "null" || rawSort.isNullOrBlank()) null else rawSort

        if (query == null && type == null) {
             return "{ \"error\": \"Fehler: Du hast keine Suchfilter angegeben. Bitte gib mindestens einen Namen ('query') oder einen Typ ('type') an, um zu suchen. Eine Suche ohne Filter ist nicht erlaubt.\" }"
        }

        println("AI Tool [search_cards] (Repo: ${repository::class.simpleName}) searching for: query='$query', type='$type', sort='$sort'")
        val filtered = repository.searchCards(query, type, sort)
        println("AI Tool [search_cards] Repository returned ${filtered.size} items")

        val result = buildJsonObject {
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

        println("AI Tool [search_cards] result: $result")
        return result
    }
}

class GetInventoryStatsTool(private val repository: CardRepository) : AgentTool {
    override val name = "get_inventory_stats"
    override val description = "Gibt Statistiken über die Sammlung."
    override val parameterSchemaJson = "{}"

    override val schema = Schema(
        type = "OBJECT",
        properties = emptyMap()
    )

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