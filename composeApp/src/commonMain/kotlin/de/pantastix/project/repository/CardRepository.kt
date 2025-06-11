package de.pantastix.project.repository

import de.pantastix.project.model.PokemonCard
import de.pantastix.project.model.PokemonCardInfo
import de.pantastix.project.model.SetInfo
import kotlinx.coroutines.flow.Flow

interface CardRepository {

    // --- Set-Operationen ---

    /** Ruft alle lokal gespeicherten Sets ab, sortiert nach Datum. */
    fun getAllSets(): Flow<List<SetInfo>>

    /** Speichert oder aktualisiert eine Liste von Sets in der lokalen DB. */
    suspend fun syncSets(sets: List<SetInfo>)


    // --- Pokémon-Karten-Operationen ---

    /** Ruft die optimierte Kartenliste für die Hauptansicht ab. */
    fun getCardInfos(): Flow<List<PokemonCardInfo>>

    /** Ruft alle Details für eine einzelne Karte ab. */
    suspend fun getFullCardDetails(cardId: Long): PokemonCard?

    /** Prüft, ob eine Karte bereits in der Sammlung ist, basierend auf ihrer TCGdex-ID. */
    suspend fun findCardByTcgDexId(tcgDexId: String): PokemonCardInfo?

    /** Fügt eine neue, vollständig definierte Karte hinzu. */
    suspend fun insertFullPokemonCard(
        setId: String, tcgDexCardId: String, nameDe: String, nameEn: String,
        localId: String, imageUrl: String?, cardMarketLink: String?,
        ownedCopies: Int, notes: String?, rarity: String?, hp: Int?,
        types: String?, illustrator: String?, stage: String?, retreatCost: Int?,
        regulationMark: String?, variantsJson: String?, abilitiesJson: String?,
        attacksJson: String?, legalJson: String?
    )

    /** Aktualisiert die vom Nutzer änderbaren Daten einer Karte (Anzahl & Notizen). */
    suspend fun updateCardUserData(cardId: Long, ownedCopies: Int, notes: String?)


    suspend fun insertFullPokemonCard(
        setId: String, tcgDexCardId: String, nameDe: String, nameEn: String,
        localId: String, imageUrl: String?, cardMarketLink: String?,
        ownedCopies: Int, notes: String?, rarity: String?, hp: Int?,
        types: String?, illustrator: String?, stage: String?, retreatCost: Int?,
        regulationMark: String?,
        currentPrice: Double?, lastPriceUpdate: String?, // <<< Parameter hier hinzugefügt
        variantsJson: String?, abilitiesJson: String?,
        attacksJson: String?, legalJson: String?
    )


    suspend fun updateCardUserData(
        cardId: Long,
        ownedCopies: Int,
        notes: String?,
        currentPrice: Double?, // <<< Parameter hier hinzugefügt
        lastPriceUpdate: String? // <<< Parameter hier hinzugefügt
    )

    /** Löscht eine Karte anhand ihrer Sammlungs-ID. */
    suspend fun deleteCardById(cardId: Long)
}