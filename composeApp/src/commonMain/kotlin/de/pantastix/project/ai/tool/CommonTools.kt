package de.pantastix.project.ai.tool

import de.pantastix.project.repository.CardRepository
import de.pantastix.project.model.gemini.Schema
import de.pantastix.project.model.PokemonCardInfo
import de.pantastix.project.ui.viewmodel.PendingChatAction
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*

// Helper functions for robust parameter parsing
private fun parseId(value: Any?): Long? {
    return when (value) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    }
}

private fun parseInt(value: Any?): Int? {
    return when (value) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }
}

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
    override val name = "search_my_inventory"
    override val description = "Sucht AUSSCHLIESSLICH in der bereits vorhandenen Sammlung des Nutzers. Nutze dies, um zu prüfen, ob der Nutzer eine Karte bereits besitzt, wie viele er hat oder um Statistiken zu eigenen Karten zu erhalten."
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
        val limit = parseInt(parameters["limit"]) ?: 20

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
                        put("card_id", it.id) // Interne ID für update_card
                        put("set", it.setName)
                        put("price", it.currentPrice ?: 0.0)
                        put("copies", it.ownedCopies)
                        put("language", it.language)
                        put("price_source", it.selectedPriceSource ?: "trend")
                        if (it.imageUrl != null) {
                            put("image_url", it.imageUrl)
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
    override val description = "Ändert die Anzahl einer Karte in der Sammlung (hinzufügen oder entfernen). Nutze search_my_inventory um die 'card_id' zu finden. WICHTIG: Du kannst den Preis einer existierenden Karte NICHT ändern. Jede Änderung muss vom Nutzer bestätigt werden."
    override val parameterSchemaJson = """
        {
          "card_id": "Long (Die interne ID der Karte)",
          "change": "Int (Mengenänderung, z.B. +1 oder -1)"
        }
    """.trimIndent()

    override val schema = Schema(
        type = "OBJECT",
        properties = mapOf(
            "card_id" to Schema(type = "INTEGER", description = "The internal card ID from search_my_inventory."),
            "change" to Schema(type = "INTEGER", description = "The number of copies to add (positive) or remove (negative).")
        ),
        required = listOf("card_id", "change")
    )

    override suspend fun execute(parameters: Map<String, Any?>): String {
        val cardId = parseId(parameters["card_id"]) ?: return "{ \"error\": \"card_id fehlt oder ungültig.\" }"
        val change = parseInt(parameters["change"]) ?: return "{ \"error\": \"change fehlt oder ungültig.\" }"

        val card = repository.getFullCardDetails(cardId) ?: return "{ \"error\": \"Karte mit ID $cardId nicht gefunden.\" }"
        
        val currentCount = card.ownedCopies
        val updatedCount = (currentCount + change).coerceAtLeast(0)
        
        if (updatedCount == currentCount) {
             return "{ \"result\": \"Keine Änderung der Anzahl notwendig. Aktuelle Anzahl: $currentCount\" }"
        }

        val action = PendingChatAction(
            actionType = de.pantastix.project.ui.viewmodel.PendingActionType.UPDATE,
            cardId = cardId,
            cardName = card.nameLocal,
            imageUrl = card.imageUrl,
            currentCount = currentCount,
            newCount = updatedCount,
            change = change,
            selectedPriceSource = card.selectedPriceSource,
            language = card.language
        )

        onActionProposed(action)

        return buildJsonObject {
            put("status", "proposed")
            put("card_name", card.nameLocal)
            put("current_count", currentCount)
            put("proposed_count", updatedCount)
            put("message", "Änderung von ${if (change > 0) "+" else ""}$change Exemplar(en) wurde zur Bestätigung vorgemerkt.")
        }.toString()
    }
}

class SearchApiCardTool(
    private val apiService: de.pantastix.project.service.TcgApiService
) : AgentTool {
    override val name = "search_external_api"
    override val description = "Sucht Kartendetails und Preise in der weltweiten Pokémon-Kartendatenbank. Nutze dies NUR, wenn 'search_my_inventory' kein Ergebnis geliefert hat und du eine neue Karte zur Sammlung hinzufügen möchtest."
    override val parameterSchemaJson = """
        {
          "set_id": "String (Die eindeutige API-ID des Sets, z.B. 'sv3')",
          "local_id": "String (Die Nummer der Karte im Set, z.B. '051')",
          "language": "String? (Sprachcode, z.B. 'de' oder 'en'. Standard: 'en')"
        }
    """.trimIndent()

    override val schema = Schema(
        type = "OBJECT",
        properties = mapOf(
            "set_id" to Schema(type = "STRING", description = "The unique set ID."),
            "local_id" to Schema(type = "STRING", description = "The card number within the set."),
            "language" to Schema(type = "STRING", description = "Language code (de, en, etc.). Default: en.")
        ),
        required = listOf("set_id", "local_id")
    )

    override suspend fun execute(parameters: Map<String, Any?>): String {
        val setId = parameters["set_id"] as? String ?: return "{ \"error\": \"set_id fehlt.\" }"
        val localId = parameters["local_id"] as? String ?: return "{ \"error\": \"local_id fehlt.\" }"
        val language = parameters["language"] as? String ?: "en"

        val details = apiService.getCardDetails(setId, localId, language) ?: return "{ \"error\": \"Karte nicht bei API gefunden.\" }"

        return buildJsonObject {
            put("tcg_dex_id", details.id)
            put("name", details.name)
            put("set_name", details.set?.name ?: "")
            put("rarity", details.rarity ?: "")
            put("image_url", details.image ?: "")
            put("pricing", buildJsonObject {
                details.pricing?.cardmarket?.let { cm ->
                    put("trend", cm.trend ?: 0.0)
                    put("trend_holo", cm.`trend-holo` ?: 0.0)
                    put("avg1", cm.avg1 ?: 0.0)
                    put("avg30", cm.avg30 ?: 0.0)
                    put("low", cm.low ?: 0.0)
                }
            })
        }.toString()
    }
}

