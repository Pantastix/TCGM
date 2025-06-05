package de.pantastix.project.repository

import de.pantastix.project.model.PokemonCard
import kotlinx.coroutines.flow.Flow

interface CardRepository {
    fun getAllCards(): Flow<List<PokemonCard>> // Flow für reaktive Updates
    suspend fun getCardById(id: Long): PokemonCard?
    suspend fun getCardsByCardMarketLink(cardMarketLink: String): List<PokemonCard>
    suspend fun searchCardsByName(searchText: String): List<PokemonCard>
    suspend fun filterCardsBySetName(setName: String): List<PokemonCard>

    suspend fun insertCard(card: PokemonCard): Long // Gibt die ID der neuen Karte zurück
    suspend fun updateCardDetails(card: PokemonCard) // Aktualisiert eine bestehende Karte
    suspend fun updateCardPrice(cardId: Long, price: Double?, lastUpdate: String?)
    suspend fun updateOwnedCopies(cardId: Long, newCount: Int)

    suspend fun deleteCardById(id: Long)
    suspend fun clearAllCards()

    suspend fun countAllCards(): Long
}