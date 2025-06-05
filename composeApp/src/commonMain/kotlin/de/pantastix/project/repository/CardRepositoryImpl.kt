import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import de.pantastix.project.model.PokemonCard
import de.pantastix.project.repository.CardRepository
import de.pantastix.project.coroutines.ioDispatcher
import de.pantastix.project.db.CardDatabaseQueries
import de.pantastix.project.db.PokemonCardEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext // withContext ist immer noch nützlich

class CardRepositoryImpl(
    private val queries: CardDatabaseQueries
) : CardRepository {

    private fun mapPokemonCardEntityToDomain(entity: PokemonCardEntity): PokemonCard {
        // ... (deine Mapper-Funktion bleibt gleich)
        return PokemonCard(
            id = entity.id,
            name = entity.name,
            setName = entity.setName,
            cardNumber = entity.cardNumber ?: "",
            language = entity.language ?: "",
            cardMarketLink = entity.cardMarketLink ?: "",
            currentPrice = entity.currentPrice,
            lastPriceUpdate = entity.lastPriceUpdate,
            imagePath = entity.imagePath,
            ownedCopies = entity.ownedCopies.toInt()
        )
    }

    override fun getAllCards(): Flow<List<PokemonCard>> {
        return queries.selectAllCards()
            .asFlow()
            .mapToList(ioDispatcher) // Verwende den KMP-sicheren Dispatcher
            .map { entities ->
                entities.map { entity -> mapPokemonCardEntityToDomain(entity) }
            }
    }

    override suspend fun getCardById(id: Long): PokemonCard? {
        // withContext kann hier bleiben, um sicherzustellen, dass die Operation nicht den aufrufenden Kontext blockiert,
        // und der Block darin wird dann im ioDispatcher ausgeführt.
        return withContext(ioDispatcher) {
            queries.selectCardById(id)
                .executeAsOneOrNull() // Führt die Query aus
                ?.let { entity -> mapPokemonCardEntityToDomain(entity) }
        }
    }

    // ... (Passe andere Methoden an, die explizit Dispatchers.IO verwendet haben) ...

    override suspend fun getCardsByCardMarketLink(cardMarketLink: String): List<PokemonCard> {
        return withContext(ioDispatcher) {
            queries.selectCardsByCardMarketLink(cardMarketLink)
                .executeAsList()
                .map { entity -> mapPokemonCardEntityToDomain(entity) }
        }
    }

    override suspend fun searchCardsByName(searchText: String): List<PokemonCard> {
        return withContext(ioDispatcher) {
            queries.searchCardsByName(searchText)
                .executeAsList()
                .map { entity -> mapPokemonCardEntityToDomain(entity) }
        }
    }

    override suspend fun filterCardsBySetName(setName: String): List<PokemonCard> {
        return withContext(ioDispatcher) {
            queries.filterCardsBySetName(setName)
                .executeAsList()
                .map { entity -> mapPokemonCardEntityToDomain(entity) }
        }
    }

    // Für Insert, Update, Delete ist es auch gut, withContext zu verwenden,
    // um sicherzustellen, dass sie nicht im Main-Thread des Aufrufers laufen.
    override suspend fun insertCard(card: PokemonCard): Long {
        return withContext(ioDispatcher) {
            queries.insertCard(
                name = card.name,
                setName = card.setName,
                cardNumber = card.cardNumber,
                language = card.language,
                cardMarketLink = card.cardMarketLink,
                currentPrice = card.currentPrice,
                lastPriceUpdate = card.lastPriceUpdate,
                imagePath = card.imagePath,
                ownedCopies = card.ownedCopies.toLong()
            )
            queries.lastInsertedRowId().executeAsOne()
        }
    }

    override suspend fun updateCardDetails(card: PokemonCard) {
        withContext(ioDispatcher) {
            card.id?.let { cardId ->
                queries.updateCardDetails(
                    id = cardId,
                    name = card.name,
                    setName = card.setName,
                    cardNumber = card.cardNumber,
                    language = card.language,
                    cardMarketLink = card.cardMarketLink,
                    currentPrice = card.currentPrice,
                    lastPriceUpdate = card.lastPriceUpdate,
                    imagePath = card.imagePath,
                    ownedCopies = card.ownedCopies.toLong()
                )
            }
        }
    }

    override suspend fun updateCardPrice(cardId: Long, price: Double?, lastUpdate: String?) {
        withContext(ioDispatcher) {
            queries.updateCardPrice(
                id = cardId,
                currentPrice = price,
                lastPriceUpdate = lastUpdate
            )
        }
    }

    override suspend fun updateOwnedCopies(cardId: Long, newCount: Int) {
        withContext(ioDispatcher) {
            queries.updateOwnedCopiesForCard(
                id = cardId,
                ownedCopies = newCount.toLong()
            )
        }
    }

    override suspend fun deleteCardById(id: Long) {
        withContext(ioDispatcher) {
            queries.deleteCardById(id)
        }
    }

    override suspend fun clearAllCards() {
        withContext(ioDispatcher) {
            queries.clearAllCards()
        }
    }

    override suspend fun countAllCards(): Long {
        return withContext(ioDispatcher) {
            queries.countAllCards().executeAsOne()
        }
    }
}