class SearchExternalCardByNameTool(
    private val apiService: de.pantastix.project.service.TcgApiService
) : AgentTool {
    override val name = "search_external_api_by_name"
    override val description = "Sucht Karten in der weltweiten Pokémon-Kartendatenbank anhand ihres Namens. Kann optional auf ein bestimmtes Set eingeschränkt werden."
    override val parameterSchemaJson = """
        {
          "name": "String (Der Name der Karte, z.B. 'Gengar')",
          "set_id": "String? (Optional: Die ID des Sets, z.B. 'sv3')",
          "language": "String? (Sprachcode, z.B. 'de' oder 'en'. Standard: 'en')"
        }
    """.trimIndent()

    override val schema = Schema(
        type = "OBJECT",
        properties = mapOf(
            "name" to Schema(type = "STRING", description = "The name of the card to search for."),
            "set_id" to Schema(type = "STRING", description = "Optional: Limit search to this Set ID."),
            "language" to Schema(type = "STRING", description = "Language code (de, en, etc.). Default: en.")
        ),
        required = listOf("name")
    )

    override suspend fun execute(parameters: Map<String, Any?>): String {
        val name = parameters["name"] as? String ?: return "{ \"error\": \"name fehlt.\" }"
        val setId = parameters["set_id"] as? String
        val language = parameters["language"] as? String ?: "en"

        val results = apiService.searchCardsByName(name, language, setId)

        return buildJsonObject {
            putJsonArray("results") {
                results.forEach { card ->
                    add(buildJsonObject {
                        put("id", card.id)
                        put("name", card.name)
                        put("image_url", card.image ?: "")
                        
                        // Use localId from API if available, otherwise fallback to parsing id
                        val finalLocalId = card.localId ?: card.id.split("-").lastOrNull() ?: ""
                        val finalSetId = if (card.id.contains("-")) card.id.substringBefore("-") else ""

                        put("set_id", finalSetId)
                        put("local_id", finalLocalId)
                    })
                }
            }
            put("count", results.size)
            put("note", "Nutze die 'set_id' und 'local_id' aus diesen Ergebnissen für 'search_external_api' um Preise zu erhalten oder 'propose_add_card' um sie hinzuzufügen.")
        }.toString()
    }
}

class ProposeAddCardTool(
    private val apiService: de.pantastix.project.service.TcgApiService,
    private val onActionProposed: (PendingChatAction) -> Unit
) : AgentTool {
    override val name = "propose_add_card"
    override val description = "Schlägt vor, eine neue Karte zur Sammlung hinzuzufügen. Nutze vorher 'search_api_card' um Details zu finden."
    override val parameterSchemaJson = """
        {
          "set_id": "String",
          "local_id": "String",
          "count": "Int (Anzahl der Exemplare)",
          "price_source": "String? (trend, trend-holo, avg1, avg30, low. Standard: trend)",
          "language": "String? (Standard: en)"
        }
    """.trimIndent()

    override val schema = Schema(
        type = "OBJECT",
        properties = mapOf(
            "set_id" to Schema(type = "STRING"),
            "local_id" to Schema(type = "STRING"),
            "count" to Schema(type = "INTEGER"),
            "price_source" to Schema(type = "STRING", description = "The price source to use (trend, avg30, etc.)."),
            "language" to Schema(type = "STRING")
        ),
        required = listOf("set_id", "local_id", "count")
    )

    override suspend fun execute(parameters: Map<String, Any?>): String {
        val setId = parameters["set_id"] as? String ?: return "{ \"error\": \"set_id fehlt.\" }"
        val localId = parameters["local_id"] as? String ?: return "{ \"error\": \"local_id fehlt.\" }"
        val count = parseInt(parameters["count"]) ?: 1
        val priceSource = parameters["price_source"] as? String ?: "trend"
        val language = parameters["language"] as? String ?: "en"

        val details = apiService.getCardDetails(setId, localId, language) ?: return "{ \"error\": \"Karte bei API nicht gefunden.\" }"

        val action = PendingChatAction(
            actionType = de.pantastix.project.ui.viewmodel.PendingActionType.ADD,
            apiDetails = details,
            cardName = details.name,
            imageUrl = details.image,
            currentCount = 0,
            newCount = count,
            change = count,
            selectedPriceSource = priceSource,
            language = language
        )

        onActionProposed(action)

        return buildJsonObject {
            put("status", "proposed")
            put("card_name", details.name)
            put("count", count)
            put("price_source", priceSource)
            put("message", "Hinzufügen von $count Exemplar(en) wurde zur Bestätigung vorgemerkt.")
        }.toString()
    }
}
