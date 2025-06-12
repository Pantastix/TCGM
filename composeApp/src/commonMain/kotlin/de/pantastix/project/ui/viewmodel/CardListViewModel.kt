package de.pantastix.project.ui.viewmodel

import de.pantastix.project.coroutines.ioDispatcher
import de.pantastix.project.model.PokemonCard
import de.pantastix.project.model.PokemonCardInfo
import de.pantastix.project.model.SetInfo
import de.pantastix.project.model.api.*
import de.pantastix.project.repository.CardRepository
import de.pantastix.project.service.TcgDexApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class CardListViewModel(
    private val cardRepository: CardRepository,
    private val apiService: TcgDexApiService, // Koin injiziert unseren API Service
    private val viewModelScope: CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher)
) {
    // --- States für die UI ---
    private val _cardInfos = MutableStateFlow<List<PokemonCardInfo>>(emptyList())
    val cardInfos: StateFlow<List<PokemonCardInfo>> = _cardInfos.asStateFlow()

    private val _sets = MutableStateFlow<List<SetInfo>>(emptyList())
    val sets: StateFlow<List<SetInfo>> = _sets.asStateFlow()

    // <<< HIER HINZUGEFÜGT: State für die Detailansicht
    private val _selectedCardDetails = MutableStateFlow<PokemonCard?>(null)
    val selectedCardDetails: StateFlow<PokemonCard?> = _selectedCardDetails.asStateFlow()

    private val _apiCardDetails = MutableStateFlow<TcgDexCardResponse?>(null)
    val apiCardDetails: StateFlow<TcgDexCardResponse?> = _apiCardDetails.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        // Beim Starten des ViewModels laden wir die Kartenliste und die Setliste
        loadCardInfos()
        loadSets()
    }

    private fun loadCardInfos() {
        cardRepository.getCardInfos()
            .onEach { cardList -> _cardInfos.value = cardList }
            .launchIn(viewModelScope)
    }

    fun loadSets() {
        viewModelScope.launch {
            _isLoading.value = true
            val setsFromDb = cardRepository.getAllSets().first()
            if (setsFromDb.isNotEmpty()) {
                _sets.value = setsFromDb
            } else {
                // Wenn die DB leer ist, von der API holen und in der DB speichern
                val setsFromApi = apiService.getAllSets()
                if (setsFromApi.isNotEmpty()) {
                    cardRepository.syncSets(setsFromApi) // Sets in DB speichern
                    _sets.value = setsFromApi
                }
            }
            _isLoading.value = false
        }
    }

    fun fetchCardDetailsFromApi(setId: String, localId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _apiCardDetails.value = null

            val details = apiService.getGermanCardDetails(setId, localId)
            if (details == null) {
                _error.value = "Karte nicht gefunden. Überprüfe Set und Kartennummer."
            } else {
                _apiCardDetails.value = details
            }
            _isLoading.value = false
        }
    }

    // <<< HIER HINZUGEFÜGT: Funktion zum Auswählen einer Karte
    /**
     * Holt die vollständigen Details einer Karte aus der DB und setzt sie als ausgewählte Karte.
     */
    fun selectCard(cardId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            _selectedCardDetails.value = cardRepository.getFullCardDetails(cardId)
            _isLoading.value = false
        }
    }

    fun confirmAndSaveCard(germanCardDetails: TcgDexCardResponse) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val existingCard = cardRepository.findCardByTcgDexId(germanCardDetails.id)
            if (existingCard != null) {
                cardRepository.updateCardUserData(
                    cardId = existingCard.id,
                    ownedCopies = existingCard.ownedCopies + 1,
                    notes = null,
                    currentPrice = existingCard.currentPrice,
                    lastPriceUpdate = null
                )
            } else {
                val englishCardDetails = apiService.getEnglishCardDetails(germanCardDetails.set.id, germanCardDetails.localId)
                if (englishCardDetails == null) {
                    _error.value = "Konnte englische Kartendetails nicht abrufen. Speichern fehlgeschlagen."
                    _isLoading.value = false
                    return@launch
                }
                saveNewCard(germanCardDetails, englishCardDetails)
            }
            _isLoading.value = false
            resetApiCardDetails()
        }
    }

    private suspend fun saveNewCard(germanCardDetails: TcgDexCardResponse, englishCardDetails: TcgDexCardResponse) {
        // Hilfsfunktion zum Erstellen des "Slugs" für die URL
        fun slugify(input: String) = input.replace("'", "").replace(" ", "-").replace(":", "")

        // KORRIGIERT: Der CardMarket-Link enthält jetzt das Set-Kürzel.
        // Wir nehmen die Set-ID (z.B. "sv10") und machen sie groß ("SV10"), da ein offizielles Kürzel
        // wie "DRI" nicht von der API bereitgestellt wird.
        val setAbbreviation = germanCardDetails.set.id.uppercase()
        val cardMarketLink = "https://www.cardmarket.com/de/Pokemon/Products/Singles/" +
                "${slugify(englishCardDetails.set.name)}/" +
                "${slugify(englishCardDetails.name)}-${setAbbreviation}${germanCardDetails.localId}"

        // KORRIGIERT: Die Bild-URL wird jetzt mit Qualität und Endung vervollständigt.
        val completeImageUrl = germanCardDetails.image?.let { "$it/high.jpg" }

        cardRepository.insertFullPokemonCard(
            setId = germanCardDetails.set.id, tcgDexCardId = germanCardDetails.id, nameDe = germanCardDetails.name,
            nameEn = englishCardDetails.name, localId = germanCardDetails.localId,
            imageUrl = completeImageUrl, // <<< Verwendet die neue, vollständige URL
            cardMarketLink = cardMarketLink, // <<< Verwendet den neuen, korrekten Link
            ownedCopies = 1, notes = null, rarity = germanCardDetails.rarity,
            hp = germanCardDetails.hp, types = germanCardDetails.types?.joinToString(","), illustrator = null,
            stage = germanCardDetails.stage, retreatCost = germanCardDetails.retreat, regulationMark = germanCardDetails.regulationMark,
            currentPrice = null, lastPriceUpdate = null,
            variantsJson = germanCardDetails.variants?.let { Json.encodeToString(TcgDexVariants.serializer(), it) },
            abilitiesJson = germanCardDetails.abilities?.let { Json.encodeToString(ListSerializer(TcgDexAbility.serializer()), it) },
            attacksJson = germanCardDetails.attacks?.let { Json.encodeToString(ListSerializer(TcgDexAttack.serializer()), it) },
            legalJson = germanCardDetails.legal?.let { Json.encodeToString(TcgDexLegal.serializer(), it) }
        )
    }

    fun resetApiCardDetails() { _apiCardDetails.value = null }
    fun clearSelectedCard() { _selectedCardDetails.value = null }
    fun clearError() { _error.value = null }
}