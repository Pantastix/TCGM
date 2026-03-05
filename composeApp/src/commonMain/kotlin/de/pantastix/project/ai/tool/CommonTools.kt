package de.pantastix.project.ai.tool

import de.pantastix.project.repository.CardRepository
import de.pantastix.project.model.gemini.Schema
import de.pantastix.project.model.PokemonCardInfo
import de.pantastix.project.ui.viewmodel.PendingChatAction
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*

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
                        put("tcg_dex_id", it.tcgDexCardId)
                        put("card_id", it.id) // Interne ID für update_card_quantity
                        put("set", it.setName)
                        put("price", it.currentPrice ?: 0.0)
                        put("copies", it.ownedCopies)
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
    override val description = "Gibt Statistiken über die Sammlung (Gesamtwert, Anzahl). WICHTIG: Nutze dieses Tool NUR für Übersichtswerte. Benutze search_cards für Informationen zu spezifischen Karten. NICHT raten."
    override val parameterSchemaJson = """
        {
          "set_id": "String? (Optional: Filtert Statistiken nur für dieses Set)",
          "sort_by": "String? (Nur relevant wenn set_id fehlt: 'value_desc', 'value_asc', 'count_desc', 'age_desc', 'age_asc')"
        }
    """.trimIndent()

    override val schema = Schema(
        type = "OBJECT",
        properties = mapOf(
            "set_id" to Schema(type = "STRING", description = "Optional: Calculate stats only for this specific Set ID."),
            "sort_by" to Schema(type = "STRING", description = "Optional: If set_id is null, sort the set breakdown by: value_desc, count_desc, age_desc, etc.")
        )
    )

    override suspend fun execute(parameters: Map<String, Any?>): String {
        val setId = parameters["set_id"] as? String
        val sortBy = parameters["sort_by"] as? String ?: "value_desc"
        val cleanSetId = if (setId == "null" || setId.isNullOrBlank()) null else setId

        val allCards = repository.getCardInfos().first()
        val allSets = repository.fetchAllSetsOnce()
        
        if (cleanSetId != null) {
            // Stats for a SPECIFIC set
            val filteredCards = repository.searchCards(query = "", setId = cleanSetId)
            val totalValue = filteredCards.sumOf { (it.currentPrice ?: 0.0) * it.ownedCopies }
            val totalCopies = filteredCards.sumOf { it.ownedCopies }
            val setInfo = allSets.find { it.setId == cleanSetId }

            return buildJsonObject {
                put("set_name", setInfo?.nameLocal ?: cleanSetId)
                put("set_id", cleanSetId)
                put("unique_cards_count", filteredCards.size)
                put("total_cards_count", totalCopies)
                put("total_market_value", totalValue)
                put("currency", "EUR")
            }.toString()
        } else {
            // GLOBAL Stats + Set Breakdown
            val globalValue = allCards.sumOf { (it.currentPrice ?: 0.0) * it.ownedCopies }
            val globalCopies = allCards.sumOf { it.ownedCopies }
            
            // Group cards by set name (since we don't have setId directly in PokemonCardInfo view yet)
            val setBreakdown = allCards.groupBy { it.setName }.map { (setName, cards) ->
                val setInfo = allSets.find { it.nameLocal == setName }
                buildJsonObject {
                    put("name", setName)
                    put("id", setInfo?.setId ?: "unknown")
                    put("unique_count", cards.size)
                    put("total_count", cards.sumOf { it.ownedCopies })
                    put("value", cards.sumOf { (it.currentPrice ?: 0.0) * it.ownedCopies })
                    put("release_date", setInfo?.releaseDate ?: "9999-99-99")
                }
            }

            val sortedBreakdown = when (sortBy) {
                "value_desc" -> setBreakdown.sortedByDescending { it["value"]?.jsonPrimitive?.double ?: 0.0 }
                "value_asc" -> setBreakdown.sortedBy { it["value"]?.jsonPrimitive?.double ?: 0.0 }
                "count_desc" -> setBreakdown.sortedByDescending { it["total_count"]?.jsonPrimitive?.int ?: 0 }
                "age_desc" -> setBreakdown.sortedByDescending { it["release_date"]?.jsonPrimitive?.content ?: "" }
                "age_asc" -> setBreakdown.sortedBy { it["release_date"]?.jsonPrimitive?.content ?: "" }
                else -> setBreakdown.sortedByDescending { it["value"]?.jsonPrimitive?.double ?: 0.0 }
            }

            return buildJsonObject {
                put("summary", buildJsonObject {
                    put("total_unique_cards", allCards.size)
                    put("total_physical_cards", globalCopies)
                    put("total_market_value", globalValue)
                    put("currency", "EUR")
                })
                putJsonArray("top_sets") {
                    sortedBreakdown.take(10).forEach { add(it) }
                }
                put("total_sets_in_collection", setBreakdown.size)
                put("note", "Showing top 10 sets by $sortBy. Use set_id for details on other sets.")
            }.toString()
        }
    }
}

