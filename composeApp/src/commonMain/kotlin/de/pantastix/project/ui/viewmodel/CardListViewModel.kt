package de.pantastix.project.ui.viewmodel

import de.pantastix.project.coroutines.ioDispatcher
import de.pantastix.project.model.PokemonCard
import de.pantastix.project.model.PokemonCardInfo
import de.pantastix.project.model.SetInfo
import de.pantastix.project.model.api.*
import de.pantastix.project.repository.CardRepository
import de.pantastix.project.repository.SettingsRepository
import de.pantastix.project.service.TcgDexApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString

enum class AppLanguage(val code: String, val displayName: String) {
    GERMAN("de", "Deutsch"),
    ENGLISH("en", "English")
}

enum class CardLanguage(val code: String, val displayName: String) {
    GERMAN("de", "Deutsch"),
    ENGLISH("en", "English"),
    FRENCH("fr", "Français"),
    SPANISH("es", "Español"),
    ITALIAN("it", "Italiano"),
    PORTUGUESE("pt", "Português"),
    JAPANESE("jp", "Japanisch")
}

data class UiState(
    val cardInfos: List<PokemonCardInfo> = emptyList(),
    val sets: List<SetInfo> = emptyList(),
    val selectedCardDetails: PokemonCard? = null,
    val apiCardDetails: TcgDexCardResponse? = null,
    val searchedCardLanguage: CardLanguage? = null, // Speichert die Sprache der letzten Suche
    val isLoading: Boolean = false,
    val error: String? = null,
    val appLanguage: AppLanguage = AppLanguage.GERMAN
)

class CardListViewModel(
    private val cardRepository: CardRepository,
    private val settingsRepository: SettingsRepository,
    private val apiService: TcgDexApiService, // Koin injiziert unseren API Service
    private val viewModelScope: CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher)
) {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        loadCardInfos()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val savedLangCode = settingsRepository.getSetting("language") ?: AppLanguage.GERMAN.code
            val language = AppLanguage.entries.find { it.code == savedLangCode } ?: AppLanguage.GERMAN
            _uiState.update { it.copy(appLanguage = language) }
            loadSets(language)
        }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            settingsRepository.saveSetting("language", language.code)
            _uiState.update { it.copy(appLanguage = language) }
            loadSets(language)
        }
    }

    private fun loadCardInfos() {
        cardRepository.getCardInfos()
            .onEach { cards -> _uiState.update { it.copy(cardInfos = cards) } }
            .launchIn(viewModelScope)
    }

    private fun loadSets(language: AppLanguage) {
        viewModelScope.launch {
            setLoading(true)
            // Lade die neuesten Sets von der API und speichere sie in der DB.
            val setsFromApi = apiService.getAllSets(language.code)
            if (setsFromApi.isNotEmpty()) {
                cardRepository.syncSets(setsFromApi)
            }
            // Abonniere den Flow aus der Datenbank. `onEach` wird immer dann ausgeführt,
            // wenn sich die Daten in der DB ändern.
            cardRepository.getAllSets()
                .onEach { setsFromDb ->
                    // Wir kehren die Liste hier um, damit die neuesten Sets oben stehen.
                    _uiState.update { it.copy(sets = setsFromDb.reversed()) }
                }.launchIn(viewModelScope)

            setLoading(false)
        }
    }

    fun fetchCardDetailsFromApi(setId: String, localId: String, language: CardLanguage) {
        viewModelScope.launch {
            setLoading(true)
            _uiState.update { it.copy(error = null, apiCardDetails = null) }

            // Die Sprache der Suche für den Speicherprozess merken
            _uiState.update { it.copy(searchedCardLanguage = language) }

            val details = apiService.getCardDetails(setId, localId, language.code)

            if (details == null) {
                _uiState.update { it.copy(error = "Karte nicht gefunden.") }
            } else {
                _uiState.update { it.copy(apiCardDetails = details) }
            }
            setLoading(false)
        }
    }

    fun selectCard(cardId: Long) {
        viewModelScope.launch {
            setLoading(true)
            val details = cardRepository.getFullCardDetails(cardId)
            _uiState.update { it.copy(selectedCardDetails = details) }
            setLoading(false)
        }
    }

    fun clearSelectedCard() {
        _uiState.update { it.copy(selectedCardDetails = null) }
    }

    fun confirmAndSaveCard(
        cardDetails: TcgDexCardResponse,
        languageCode: String,
        abbreviation: String?,
        price: Double?,
        cardMarketLink: String
    ) {
        viewModelScope.launch {
            setLoading(true)
            if (!abbreviation.isNullOrBlank()) {
                cardRepository.updateSetAbbreviation(cardDetails.set.id, abbreviation)
            }

            val englishCardDetails = apiService.getCardDetails(cardDetails.set.id, cardDetails.localId, CardLanguage.ENGLISH.code)
            if (englishCardDetails == null) {
                _uiState.update { it.copy(error = "Konnte englische Kartendetails nicht abrufen.") }
                setLoading(false)
                return@launch
            }

            val existingCard = cardRepository.findCardByTcgDexId(cardDetails.id)
            if (existingCard != null) {
                cardRepository.updateCardUserData(
                    cardId = existingCard.id,
                    ownedCopies = existingCard.ownedCopies + 1,
                    notes = null,
                    currentPrice = price ?: existingCard.currentPrice,
                    lastPriceUpdate = if (price != null) Clock.System.now().toString() else null
                )
            } else {
                saveNewCard(cardDetails, englishCardDetails, abbreviation, price, languageCode, cardMarketLink)
            }

            setLoading(false)
            resetApiCardDetails()
        }
    }

    private suspend fun saveNewCard(
        localCardDetails: TcgDexCardResponse,
        englishCardDetails: TcgDexCardResponse,
        abbreviation: String?,
        price: Double?,
        languageCode: String,
        marketLink: String // <<< NEUER PARAMETER
    ) {
        val completeImageUrl = localCardDetails.image?.let { "$it/high.jpg" }

        cardRepository.insertFullPokemonCard(
            setId = localCardDetails.set.id, tcgDexCardId = localCardDetails.id,
            nameLocal = localCardDetails.name, // Verwendet den (ggf. bearbeiteten) Namen
            nameEn = englishCardDetails.name,
            language = languageCode,
            localId = localCardDetails.localId,
            imageUrl = completeImageUrl,
            cardMarketLink = marketLink, // <<< Verwendet den bearbeiteten Link
            ownedCopies = 1,
            notes = null,
            rarity = localCardDetails.rarity,
            hp = localCardDetails.hp,
            types = localCardDetails.types?.joinToString(","),
            illustrator = localCardDetails.illustrator,
            stage = localCardDetails.stage,
            retreatCost = localCardDetails.retreat,
            regulationMark = localCardDetails.regulationMark,
            currentPrice = price,
            lastPriceUpdate = if (price != null) Clock.System.now().toString() else null,
            variantsJson = Json.encodeToString(localCardDetails.variants),
            abilitiesJson = Json.encodeToString(localCardDetails.abilities),
            attacksJson = Json.encodeToString(localCardDetails.attacks),
            legalJson = Json.encodeToString(localCardDetails.legal)
        )
    }

    private fun setLoading(isLoading: Boolean) = _uiState.update { it.copy(isLoading = isLoading) }
    fun resetApiCardDetails() = _uiState.update { it.copy(apiCardDetails = null) }
    fun clearError() = _uiState.update { it.copy(error = null) }
}