package de.pantastix.project.ui.viewmodel

import de.pantastix.project.coroutines.ioDispatcher
import de.pantastix.project.model.PokemonCard
import de.pantastix.project.repository.CardRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class CardListViewModel(
    private val cardRepository: CardRepository,
    // Ein eigener Scope für das ViewModel ist gute Praxis
    private val viewModelScope: CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher)
) {
    private val _cards = MutableStateFlow<List<PokemonCard>>(emptyList())
    val cards: StateFlow<List<PokemonCard>> = _cards.asStateFlow()

    private val _selectedCard = MutableStateFlow<PokemonCard?>(null)
    val selectedCard: StateFlow<PokemonCard?> = _selectedCard.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadCards()
    }

    fun loadCards() {
        _isLoading.value = true
        cardRepository.getAllCards()
            .onEach { cardList ->
                _cards.value = cardList
                _isLoading.value = false
                _error.value = null
            }
            .catch { e ->
                _error.value = "Fehler beim Laden der Karten: ${e.message}"
                _isLoading.value = false
                // Logge den Fehler auch richtig, z.B. mit einer Logging-Bibliothek
                println("Fehler beim Laden der Karten: $e")
            }
            .launchIn(viewModelScope) // Sammelt den Flow im viewModelScope
    }

    fun selectCard(card: PokemonCard?) {
        _selectedCard.value = card
    }

    fun clearSelectedCard() {
        _selectedCard.value = null
    }

    fun addCard(
        name: String,
        setName: String,
        cardNumber: String, // Beachte, dass dein Modell hier non-nullable ist
        language: String,  // Beachte, dass dein Modell hier non-nullable ist
        cardMarketLink: String, // Beachte, dass dein Modell hier non-nullable ist
        currentPrice: Double?,
        lastPriceUpdate: String?,
        imagePath: String?,
        ownedCopies: Int
    ) {
        viewModelScope.launch {
            try {
                val newCard = PokemonCard(
                    id = null, // DB generiert die ID
                    name = name,
                    setName = setName,
                    cardNumber = cardNumber,
                    language = language,
                    cardMarketLink = cardMarketLink,
                    currentPrice = currentPrice,
                    lastPriceUpdate = lastPriceUpdate,
                    imagePath = imagePath,
                    ownedCopies = ownedCopies
                )
                cardRepository.insertCard(newCard)
                // Die Kartenliste wird durch den Flow automatisch aktualisiert, kein manuelles `loadCards()` hier nötig.
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Fehler beim Hinzufügen der Karte: ${e.message}"
                println("Fehler beim Hinzufügen der Karte: $e")
            }
        }
    }

    fun deleteCard(cardId: Long) {
        viewModelScope.launch {
            try {
                cardRepository.deleteCardById(cardId)
                if (_selectedCard.value?.id == cardId) {
                    clearSelectedCard() // Auswahl aufheben, wenn die ausgewählte Karte gelöscht wird
                }
            } catch (e: Exception) {
                _error.value = "Fehler beim Löschen der Karte: ${e.message}"
                println("Fehler beim Löschen der Karte: $e")
            }
        }
    }

    // Methode, um einen Error-State zurückzusetzen
    fun clearError() {
        _error.value = null
    }
}