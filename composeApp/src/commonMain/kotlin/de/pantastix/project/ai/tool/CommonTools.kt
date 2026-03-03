package de.pantastix.project.ai.tool

import de.pantastix.project.repository.CardRepository
import de.pantastix.project.model.gemini.Schema
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class SearchSetsTool(private val repository: CardRepository) : AgentTool {
    override val name = "search_sets"
    override val description = "Sucht nach Kartensets basierend auf Name, ID oder Abkürzung. Nützlich, um die korrekte 'setId' für andere Tools zu finden."
    override val parameterSchemaJson = """
        {
          "query": "String (Name oder ID des Sets, z.B. '151', 'Obsidian', 'sv3')"
        }
    """.trimIndent()

    override val schema = Schema(
        type = "OBJECT",
        properties = mapOf(
            "query" to Schema(
                type = "STRING",
                description = "The name, abbreviation, or ID of the set to search for."
            )
        ),
        required = listOf("query")
    )

    override suspend fun execute(parameters: Map<String, Any?>): String {
        val query = parameters["query"] as? String
        
        if (query.isNullOrBlank()) {
             return "{ \"error\": \"Fehler: Kein Suchbegriff angegeben.\" }"
        }

        val sets = repository.searchSets(query)

        return buildJsonObject {
            putJsonArray("sets") {
                sets.forEach {
                    add(buildJsonObject {
                        put("id", it.setId)
                        put("name", it.nameLocal)
                        put("abbreviation", it.abbreviation ?: "")
                        put("card_count", it.cardCountOfficial)
                    })
                }
            }
            put("count", sets.size)
        }.toString()
    }
}

class SearchCardsTool(private val repository: CardRepository) : AgentTool {
    override val name = "search_cards"
    override val description = "Sucht Karten in der Sammlung. Unterstützt Filter nach Name, Set, Typ (sprachunabhängig, z.B. 'Water' oder 'Wasser'), Seltenheit und Künstler."
    override val parameterSchemaJson = """
        {
          "query": "String? (Name)",
          "set_id": "String? (Exakte Set ID, z.B. 'sv1')",
          "type": "String? (z.B. Fire)",
          "rarity": "String? (z.B. Rare)",
          "illustrator": "String? (Name)",
          "sort": "String? (price_desc, price_asc, name_asc)",
          "limit": "Int? (Standard: 20, max: 50)"
        }
    """.trimIndent()

    override val schema = Schema(
        type = "OBJECT",
        properties = mapOf(
            "query" to Schema(type = "STRING", description = "Name or partial name of the card."),
            "set_id" to Schema(type = "STRING", description = "The exact Set ID (use search_sets to find it)."),
            "type" to Schema(type = "STRING", description = "Pokémon type (e.g., Fire, Water)."),
            "rarity" to Schema(type = "STRING", description = "Rarity (e.g., 'Illustration Rare', 'Common')."),
            "illustrator" to Schema(type = "STRING", description = "Artist name."),
            "sort" to Schema(type = "STRING", description = "Sort order: price_asc, price_desc, name_asc."),
            "limit" to Schema(type = "INTEGER", description = "Limit the number of results (default 20, max 50).")
        ),
        required = emptyList()
    )

    override suspend fun execute(parameters: Map<String, Any?>): String {
        val query = parameters["query"] as? String
        val setId = parameters["set_id"] as? String
        val type = parameters["type"] as? String
        val rarity = parameters["rarity"] as? String
        val illustrator = parameters["illustrator"] as? String
        val sort = parameters["sort"] as? String
        val limit = (parameters["limit"] as? Number)?.toInt() ?: 20

        // Allow search if at least one filter OR a sort order is provided
        if (query.isNullOrBlank() && setId.isNullOrBlank() && type.isNullOrBlank() && 
            rarity.isNullOrBlank() && illustrator.isNullOrBlank() && sort.isNullOrBlank()) {
             return "{ \"error\": \"Fehler: Bitte gib mindestens einen Filter an (Name, Set, Typ, etc.) oder eine Sortierung.\" }"
        }

        val filtered = repository.searchCards(
            query = if (query == "null" || query.isNullOrBlank()) null else query,
            type = if (type == "null" || type.isNullOrBlank()) null else type,
            sort = if (sort == "null" || sort.isNullOrBlank()) null else sort,
            setId = if (setId == "null" || setId.isNullOrBlank()) null else setId,
            rarity = if (rarity == "null" || rarity.isNullOrBlank()) null else rarity,
            illustrator = if (illustrator == "null" || illustrator.isNullOrBlank()) null else illustrator,
            limit = limit.coerceIn(1, 50)
        )

        val result = buildJsonObject {
            putJsonArray("cards") {
                filtered.forEach {
                    add(buildJsonObject {
                        put("name", it.nameLocal)
                        put("id", it.tcgDexCardId)
                        put("set", it.setName)
                        put("rarity", "") // Info not currently in PokemonCardInfo view, need to check Repository mapping if critical
                        put("price", it.currentPrice ?: 0.0)
                        put("copies", it.ownedCopies)
                        // WICHTIG: Image URL für Markdown Embedding zurückgeben
                        if (it.imageUrl != null) {
                            put("imageUrl", it.imageUrl)
                        }
                    })
                }
            }
            put("count", filtered.size)
        }.toString()

        return result
    }
}

class GetInventoryStatsTool(private val repository: CardRepository) : AgentTool {
    override val name = "get_inventory_stats"
    override val description = "Gibt Statistiken über die Sammlung (Gesamtwert, Anzahl). NICHT für Informationen zu einzelnen Karten verwenden (nutze dafür search_cards)."
    override val parameterSchemaJson = """
        {
          "set_id": "String? (Optional: Filtert Statistiken nur für dieses Set)"
        }
    """.trimIndent()

    override val schema = Schema(
        type = "OBJECT",
        properties = mapOf(
            "set_id" to Schema(type = "STRING", description = "Optional: Calculate stats only for this specific Set ID.")
        )
    )

    override suspend fun execute(parameters: Map<String, Any?>): String {
        val setId = parameters["set_id"] as? String
        val cleanSetId = if (setId == "null" || setId.isNullOrBlank()) null else setId

        // TODO: This loads all cards into memory, which is fine for < 20k cards but could be optimized with a direct SQL count query later.
        val allCards = repository.getCardInfos().first()
        
        val filteredCards = if (cleanSetId != null) {
            // Since we don't have setId in PokemonCardInfo directly available without lookup or if repository doesn't filter it
            // We need to rely on the fact that we might need to fetch full details OR assume the repository call handles filtering.
            // CURRENTLY: Repository.getCardInfos returns ALL.
            // Ideally, we should add `getInventoryStats(setId)` to the Repository.
            // For now, let's filter in memory if possible, but PokemonCardInfo lacks setId field in the simple view!
            // Wait, I updated PokemonCardInfo? Let me check.
            // The view `PokemonCardInfo` has `tcgDexCardId` (e.g. "sv1-001"), so we can extract set ID? NO, that's brittle.
            // Repository `searchCards` returns `PokemonCardInfo`. Let's use `searchCards` with set_id if provided!
            repository.searchCards(query = "", setId = cleanSetId)
        } else {
            allCards
        }

        val totalValue = filteredCards.sumOf { (it.currentPrice ?: 0.0) * it.ownedCopies }
        val totalCopies = filteredCards.sumOf { it.ownedCopies }

        return buildJsonObject {
            put("filter_set_id", cleanSetId ?: "all")
            put("unique_cards_count", filteredCards.size)
            put("total_cards_count", totalCopies)
            put("total_market_value", totalValue)
        }.toString()
    }
}
