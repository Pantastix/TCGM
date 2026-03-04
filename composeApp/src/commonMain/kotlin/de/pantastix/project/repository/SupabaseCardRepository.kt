package de.pantastix.project.repository

import de.pantastix.project.model.*
import de.pantastix.project.model.supabase.FullPokemonCardResponse
import de.pantastix.project.model.supabase.SupabasePokemonCard
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.Count
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.int
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.contentOrNull

class SupabaseCardRepository(
    private val postgrest: Postgrest
) : CardRepository {

    private val jsonParser = Json { ignoreUnknownKeys = true }

    private val cardsTable = "PokemonCardEntity" // Name deiner Tabelle in Supabase
    private val setsTable = "SetEntity"
    private val pokemonCardInfoView = "PokemonCardInfoView"
    private val _setsFlow = MutableStateFlow<List<SetInfo>>(emptyList())

    private fun PokemonCard.toSupabasePokemonCard(): SupabasePokemonCard {
        return SupabasePokemonCard(
            id = this.id,
            setId = this.setId,
            tcgDexCardId = this.tcgDexCardId,
            nameLocal = this.nameLocal,
            nameEn = this.nameEn,
            language = this.language,
            localId = this.localId, // Speichert den kombinierten String "051 / 244"
            imageUrl = this.imageUrl,
            cardMarketLink = this.cardMarketLink,
            ownedCopies = this.ownedCopies,
            notes = this.notes,
            rarity = this.rarity,
            hp = this.hp,
            types = this.types.joinToString(","), // Konvertiert Liste zu kommasepariertem String
            illustrator = this.illustrator,
            stage = this.stage,
            retreatCost = this.retreatCost,
            regulationMark = this.regulationMark,
            currentPrice = this.currentPrice,
            lastPriceUpdate = this.lastPriceUpdate,
            selectedPriceSource = this.selectedPriceSource,
            variantsJson = this.variantsJson,
            abilitiesJson = jsonParser.encodeToString(ListSerializer(Ability.serializer()), this.abilities),
            attacksJson = jsonParser.encodeToString(ListSerializer(Attack.serializer()), this.attacks),
            legalJson = this.legalJson,
            gradedCopiesJson = if (this.gradedCopies.isNotEmpty()) jsonParser.encodeToString(ListSerializer(GradedCopy.serializer()), this.gradedCopies) else null
        )
    }

    override fun getCardInfos(): Flow<List<PokemonCardInfo>> = flow {
        println("Fetching card infos from Supabase...")
            val data = postgrest.from(pokemonCardInfoView)
                .select() // Select all columns from the view
                .decodeList<PokemonCardInfo>() // Directly decode into PokemonCardInfo
            emit(data)
    }

    override suspend fun getFullCardDetails(cardId: Long): PokemonCard? {
        println("Fetching full card details for card ID: $cardId from Supabase...")
        // Fetches the card and the associated set name via a join
        val result2 = postgrest.from(cardsTable)
            .select(
                columns = Columns.list(
                    "*",
                    "SetEntity(nameLocal)"
                )
            ){
                filter {
                    eq("id", cardId)
                }
            }

        val fullCardData = result2.decodeSingleOrNull<FullPokemonCardResponse>()

        return fullCardData?.let { data ->
            val setName = data.setEntity?.nameLocal ?: "Unknown Set"
            // Convert FullPokemonCardResponse to PokemonCard
            PokemonCard(
                id = data.id,
                tcgDexCardId = data.tcgDexCardId,
                nameLocal = data.nameLocal,
                nameEn = data.nameEn,
                language = data.language,
                imageUrl = data.imageUrl,
                cardMarketLink = data.cardMarketLink,
                ownedCopies = data.ownedCopies,
                notes = data.notes,
                setId = data.setId, // Use setId from FullPokemonCardResponse for apiSetId
                setName = setName,
                localId = data.localId,
                currentPrice = data.currentPrice,
                lastPriceUpdate = data.lastPriceUpdate,
                selectedPriceSource = data.selectedPriceSource,
                rarity = data.rarity,
                hp = data.hp,
                types = data.types?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList(),
                illustrator = data.illustrator,
                stage = data.stage,
                retreatCost = data.retreatCost,
                regulationMark = data.regulationMark,
                abilities = data.abilitiesJson?.let { jsonParser.decodeFromString(ListSerializer(Ability.serializer()), it) } ?: emptyList(),
                attacks = data.attacksJson?.let { jsonParser.decodeFromString(ListSerializer(Attack.serializer()), it) } ?: emptyList(),
                variantsJson = data.variantsJson,
                legalJson = data.legalJson,
                gradedCopies = data.gradedCopiesJson?.let { jsonParser.decodeFromString(ListSerializer(GradedCopy.serializer()), it) } ?: emptyList()
            )
        }
    }

    override suspend fun findCardByTcgDexId(tcgDexId: String, language: String): PokemonCardInfo? {
        val data = postgrest.from(pokemonCardInfoView).select() {
            filter {
                eq("tcgDexCardId", tcgDexId)
                eq("language", language)
            }
        }.decodeSingleOrNull<PokemonCardInfo>()
        return data
    }

    override suspend fun insertFullPokemonCard(card: PokemonCard) {
        val supabaseCard = card.toSupabasePokemonCard()
        postgrest.from(cardsTable).insert(supabaseCard)
    }

    override suspend fun updateCardUserData(
        cardId: Long,
        ownedCopies: Int,
        notes: String?,
        currentPrice: Double?,
        lastPriceUpdate: String?,
        selectedPriceSource: String?,
        gradedCopies: List<de.pantastix.project.model.GradedCopy>
    ) {
        postgrest.from(cardsTable).update({
            set("ownedCopies", ownedCopies)
            set("notes", notes)
            set("currentPrice", currentPrice)
            set("lastPriceUpdate", lastPriceUpdate)
            set("selectedPriceSource", selectedPriceSource)
            set("gradedCopiesJson", if (gradedCopies.isNotEmpty()) jsonParser.encodeToString(ListSerializer(GradedCopy.serializer()), gradedCopies) else null)
        }) {
            filter { eq("id", cardId) }
        }
    }

    override suspend fun deleteCardById(cardId: Long) {
        postgrest.from(cardsTable).delete { filter { eq("id", cardId) } }
    }

    override suspend fun clearAllData() {
        println("WARNING: clearAllData called for SupabaseCardRepository.")
    }

    // --- SET-OPERATIONEN---
    override fun getAllSets(): Flow<List<SetInfo>> = _setsFlow.asStateFlow()

    override suspend fun fetchAllSetsOnce(): List<SetInfo> {
        println("Fetching all sets directly from Supabase for migration...")
        return postgrest.from(setsTable).select().decodeList<SetInfo>()
    }

    override suspend fun isSetStorageEmpty(): Boolean {
        return try {
            val result = postgrest.from(setsTable).select {
                count(Count.EXACT)
            }
            val count = result.countOrNull()
            return count == 0L
        } catch (e: Exception) {
            println("Fehler bei der Prüfung, ob die Set-Tabelle leer ist: ${e.message}")
            false
        }
    }

    private suspend fun refreshSets() {
        val data = postgrest.from(setsTable).select().decodeList<SetInfo>()
        _setsFlow.value = data
    }

    override suspend fun syncSets(sets: List<SetInfo>) {
        val oldSetsMap = postgrest.from(setsTable).select().decodeList<SetInfo>().associateBy { it.setId }

        val setsToSave = sets.map { newSet ->
            val existingAbbreviation = oldSetsMap[newSet.setId]?.abbreviation
            if (!existingAbbreviation.isNullOrBlank()) {
                newSet.copy(abbreviation = existingAbbreviation)
            } else {
                newSet
            }
        }

        postgrest.from(setsTable).upsert(setsToSave) {
            onConflict = "setId"
        }

        refreshSets()
    }

    override suspend fun getSetsByOfficialCount(count: Int): List<SetInfo> {
        return postgrest.from(setsTable).select {
            filter {
                eq("cardCountOfficial", count)
            }
        }.decodeList<SetInfo>()
    }

    override suspend fun updateSetAbbreviation(setId: String, abbreviation: String) {
        postgrest.from(setsTable).update({
            set("abbreviation", abbreviation)
        }) {
            filter { eq("setId", setId) }
        }
        refreshSets()
    }

    override suspend fun findExistingCard(setId: String, localId: String, language: String): PokemonCardInfo? = null

    override suspend fun getAllTypeReferences(): List<de.pantastix.project.model.TypeReference> {
        return try {
            postgrest.from("TypeReference").select().decodeList<de.pantastix.project.model.TypeReference>()
        } catch (e: Exception) {
            println("SupabaseCardRepository: Error fetching type references: ${e.message}")
            emptyList()
        }
    }

    override suspend fun searchCards(
        query: String?,
        type: String?,
        sort: String?,
        setId: String?,
        rarity: String?,
        illustrator: String?,
        limit: Int
    ): List<PokemonCardInfo> {
        // Find translations for the provided type
        val searchTerms = if (!type.isNullOrBlank()) {
             val refs = getAllTypeReferences()
             // Try to find a reference where the input type matches ID or any name
             val matchedRef = refs.find { ref ->
                 ref.id.equals(type, ignoreCase = true) || 
                 ref.getAllNames().any { it.equals(type, ignoreCase = true) }
             }
             matchedRef?.getAllNames() ?: listOf(type)
        } else emptyList()

        println("SupabaseCardRepository: Searching for query='$query' types=$searchTerms sort='$sort' setId='$setId' limit=$limit in $cardsTable")
        try {
            val result = postgrest.from(cardsTable).select(
                columns = Columns.list(
                    "id",
                    "tcgDexCardId",
                    "language",
                    "nameLocal",
                    "nameEn",
                    "imageUrl",
                    "ownedCopies",
                    "currentPrice",
                    "selectedPriceSource",
                    "lastPriceUpdate",
                    "SetEntity(nameLocal)"
                )
            ) {
                filter {
                    if (!query.isNullOrBlank()) {
                        or {
                            ilike("nameLocal", "%$query%")
                            ilike("nameEn", "%$query%")
                        }
                    }
                    
                    if (searchTerms.isNotEmpty()) {
                        or {
                            searchTerms.forEach { term ->
                                ilike("types", "%$term%")
                            }
                        }
                    }
                    
                    if (!setId.isNullOrBlank()) eq("setId", setId)
                    if (!rarity.isNullOrBlank()) ilike("rarity", "%$rarity%")
                    if (!illustrator.isNullOrBlank()) ilike("illustrator", "%$illustrator%")
                }
                
                when (sort) {
                    "price_asc" -> order("currentPrice", Order.ASCENDING)
                    "price_desc" -> order("currentPrice", Order.DESCENDING)
                    "name_asc" -> order("nameLocal", Order.ASCENDING)
                    "name_desc" -> order("nameLocal", Order.DESCENDING)
                }
                
                limit(limit.toLong()) 
            }
            
            val jsonElements = result.decodeList<JsonObject>()
            val mapped = jsonElements.map { json ->
                val setObj = json["SetEntity"] as? JsonObject
                val setName = setObj?.get("nameLocal")?.jsonPrimitive?.content ?: "Unknown"
                
                PokemonCardInfo(
                    id = json["id"]!!.jsonPrimitive.long,
                    tcgDexCardId = json["tcgDexCardId"]!!.jsonPrimitive.content,
                    language = json["language"]!!.jsonPrimitive.content,
                    nameLocal = json["nameLocal"]!!.jsonPrimitive.content,
                    setName = setName,
                    imageUrl = json["imageUrl"]?.jsonPrimitive?.contentOrNull,
                    ownedCopies = json["ownedCopies"]!!.jsonPrimitive.int,
                    currentPrice = json["currentPrice"]?.jsonPrimitive?.doubleOrNull,
                    selectedPriceSource = json["selectedPriceSource"]?.jsonPrimitive?.contentOrNull,
                    lastPriceUpdate = json["lastPriceUpdate"]?.jsonPrimitive?.contentOrNull
                )
            }
            return mapped
        } catch (e: Exception) {
            println("SupabaseCardRepository: Error searching cards: ${e.message}")
            return emptyList()
        }
    }

    override suspend fun searchSets(query: String): List<SetInfo> {
        return try {
            postgrest.from(setsTable).select {
                filter {
                    or {
                        ilike("nameLocal", "%$query%")
                        ilike("nameEn", "%$query%")
                        ilike("abbreviation", "%$query%")
                        ilike("setId", "%$query%")
                    }
                }
                limit(10)
            }.decodeList<SetInfo>()
        } catch (e: Exception) {
            println("SupabaseCardRepository: Error searching sets: ${e.message}")
            emptyList()
        }
    }

    // --- Portfolio Snapshots ---

    override suspend fun savePortfolioSnapshot(
        snapshot: de.pantastix.project.model.PortfolioSnapshot,
        items: List<de.pantastix.project.model.PortfolioSnapshotItem>
    ) {
        try {
            postgrest.from("PortfolioSnapshot").upsert(snapshot)
            postgrest.from("PortfolioSnapshotItem").upsert(items)
        } catch (e: Exception) {
            println("SupabaseCardRepository: Error saving snapshot: ${e.message}")
        }
    }

    override suspend fun getAllSnapshots(): List<de.pantastix.project.model.PortfolioSnapshot> {
        return try {
            postgrest.from("PortfolioSnapshot").select().decodeList<de.pantastix.project.model.PortfolioSnapshot>()
        } catch (e: Exception) {
            println("SupabaseCardRepository: Error fetching snapshots: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getSnapshotItems(date: String): List<de.pantastix.project.model.PortfolioSnapshotItem> {
        return try {
            postgrest.from("PortfolioSnapshotItem").select {
                filter { eq("date", date) }
            }.decodeList<de.pantastix.project.model.PortfolioSnapshotItem>()
        } catch (e: Exception) {
            println("SupabaseCardRepository: Error fetching snapshot items: ${e.message}")
            emptyList()
        }
    }

    override suspend fun deleteSnapshot(date: String) {
        try {
            postgrest.from("PortfolioSnapshot").delete {
                filter { eq("date", date) }
            }
        } catch (e: Exception) {
            println("SupabaseCardRepository: Error deleting snapshot: ${e.message}")
        }
    }
}
