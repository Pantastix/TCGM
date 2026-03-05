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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*

class SupabaseCardRepository(
    private val postgrest: Postgrest
) : CardRepository {

    private val jsonParser = Json { ignoreUnknownKeys = true }

    private val cardsTable = "PokemonCardEntity" 
    private val setsTable = "SetEntity"
    private val _setsFlow = MutableStateFlow<List<SetInfo>>(emptyList())

    private fun PokemonCard.toSupabasePokemonCard(): SupabasePokemonCard {
        return SupabasePokemonCard(
            id = this.id,
            setId = this.setId,
            tcgDexCardId = this.tcgDexCardId,
            nameLocal = this.nameLocal,
            nameEn = this.nameEn,
            language = this.language,
            localId = this.localId, 
            imageUrl = this.imageUrl,
            cardMarketLink = this.cardMarketLink,
            ownedCopies = this.ownedCopies,
            notes = this.notes,
            rarity = this.rarity,
            hp = this.hp,
            types = this.types.joinToString(","),
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
        println("SupabaseCardRepository: Fetching card infos...")
        val data = try {
            val response = postgrest.from(cardsTable).select(
                columns = Columns.list(
                    "id", "tcgDexCardId", "setId", "language", "nameLocal", "imageUrl", 
                    "ownedCopies", "currentPrice", "selectedPriceSource", "lastPriceUpdate",
                    "SetEntity(nameLocal)"
                )
            )
            
            val jsonElements = response.decodeList<JsonObject>()
            jsonElements.map { json ->
                val setObj = json["SetEntity"] as? JsonObject
                val setName = setObj?.get("nameLocal")?.jsonPrimitive?.content ?: "Unknown Set"
                
                PokemonCardInfo(
                    id = json["id"]?.jsonPrimitive?.long ?: 0L,
                    tcgDexCardId = json["tcgDexCardId"]?.jsonPrimitive?.content ?: "",
                    setId = json["setId"]?.jsonPrimitive?.content ?: "",
                    language = json["language"]?.jsonPrimitive?.content ?: "de",
                    nameLocal = json["nameLocal"]?.jsonPrimitive?.content ?: "Unknown",
                    setName = setName,
                    imageUrl = json["imageUrl"]?.jsonPrimitive?.contentOrNull,
                    ownedCopies = json["ownedCopies"]?.jsonPrimitive?.int ?: 1,
                    currentPrice = json["currentPrice"]?.jsonPrimitive?.doubleOrNull,
                    selectedPriceSource = json["selectedPriceSource"]?.jsonPrimitive?.contentOrNull,
                    lastPriceUpdate = json["lastPriceUpdate"]?.jsonPrimitive?.contentOrNull
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            println("SupabaseCardRepository: Error fetching card infos: ${e.message}")
            emptyList()
        }
        emit(data)
    }

    override suspend fun getFullCardDetails(cardId: Long): PokemonCard? {
        return try {
            val response = postgrest.from(cardsTable)
                .select(columns = Columns.list("*", "SetEntity(nameLocal)")) {
                    filter { eq("id", cardId) }
                }

            val fullCardData = response.decodeSingleOrNull<FullPokemonCardResponse>()

            fullCardData?.let { data ->
                val setName = data.setEntity?.nameLocal ?: "Unknown Set"
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
                    setId = data.setId,
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
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            println("SupabaseCardRepository: Error fetching full details: ${e.message}")
            null
        }
    }

    override suspend fun findCardByTcgDexId(tcgDexId: String, language: String): PokemonCardInfo? {
        return try {
            getCardInfos().first().find { it.tcgDexCardId == tcgDexId && it.language == language }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    override suspend fun insertFullPokemonCard(card: PokemonCard) {
        try {
            val supabaseCard = card.toSupabasePokemonCard()
            postgrest.from(cardsTable).insert(supabaseCard)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            println("SupabaseCardRepository: Error inserting card: ${e.message}")
        }
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
        try {
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
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            println("SupabaseCardRepository: Error updating card: ${e.message}")
        }
    }

    override suspend fun deleteCardById(cardId: Long) {
        try {
            postgrest.from(cardsTable).delete { filter { eq("id", cardId) } }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            println("SupabaseCardRepository: Error deleting card: ${e.message}")
        }
    }

    override suspend fun clearAllData() {
        println("WARNING: clearAllData called for SupabaseCardRepository.")
    }

    override fun getAllSets(): Flow<List<SetInfo>> = _setsFlow.asStateFlow()

    override suspend fun fetchAllSetsOnce(): List<SetInfo> {
        return try {
            postgrest.from(setsTable).select().decodeList<SetInfo>()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            println("SupabaseCardRepository: Error fetching sets: ${e.message}")
            emptyList()
        }
    }

    override suspend fun isSetStorageEmpty(): Boolean {
        return try {
            val result = postgrest.from(setsTable).select {
                count(Count.EXACT)
            }
            val count = result.countOrNull()
            count == 0L
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            false
        }
    }

    private suspend fun refreshSets() {
        try {
            val data = postgrest.from(setsTable).select().decodeList<SetInfo>()
            _setsFlow.value = data
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }

    override suspend fun syncSets(sets: List<SetInfo>) {
        try {
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
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            println("SupabaseCardRepository: Error syncing sets: ${e.message}")
        }
    }

    override suspend fun getSetsByOfficialCount(count: Int): List<SetInfo> {
        return try {
            postgrest.from(setsTable).select {
                filter { eq("cardCountOfficial", count) }
            }.decodeList<SetInfo>()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    override suspend fun updateSetAbbreviation(setId: String, abbreviation: String) {
        try {
            postgrest.from(setsTable).update({
                set("abbreviation", abbreviation)
            }) {
                filter { eq("setId", setId) }
            }
            refreshSets()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }

    override suspend fun findExistingCard(setId: String, localId: String, language: String): PokemonCardInfo? {
        return try {
            getCardInfos().first().find { it.setId == setId && it.tcgDexCardId.endsWith(localId) && it.language == language }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
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
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    override suspend fun getSetProgressList(): List<de.pantastix.project.model.SetProgress> {
        return try {
            val allSets = fetchAllSetsOnce()
            val allCards = getCardInfos().first()
            
            allSets.map { set ->
                val setCards = allCards.filter { it.setId == set.setId }
                de.pantastix.project.model.SetProgress(
                    setId = set.setId,
                    name = set.nameLocal,
                    logoUrl = set.logoUrl,
                    cardCountOfficial = set.cardCountOfficial,
                    releaseDate = set.releaseDate,
                    ownedUniqueCount = setCards.size.toLong(),
                    totalPhysicalCount = setCards.sumOf { it.ownedCopies }.toLong(),
                    artRarePlusCount = 0L, 
                    totalValue = setCards.sumOf { (it.currentPrice ?: 0.0) * it.ownedCopies }
                )
            }.filter { it.ownedUniqueCount > 0 }.sortedByDescending { it.releaseDate }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            println("SupabaseCardRepository: Error calculating set progress: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getCardsBySet(setId: String): List<PokemonCardInfo> {
        return searchCards(query = null, setId = setId, limit = 1000)
    }

    override suspend fun getAllTypeReferences(): List<de.pantastix.project.model.TypeReference> {
        return try {
            postgrest.from("TypeReference").select().decodeList<de.pantastix.project.model.TypeReference>()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
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
        val searchTerms = if (!type.isNullOrBlank()) {
             val refs = try { getAllTypeReferences() } catch(e: Exception) { emptyList() }
             val matchedRef = refs.find { ref ->
                 ref.id.equals(type, ignoreCase = true) || 
                 ref.getAllNames().any { it.equals(type, ignoreCase = true) }
             }
             matchedRef?.getAllNames() ?: listOf(type)
        } else emptyList()

        return try {
            val response = postgrest.from(cardsTable).select(
                columns = Columns.list(
                    "id", "tcgDexCardId", "language", "nameLocal", "imageUrl", 
                    "ownedCopies", "currentPrice", "selectedPriceSource", "lastPriceUpdate", "setId",
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
                        or { searchTerms.forEach { ilike("types", "%$it%") } }
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
            
            val jsonElements = response.decodeList<JsonObject>()
            jsonElements.map { json ->
                val setObj = json["SetEntity"] as? JsonObject
                val setName = setObj?.get("nameLocal")?.jsonPrimitive?.content ?: "Unknown Set"
                
                PokemonCardInfo(
                    id = json["id"]?.jsonPrimitive?.long ?: 0L,
                    tcgDexCardId = json["tcgDexCardId"]?.jsonPrimitive?.content ?: "",
                    setId = json["setId"]?.jsonPrimitive?.content ?: "",
                    language = json["language"]?.jsonPrimitive?.content ?: "de",
                    nameLocal = json["nameLocal"]?.jsonPrimitive?.content ?: "Unknown",
                    setName = setName,
                    imageUrl = json["imageUrl"]?.jsonPrimitive?.contentOrNull,
                    ownedCopies = json["ownedCopies"]?.jsonPrimitive?.int ?: 1,
                    currentPrice = json["currentPrice"]?.jsonPrimitive?.doubleOrNull,
                    selectedPriceSource = json["selectedPriceSource"]?.jsonPrimitive?.contentOrNull,
                    lastPriceUpdate = json["lastPriceUpdate"]?.jsonPrimitive?.contentOrNull
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            println("SupabaseCardRepository: Error searching cards: ${e.message}")
            emptyList()
        }
    }

    override suspend fun savePortfolioSnapshot(
        snapshot: de.pantastix.project.model.PortfolioSnapshot,
        items: List<de.pantastix.project.model.PortfolioSnapshotItem>
    ) {
        try {
            postgrest.from("PortfolioSnapshot").upsert(snapshot)
            postgrest.from("PortfolioSnapshotItem").upsert(items)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }

    override suspend fun getAllSnapshots(): List<de.pantastix.project.model.PortfolioSnapshot> {
        return try {
            postgrest.from("PortfolioSnapshot").select().decodeList<de.pantastix.project.model.PortfolioSnapshot>()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    override suspend fun getSnapshotItems(date: String): List<de.pantastix.project.model.PortfolioSnapshotItem> {
        return try {
            postgrest.from("PortfolioSnapshotItem").select {
                filter { eq("date", date) }
            }.decodeList<de.pantastix.project.model.PortfolioSnapshotItem>()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    override suspend fun deleteSnapshot(date: String) {
        try {
            postgrest.from("PortfolioSnapshot").delete {
                filter { eq("date", date) }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }
}
