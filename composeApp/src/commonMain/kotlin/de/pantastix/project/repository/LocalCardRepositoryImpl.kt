package de.pantastix.project.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import de.pantastix.project.coroutines.ioDispatcher
import de.pantastix.project.db.cards.CardDatabaseQueries
import de.pantastix.project.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class LocalCardRepositoryImpl(
    private val queries: CardDatabaseQueries // Wird von Koin injiziert
) : CardRepository {

    private val jsonParser = Json { ignoreUnknownKeys = true }

    override fun getAllSets(): Flow<List<SetInfo>> {
        return queries.selectAllSets()
            .asFlow()
            .mapToList(ioDispatcher)
            .map { entities ->
                entities.map { it.toSetInfo() }
            }
    }

    override suspend fun fetchAllSetsOnce(): List<SetInfo> {
        return withContext(ioDispatcher) {
            queries.selectAllSets().executeAsList().map { it.toSetInfo() }
        }
    }

    override suspend fun isSetStorageEmpty(): Boolean {
        return withContext(ioDispatcher) {
            queries.selectAllSets().executeAsList().isEmpty()
        }
    }

    override suspend fun syncSets(sets: List<SetInfo>) {
        withContext(ioDispatcher) {
            queries.transaction {
                sets.forEach { setInfo ->
                    val existingSet = queries.selectSetById(setInfo.setId).executeAsOneOrNull()
                    val existingAbbr = existingSet?.abbreviation
                    val existingReleaseDate = existingSet?.releaseDate
                    
                    queries.insertOrReplaceSet(
                        id = setInfo.id.toLong(),
                        setId = setInfo.setId,
                        abbreviation = existingAbbr ?: setInfo.abbreviation,
                        nameLocal = setInfo.nameLocal,
                        nameEn = setInfo.nameEn,
                        logoUrl = setInfo.logoUrl,
                        cardCountOfficial = setInfo.cardCountOfficial.toLong(),
                        cardCountTotal = setInfo.cardCountTotal.toLong(),
                        releaseDate = existingReleaseDate ?: setInfo.releaseDate
                    )
                }
            }
        }
    }

    override suspend fun updateSetAbbreviation(setId: String, abbreviation: String) {
        withContext(ioDispatcher) {
            queries.updateSetAbbreviation(abbreviation = abbreviation, setId = setId)
        }
    }

    override suspend fun updateSetDetails(setId: String, abbreviation: String?, releaseDate: String?) {
        withContext(ioDispatcher) {
            queries.updateSetDetails(abbreviation = abbreviation, releaseDate = releaseDate, setId = setId)
        }
    }

    override suspend fun getSetsByOfficialCount(count: Int): List<SetInfo> {
        return withContext(ioDispatcher) {
            queries.selectSetsByOfficialCount(count.toLong()).executeAsList().map { it.toSetInfo() }
        }
    }

    override suspend fun getSetByAbbreviation(abbreviation: String): SetInfo? {
        return withContext(ioDispatcher) {
            queries.selectSetByAbbreviation(abbreviation).executeAsOneOrNull()?.toSetInfo()
        }
    }

    private fun de.pantastix.project.db.cards.SetEntity.toSetInfo(): SetInfo {
        return SetInfo(
            id = id.toInt(),
            setId = setId,
            abbreviation = abbreviation,
            nameLocal = nameLocal,
            nameEn = nameEn,
            logoUrl = logoUrl,
            cardCountOfficial = cardCountOfficial.toInt(),
            cardCountTotal = cardCountTotal.toInt(),
            releaseDate = releaseDate
        )
    }

    // --- Pokémon-Karten-Implementierungen ---

    override fun getCardInfos(): Flow<List<PokemonCardInfo>> {
        return queries.selectAllCardInfos()
            .asFlow()
            .mapToList(ioDispatcher)
            .map { results ->
                results.map { result ->
                    PokemonCardInfo(
                        id = result.id,
                        tcgDexCardId = result.tcgDexCardId,
                        setId = result.setId,
                        language = result.language,
                        nameLocal = result.nameLocal,
                        setName = result.setName,
                        imageUrl = result.imageUrl,
                        ownedCopies = result.ownedCopies.toInt(),
                        currentPrice = result.currentPrice,
                        selectedPriceSource = result.selectedPriceSource,
                        lastPriceUpdate = result.lastPriceUpdate
                    )
                }
            }
    }

    override suspend fun getFullCardDetails(cardId: Long): PokemonCard? {
        return withContext(ioDispatcher) {
            val entity = queries.selectFullCardDetailsById(cardId).executeAsOneOrNull()
            entity?.let {
                val abilities = it.abilitiesJson?.let { json -> jsonParser.decodeFromString<List<Ability>>(json) } ?: emptyList()
                val attacks = it.attacksJson?.let { json -> jsonParser.decodeFromString<List<Attack>>(json) } ?: emptyList()
                PokemonCard(
                    id = it.id,
                    tcgDexCardId = it.tcgDexCardId,
                    nameLocal = it.nameLocal,
                    nameEn = it.nameEn,
                    language = it.language,
                    imageUrl = it.imageUrl,
                    cardMarketLink = it.cardMarketLink,
                    ownedCopies = it.ownedCopies.toInt(),
                    notes = it.notes,
                    currentPrice = it.currentPrice,
                    lastPriceUpdate = it.lastPriceUpdate,
                    selectedPriceSource = it.selectedPriceSource,
                    setId = it.setId,
                    setName = it.setName,
                    localId = "${it.localId} / ${it.cardCountTotal}",
                    rarity = it.rarity,
                    hp = it.hp?.toInt(),
                    types = it.types?.split(',') ?: emptyList(),
                    illustrator = it.illustrator,
                    stage = it.stage,
                    retreatCost = it.retreatCost?.toInt(),
                    regulationMark = it.regulationMark,
                    abilities = abilities,
                    attacks = attacks,
                    variantsJson = it.variantsJson,
                    legalJson = it.legalJson,
                    gradedCopies = it.gradedCopiesJson?.let { json -> jsonParser.decodeFromString(json) } ?: emptyList()
                )
            }
        }
    }

    override suspend fun findCardByTcgDexId(tcgDexId: String, language: String): PokemonCardInfo? {
        return withContext(ioDispatcher) {
            queries.findCardByTcgDexIdAndLanguage(tcgDexId, language).executeAsOneOrNull()?.let { entity ->
                PokemonCardInfo(
                    id = entity.id,
                    tcgDexCardId = entity.tcgDexCardId,
                    setId = entity.setId,
                    language = entity.language,
                    nameLocal = entity.nameLocal,
                    setName = "", 
                    imageUrl = entity.imageUrl,
                    ownedCopies = entity.ownedCopies.toInt(),
                    currentPrice = entity.currentPrice,
                    selectedPriceSource = entity.selectedPriceSource,
                    lastPriceUpdate = entity.lastPriceUpdate
                )
            }
        }
    }

    override suspend fun insertFullPokemonCard(card: PokemonCard) {
        withContext(ioDispatcher) {
            queries.insertCard(
                setId = card.setId,
                tcgDexCardId = card.tcgDexCardId,
                nameLocal = card.nameLocal,
                nameEn = card.nameEn,
                language = card.language,
                localId = card.localId.split(" / ").firstOrNull() ?: "",
                imageUrl = card.imageUrl,
                cardMarketLink = card.cardMarketLink,
                ownedCopies = card.ownedCopies.toLong(),
                notes = card.notes,
                rarity = card.rarity,
                hp = card.hp?.toLong(),
                types = card.types.joinToString(","),
                illustrator = card.illustrator,
                stage = card.stage,
                retreatCost = card.retreatCost?.toLong(),
                regulationMark = card.regulationMark,
                currentPrice = card.currentPrice,
                lastPriceUpdate = card.lastPriceUpdate,
                selectedPriceSource = card.selectedPriceSource,
                variantsJson = card.variantsJson,
                abilitiesJson = Json.encodeToString(ListSerializer(Ability.serializer()), card.abilities),
                attacksJson = Json.encodeToString(ListSerializer(Attack.serializer()), card.attacks),
                legalJson = card.legalJson,
                gradedCopiesJson = if (card.gradedCopies.isNotEmpty()) Json.encodeToString(card.gradedCopies) else null
            )
        }
    }

    override suspend fun findExistingCard(setId: String, localId: String, language: String): PokemonCardInfo? {
        return withContext(ioDispatcher) {
            queries.findCardBySetAndLocalIdAndLanguage(setId, localId, language).executeAsOneOrNull()?.let { entity ->
                PokemonCardInfo(
                    id = entity.id,
                    tcgDexCardId = entity.tcgDexCardId,
                    setId = entity.setId,
                    language = entity.language,
                    nameLocal = entity.nameLocal,
                    setName = "", 
                    imageUrl = entity.imageUrl,
                    ownedCopies = entity.ownedCopies.toInt(),
                    currentPrice = entity.currentPrice,
                    selectedPriceSource = entity.selectedPriceSource,
                    lastPriceUpdate = entity.lastPriceUpdate
                )
            }
        }
    }

    override suspend fun getAllTypeReferences(): List<de.pantastix.project.model.TypeReference> {
        return withContext(ioDispatcher) {
            queries.selectAllTypes().executeAsList().map { entity ->
                de.pantastix.project.model.TypeReference(
                    id = entity.id,
                    name_de = entity.name_de,
                    name_en = entity.name_en,
                    name_fr = entity.name_fr,
                    name_es = entity.name_es,
                    name_it = entity.name_it,
                    name_pt = entity.name_pt,
                    name_jp = entity.name_jp
                )
            }
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
        return withContext(ioDispatcher) {
            val typeSearchTerms = if (!type.isNullOrBlank()) {
                val refs = getAllTypeReferences()
                val matchedRef = refs.find { ref ->
                    ref.id.equals(type, ignoreCase = true) || 
                    ref.getAllNames().any { it.equals(type, ignoreCase = true) }
                }
                matchedRef?.getAllNames() ?: listOf(type)
            } else null

            val results = queries.advancedSearch(
                query = query,
                setId = setId,
                type = null, 
                rarity = rarity,
                illustrator = illustrator
            ).executeAsList().filter { entity ->
                if (typeSearchTerms == null) return@filter true
                val cardTypes = entity.types?.split(",")?.map { it.trim().lowercase() } ?: emptyList()
                typeSearchTerms.any { term -> cardTypes.contains(term.lowercase()) }
            }.map { result ->
                PokemonCardInfo(
                    id = result.id,
                    tcgDexCardId = result.tcgDexCardId,
                    setId = result.setId,
                    language = result.language,
                    nameLocal = result.nameLocal,
                    setName = result.setName,
                    imageUrl = result.imageUrl,
                    ownedCopies = result.ownedCopies.toInt(),
                    currentPrice = result.currentPrice,
                    selectedPriceSource = result.selectedPriceSource,
                    lastPriceUpdate = result.lastPriceUpdate
                )
            }
            
            val sorted = when(sort) {
                "name_asc" -> results.sortedBy { it.nameLocal }
                "name_desc" -> results.sortedByDescending { it.nameLocal }
                "price_asc" -> results.sortedBy { it.currentPrice ?: 0.0 }
                "price_desc" -> results.sortedByDescending { it.currentPrice ?: 0.0 }
                else -> results 
            }
            
            sorted.take(limit)
        }
    }

    override suspend fun searchSets(query: String): List<SetInfo> {
        return withContext(ioDispatcher) {
            queries.searchSets(query).executeAsList().map { it.toSetInfo() }
        }
    }

    override suspend fun getSetProgressList(): List<de.pantastix.project.model.SetProgress> {
        return withContext(ioDispatcher) {
            queries.getSetProgress().executeAsList().map { result ->
                de.pantastix.project.model.SetProgress(
                    setId = result.setId,
                    name = result.nameLocal,
                    logoUrl = result.logoUrl,
                    cardCountOfficial = result.cardCountOfficial.toInt(),
                    releaseDate = result.releaseDate,
                    ownedUniqueCount = result.ownedUniqueCount,
                    totalPhysicalCount = result.totalPhysicalCount?.toLong() ?: 0L,
                    artRarePlusCount = result.artRarePlusCount?.toLong() ?: 0L,
                    totalValue = result.totalValue ?: 0.0
                )
            }
        }
    }

    override suspend fun getCardsBySet(setId: String): List<PokemonCardInfo> {
        return withContext(ioDispatcher) {
            queries.selectCardsBySet(setId).executeAsList().map { result ->
                PokemonCardInfo(
                    id = result.id,
                    tcgDexCardId = result.tcgDexCardId,
                    setId = result.setId,
                    language = result.language,
                    nameLocal = result.nameLocal,
                    setName = result.setName,
                    imageUrl = result.imageUrl,
                    ownedCopies = result.ownedCopies.toInt(),
                    currentPrice = result.currentPrice,
                    selectedPriceSource = result.selectedPriceSource,
                    lastPriceUpdate = result.lastPriceUpdate
                )
            }
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
        withContext(ioDispatcher) {
            queries.updateCardUserData(
                ownedCopies = ownedCopies.toLong(),
                notes = notes,
                currentPrice = currentPrice,
                lastPriceUpdate = lastPriceUpdate,
                selectedPriceSource = selectedPriceSource,
                gradedCopiesJson = if (gradedCopies.isNotEmpty()) Json.encodeToString(gradedCopies) else null,
                id = cardId
            )
        }
    }

    override suspend fun deleteCardById(cardId: Long) {
        withContext(ioDispatcher) {
            queries.deleteCardById(cardId)
        }
    }

    override suspend fun clearAllData() {
        withContext(ioDispatcher) {
            queries.transaction {
                queries.clearAllPokemonCards()
                queries.clearAllSets()
            }
        }
    }

    // --- Snapshots ---

    override suspend fun savePortfolioSnapshot(
        snapshot: de.pantastix.project.model.PortfolioSnapshot,
        items: List<de.pantastix.project.model.PortfolioSnapshotItem>
    ) {
        withContext(ioDispatcher) {
            queries.transaction {
                queries.deleteSnapshotItemsByDate(snapshot.date)
                queries.insertSnapshot(
                    date = snapshot.date,
                    totalValue = snapshot.totalValue,
                    totalRawValue = snapshot.totalRawValue,
                    totalGradedValue = snapshot.totalGradedValue,
                    cardCount = snapshot.cardCount.toLong(),
                    updatedAt = snapshot.updatedAt
                )
                items.forEach { item ->
                    queries.insertSnapshotItem(
                        date = item.date,
                        cardId = item.cardId,
                        nameLocal = item.nameLocal,
                        setName = item.setName,
                        imageUrl = item.imageUrl,
                        rawPrice = item.rawPrice,
                        rowCount = item.rowCount.toLong(),
                        gradedCopiesJson = item.gradedCopiesJson
                    )
                }
            }
        }
    }

    override suspend fun getAllSnapshots(): List<de.pantastix.project.model.PortfolioSnapshot> {
        return withContext(ioDispatcher) {
            queries.getSnapshots().executeAsList().map { 
                de.pantastix.project.model.PortfolioSnapshot(
                    date = it.date,
                    totalValue = it.totalValue,
                    totalRawValue = it.totalRawValue,
                    totalGradedValue = it.totalGradedValue,
                    cardCount = it.cardCount.toInt(),
                    updatedAt = it.updatedAt
                )
            }
        }
    }

    override suspend fun getSnapshotItems(date: String): List<de.pantastix.project.model.PortfolioSnapshotItem> {
        return withContext(ioDispatcher) {
            queries.getSnapshotItems(date).executeAsList().map {
                de.pantastix.project.model.PortfolioSnapshotItem(
                    date = it.date,
                    cardId = it.cardId,
                    nameLocal = it.nameLocal,
                    setName = it.setName,
                    imageUrl = it.imageUrl,
                    rawPrice = it.rawPrice,
                    rowCount = it.rowCount.toInt(),
                    gradedCopiesJson = it.gradedCopiesJson
                )
            }
        }
    }

    override suspend fun deleteSnapshot(date: String) {
        withContext(ioDispatcher) {
            queries.deleteSnapshot(date)
        }
    }
}
