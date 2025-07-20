package de.pantastix.project.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import de.pantastix.project.coroutines.ioDispatcher
import de.pantastix.project.db.cards.CardDatabaseQueries
import de.pantastix.project.model.Ability
import de.pantastix.project.model.Attack
import de.pantastix.project.model.PokemonCard
import de.pantastix.project.model.PokemonCardInfo
import de.pantastix.project.model.SetInfo
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
                entities.map { entity ->
                    // Mappt die Datenbank-Entität auf das SetInfo-Datenmodell
                    SetInfo(
                        setId = entity.setId,
                        tcgIoSetId = entity.tcgIoSetId, // NEU: Liest das neue Feld aus
                        abbreviation = entity.abbreviation,
                        nameLocal = entity.nameLocal,
                        nameEn = entity.nameEn,
                        logoUrl = entity.logoUrl,
                        cardCountOfficial = entity.cardCountOfficial.toInt(),
                        cardCountTotal = entity.cardCountTotal.toInt(),
                        releaseDate = entity.releaseDate
                    )
                }
            }
    }

    override suspend fun syncSets(sets: List<SetInfo>) {
        withContext(ioDispatcher) {
            queries.transaction {
                sets.forEach { setInfo ->
                    // Behalte das vorhandene Kürzel, falls es schon existiert
                    val existingAbbr = queries.selectSetById(setInfo.setId).executeAsOneOrNull()?.abbreviation
                    queries.insertOrReplaceSet(
                        setId = setInfo.setId,
                        tcgIoSetId = setInfo.tcgIoSetId, // NEU: Schreibt das neue Feld in die DB
                        abbreviation = existingAbbr ?: setInfo.abbreviation,
                        nameLocal = setInfo.nameLocal,
                        nameEn = setInfo.nameEn,
                        logoUrl = setInfo.logoUrl,
                        cardCountOfficial = setInfo.cardCountOfficial.toLong(),
                        cardCountTotal = setInfo.cardCountTotal.toLong(),
                        releaseDate = setInfo.releaseDate
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
                        language = result.language,
                        nameLocal = result.nameLocal,
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
                val abilities = it.abilitiesJson?.let { json -> jsonParser.decodeFromString<List<Ability>>(json) } ?: emptyList()
                val attacks = it.attacksJson?.let { json -> jsonParser.decodeFromString<List<Attack>>(json) } ?: emptyList()
                PokemonCard(
                    id = it.id,
                    tcgDexCardId = it.tcgDexCardId,
                    nameLocal = it.nameLocal, // KORRIGIERT
                    nameEn = it.nameEn,
                    language = it.language,
                    imageUrl = it.imageUrl,
                    cardMarketLink = it.cardMarketLink,
                    ownedCopies = it.ownedCopies.toInt(),
                    notes = it.notes,
                    currentPrice = it.currentPrice,
                    lastPriceUpdate = it.lastPriceUpdate,
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
                    attacks = attacks
                )
            }
        }
    }

    override suspend fun findCardByTcgDexId(tcgDexId: String, language: String): PokemonCardInfo? {
        return withContext(ioDispatcher) {
            queries.findCardByTcgDexIdAndLanguage(tcgDexId, language).executeAsOneOrNull()?.let { entity ->
                // Mappen auf das Info-Objekt für den Check im ViewModel.
                PokemonCardInfo(
                    id = entity.id,
                    tcgDexCardId = entity.tcgDexCardId,
                    language = entity.language,
                    nameLocal = entity.nameLocal,
                    setName = "", // Nicht relevant für diesen Check
                    imageUrl = entity.imageUrl,
                    ownedCopies = entity.ownedCopies.toInt(),
                    currentPrice = entity.currentPrice
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
                variantsJson = card.variantsJson, // Muss aus einem passenden Feld im Modell kommen
                abilitiesJson = Json.encodeToString(ListSerializer(Ability.serializer()), card.abilities),
                attacksJson = Json.encodeToString(ListSerializer(Attack.serializer()), card.attacks),
                legalJson = card.legalJson // Muss aus einem passenden Feld im Modell kommen
            )
        }
    }

    override suspend fun findExistingCard(setId: String, localId: String, language: String): PokemonCardInfo? {
        return withContext(ioDispatcher) {
            queries.findCardBySetAndLocalIdAndLanguage(setId, localId, language).executeAsOneOrNull()?.let { entity ->
                // Wir mappen das Ergebnis auf PokemonCardInfo. Alle Felder sind für den Check verfügbar.
                PokemonCardInfo(
                    id = entity.id,
                    tcgDexCardId = entity.tcgDexCardId,
                    language = entity.language,
                    nameLocal = entity.nameLocal,
                    setName = "", // Nicht nötig für diesen Check
                    imageUrl = entity.imageUrl,
                    ownedCopies = entity.ownedCopies.toInt(),
                    currentPrice = entity.currentPrice
                )
            }
        }
    }

    override suspend fun updateCardUserData(
        cardId: Long, ownedCopies: Int, notes: String?, currentPrice: Double?, lastPriceUpdate: String?
    ) {
        withContext(ioDispatcher) {
            queries.updateCardUserData(
                id = cardId, ownedCopies = ownedCopies.toLong(), notes = notes,
                currentPrice = currentPrice, lastPriceUpdate = lastPriceUpdate
            )
        }
    }

    override suspend fun deleteCardById(cardId: Long) {
        withContext(ioDispatcher) {
            queries.deleteCardById(cardId)
        }
    }
}