package de.pantastix.project.repository

import de.pantastix.project.model.PokemonCard
import de.pantastix.project.model.PokemonCardInfo
import de.pantastix.project.model.SetInfo
import kotlinx.coroutines.flow.Flow

/**
 * Der "Vertrag" für den Datenzugriff.
 * Dies ist die finale, korrigierte Version ohne doppelte Methoden.
 */
interface CardRepository {

    // --- Set-Operationen ---
    fun getAllSets(): Flow<List<SetInfo>>
    suspend fun syncSets(sets: List<SetInfo>)
    suspend fun getSetsByOfficialCount(count: Int): List<SetInfo>


    // --- Pokémon-Karten-Operationen ---
    fun getCardInfos(): Flow<List<PokemonCardInfo>>
    suspend fun getFullCardDetails(cardId: Long): PokemonCard?
    suspend fun findCardByTcgDexId(tcgDexId: String, language: String): PokemonCardInfo?

    suspend fun updateSetAbbreviation(setId: String, abbreviation: String)

    /**
     * Fügt eine neue, vollständig definierte Karte hinzu.
     * Dies ist die einzige, korrekte Version der Methode.
     */
    suspend fun insertFullPokemonCard(card: PokemonCard)

    /**
     * Aktualisiert die vom Nutzer änderbaren Daten einer Karte.
     * Dies ist die einzige, korrekte Version der Methode.
     */
    suspend fun updateCardUserData(
        cardId: Long,
        ownedCopies: Int,
        notes: String?,
        currentPrice: Double?,
        lastPriceUpdate: String?
    )

    suspend fun findExistingCard(setId: String, localId: String, language: String): PokemonCardInfo?

    /** Löscht eine Karte anhand ihrer Sammlungs-ID. */
    suspend fun deleteCardById(cardId: Long)

    suspend fun clearAllData()
}
