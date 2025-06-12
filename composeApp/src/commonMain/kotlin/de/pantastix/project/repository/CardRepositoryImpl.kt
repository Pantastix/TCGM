package de.pantastix.project.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import de.pantastix.project.coroutines.ioDispatcher
import de.pantastix.project.db.CardDatabaseQueries
import de.pantastix.project.model.Ability
import de.pantastix.project.model.Attack
import de.pantastix.project.model.PokemonCard
import de.pantastix.project.model.PokemonCardInfo
import de.pantastix.project.model.SetInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class CardRepositoryImpl(
    private val queries: CardDatabaseQueries // Wird von Koin injiziert
) : CardRepository {

    // JSON-Parser, der tolerant gegenüber unbekannten Feldern ist.
    private val jsonParser = Json { ignoreUnknownKeys = true }

    // --- Set-Implementierungen ---

    override fun getAllSets(): Flow<List<SetInfo>> {
        return queries.selectAllSets()
            .asFlow()
            .mapToList(ioDispatcher)
            .map { setEntities ->
                setEntities.map { entity ->
                    SetInfo(
                        setId = entity.setId,
                        nameDe = entity.nameDe,
                        nameEn = entity.nameEn,
                        logoUrl = entity.logoUrl
                    )
                }
            }
    }

    override suspend fun syncSets(sets: List<SetInfo>) {
        withContext(ioDispatcher) {
            queries.transaction {
                sets.forEach { setInfo ->
                    queries.insertOrReplaceSet(
                        setId = setInfo.setId,
                        nameDe = setInfo.nameDe,
                        nameEn = setInfo.nameEn,
                        logoUrl = setInfo.logoUrl,
                        // Diese Werte müssen wir später aus der API holen und hier übergeben
                        cardCountOfficial = 0,
                        cardCountTotal = 0,
                        releaseDate = null
                    )
                }
            }
        }
    }

    // --- Pokémon-Karten-Implementierungen ---

    override fun getCardInfos(): Flow<List<PokemonCardInfo>> {
        return queries.selectAllCardInfos() // Nutzt die optimierte Abfrage für die Liste
            .asFlow()
            .mapToList(ioDispatcher)
            .map { joinedResults ->
                joinedResults.map { result ->
                    PokemonCardInfo(
                        id = result.id,
                        nameDe = result.nameDe,
                        setName = result.setName,
                        imageUrl = result.imageUrl,
                        ownedCopies = result.ownedCopies.toInt(),
                        currentPrice = result.currentPrice
                    )
                }
            }
    }

    override suspend fun getFullCardDetails(cardId: Long): PokemonCard? {
        return withContext(ioDispatcher) {
            val entity = queries.selectFullCardDetailsById(cardId).executeAsOneOrNull()

            entity?.let {
                // Hier parsen wir die JSON-Strings zurück in unsere Kotlin-Datenklassen
                val abilities = it.abilitiesJson?.let { json ->
                    jsonParser.decodeFromString<List<Ability>>(json)
                } ?: emptyList()

                val attacks = it.attacksJson?.let { json ->
                    jsonParser.decodeFromString<List<Attack>>(json)
                } ?: emptyList()

                // Mappen auf unser vollständiges "PokemonCard"-Domain-Modell
                PokemonCard(
                    id = it.id,
                    tcgDexCardId = it.tcgDexCardId,
                    nameDe = it.nameDe,
                    nameEn = it.nameEn,
                    imageUrl = it.imageUrl,
                    cardMarketLink = it.cardMarketLink,
                    ownedCopies = it.ownedCopies.toInt(),
                    notes = it.notes,
                    currentPrice = it.currentPrice,
                    lastPriceUpdate = it.lastPriceUpdate,
                    setName = it.setNameDe, // kommt aus dem JOIN mit SetEntity
                    localId = "${it.localId} / ${it.cardCountTotal}", // Setzen die Kartennummer zusammen
                    rarity = it.rarity,
                    hp = it.hp?.toInt(),
                    types = it.types?.split(',') ?: emptyList(), // Erzeugt eine Liste aus dem String
                    illustrator = it.illustrator,
                    stage = it.stage,
                    retreatCost = it.retreatCost?.toInt(),
                    regulationMark = it.regulationMark,
                    abilities = abilities,
                    attacks = attacks
                )
            }
        }
    }

    override suspend fun findCardByTcgDexId(tcgDexId: String): PokemonCardInfo? {
        return withContext(ioDispatcher) {
            queries.selectCardByTcgDexId(tcgDexId).executeAsOneOrNull()?.let {
                PokemonCardInfo(it.id, it.nameDe, "", it.imageUrl, it.ownedCopies.toInt(), it.currentPrice)
            }
        }
    }

    // <<< KORRIGIERTE SIGNATUR HIER >>>
    override suspend fun insertFullPokemonCard(
        setId: String, tcgDexCardId: String, nameDe: String, nameEn: String, localId: String,
        imageUrl: String?, cardMarketLink: String?, ownedCopies: Int, notes: String?,
        rarity: String?, hp: Int?, types: String?, illustrator: String?, stage: String?,
        retreatCost: Int?, regulationMark: String?,
        currentPrice: Double?, lastPriceUpdate: String?, // Diese Parameter waren im Interface, aber nicht hier
        variantsJson: String?, abilitiesJson: String?, attacksJson: String?, legalJson: String?
    ) {
        withContext(ioDispatcher) {
            queries.insertCard(
                setId = setId, tcgDexCardId = tcgDexCardId, nameDe = nameDe, nameEn = nameEn,
                localId = localId, imageUrl = imageUrl, cardMarketLink = cardMarketLink,
                ownedCopies = ownedCopies.toLong(), notes = notes, rarity = rarity, hp = hp?.toLong(),
                types = types, illustrator = illustrator, stage = stage, retreatCost = retreatCost?.toLong(),
                regulationMark = regulationMark,
                currentPrice = currentPrice,
                lastPriceUpdate = lastPriceUpdate,
                variantsJson = variantsJson,
                abilitiesJson = abilitiesJson, attacksJson = attacksJson, legalJson = legalJson
            )
        }
    }

    // <<< KORRIGIERTE SIGNATUR HIER >>>
    override suspend fun updateCardUserData(cardId: Long, ownedCopies: Int, notes: String?, currentPrice: Double?, lastPriceUpdate: String?) {
        withContext(ioDispatcher) {
            queries.updateCardUserData(
                id = cardId,
                ownedCopies = ownedCopies.toLong(),
                notes = notes,
                currentPrice = currentPrice,
                lastPriceUpdate = lastPriceUpdate
            )
        }
    }

    override suspend fun deleteCardById(cardId: Long) {
        withContext(ioDispatcher) {
            queries.deleteCardById(cardId)
        }
    }
}