class GetMissingCardsTool(
    private val repository: CardRepository,
    private val apiService: de.pantastix.project.service.TcgApiService
) : AgentTool {
    override val name = "get_missing_cards"
    override val description = "Identifiziert Karten eines Sets, die noch nicht in der Sammlung sind. WICHTIG: Nutze search_sets um die setId zu finden, bevor du dieses Tool nutzt."
    override val parameterSchemaJson = """
        {
          "set_id": "String (Die eindeutige API-ID des Sets, z.B. 'sv3pt5')",
          "rarity": "String? (Optional: Filtert fehlende Karten nach Seltenheit, z.B. 'Rare', 'Ultra Rare')"
        }
    """.trimIndent()

    override val schema = Schema(
        type = "OBJECT",
        properties = mapOf(
            "set_id" to Schema(type = "STRING", description = "The unique set ID (e.g., 'sv3pt5')."),
            "rarity" to Schema(type = "STRING", description = "Optional: Filter missing cards by rarity.")
        ),
        required = listOf("set_id")
    )

    override suspend fun execute(parameters: Map<String, Any?>): String {
        val setId = parameters["set_id"] as? String ?: return "{ \"error\": \"set_id fehlt.\" }"
        val rarityFilter = parameters["rarity"] as? String

        val apiCards = apiService.getSetCards(setId)
        if (apiCards.isEmpty()) return "{ \"error\": \"Keine Karten für Set $setId gefunden oder API-Fehler.\" }"

        val ownedCards = repository.getCardsBySet(setId)
        val ownedTcgIds = ownedCards.map { it.tcgDexCardId }.toSet()

        val missingCards = apiCards.filter { apiCard ->
            !ownedTcgIds.contains(apiCard.id) && (rarityFilter == null || apiCard.rarity?.contains(rarityFilter, ignoreCase = true) == true)
        }

        return buildJsonObject {
            put("set_id", setId)
            put("total_cards_in_set", apiCards.size)
            put("owned_unique_cards", ownedTcgIds.size)
            put("missing_cards_count", missingCards.size)
            putJsonArray("missing_samples") {
                missingCards.take(15).forEach { card ->
                    add(buildJsonObject {
                        put("name", card.name)
                        put("id", card.id)
                        put("local_id", card.localId)
                        put("rarity", card.rarity ?: "Unknown")
                    })
                }
            }
            if (missingCards.size > 15) {
                put("note", "Es fehlen noch ${missingCards.size - 15} weitere Karten. Verfeinere die Suche mit dem rarity-Parameter.")
            }
        }.toString()
    }
}

class UpdateCardQuantityTool(
    private val repository: CardRepository,
    private val onActionProposed: (PendingChatAction) -> Unit
) : AgentTool {
    override val name = "update_card_quantity"
    override val description = "Ändert die Anzahl einer Karte in der Sammlung (hinzufügen oder entfernen). Nutze search_cards um die 'card_id' und die aktuelle 'copies' Anzahl zu finden. Jede Änderung muss vom Nutzer bestätigt werden."
    override val parameterSchemaJson = """
        {
          "card_id": "Long (Die interne ID der Karte)",
          "change": "Int (Positive Zahl zum Hinzufügen, negative zum Entfernen, z.B. 1 oder -1)"
        }
    """.trimIndent()

    override val schema = Schema(
        type = "OBJECT",
        properties = mapOf(
            "card_id" to Schema(type = "INTEGER", description = "The internal card ID from search_cards."),
            "change" to Schema(type = "INTEGER", description = "The number of copies to add (positive) or remove (negative).")
        ),
        required = listOf("card_id", "change")
    )

    override suspend fun execute(parameters: Map<String, Any?>): String {
        val cardId = (parameters["card_id"] as? Number)?.toLong() ?: return "{ \"error\": \"card_id fehlt oder ungültig.\" }"
        val change = (parameters["change"] as? Number)?.toInt() ?: return "{ \"error\": \"change fehlt oder ungültig.\" }"

        val card = repository.getFullCardDetails(cardId) ?: return "{ \"error\": \"Karte mit ID $cardId nicht gefunden.\" }"
        
        val currentCount = card.ownedCopies
        val newCount = (currentCount + change).coerceAtLeast(0)
        
        if (newCount == currentCount) {
             return "{ \"result\": \"Keine Änderung notwendig. Aktuelle Anzahl: $currentCount\" }"
        }

        val action = PendingChatAction(
            cardId = cardId,
            cardName = card.nameLocal,
            imageUrl = card.imageUrl,
            currentCount = currentCount,
            newCount = newCount,
            change = change
        )

        onActionProposed(action)

        return buildJsonObject {
            put("status", "proposed")
            put("card_name", card.nameLocal)
            put("current_count", currentCount)
            put("proposed_count", newCount)
            put("message", "Änderung von ${if (change > 0) "+" else ""}$change Exemplar(en) wurde zur Bestätigung vorgemerkt.")
        }.toString()
    }
}
