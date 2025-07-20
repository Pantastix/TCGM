package de.pantastix.project.ui.viewmodel

import de.pantastix.project.coroutines.ioDispatcher
import de.pantastix.project.model.Ability
import de.pantastix.project.model.Attack
import de.pantastix.project.model.PokemonCard
import de.pantastix.project.model.PokemonCardInfo
import de.pantastix.project.model.SetInfo
import de.pantastix.project.model.api.*
import de.pantastix.project.repository.CardRepository
import de.pantastix.project.repository.SettingsRepository
import de.pantastix.project.repository.SupabaseCardRepository
import de.pantastix.project.service.TcgApiService
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

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
    val loadingMessage: String? = null,
    val error: String? = null,
    val appLanguage: AppLanguage = AppLanguage.GERMAN,
    val supabaseUrl: String = "",
    val supabaseKey: String = "",
    val isSupabaseConnected: Boolean = false,
    val syncPromptMessage: String? = null
)

@OptIn(kotlin.time.ExperimentalTime::class)
class CardListViewModel(
    private val localCardRepository: CardRepository, // Immer das SQLite-Repository
    private val settingsRepository: SettingsRepository,
    private val apiService: TcgApiService,
    private val viewModelScope: CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher)
) {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var remoteCardRepository: CardRepository? = null // Für die Supabase-Implementierung

    private var cardInfosCollectionJob: Job? = null
    private var setsCollectionJob: Job? = null

    // Wählt dynamisch das richtige Repository (Cloud oder Lokal)
    private val activeCardRepository: CardRepository
        get() {
            val repo = remoteCardRepository ?: localCardRepository
            println("DEBUG: activeCardRepository getter called. Current active: ${repo::class.simpleName}")
            return repo
        }

    init {
        loadSettings()
        loadCardInfos()
        loadSupabaseSettings()
    }

    // --- Einstellungs- und Sprachlogik ---

    private fun loadSettings() {
        viewModelScope.launch {
            val savedLangCode = settingsRepository.getSetting("language") ?: AppLanguage.GERMAN.code
            val language = AppLanguage.entries.find { it.code == savedLangCode } ?: AppLanguage.GERMAN
            _uiState.update { it.copy(appLanguage = language) }
            loadSets(language)
        }
    }

    fun setAppLanguage(language: AppLanguage) {
        viewModelScope.launch {
            settingsRepository.saveSetting("language", language.code)
            _uiState.update { it.copy(appLanguage = language) }
            loadSets(language)
        }
    }

    // --- Datenlade-Logik ---

    private fun loadCardInfos() {
        // Bricht den vorherigen Job ab, um sicherzustellen, dass nur ein Repository beobachtet wird
        cardInfosCollectionJob?.cancel()

        // Startet einen neuen Job, der das aktuell aktive Repository beobachtet
        cardInfosCollectionJob = activeCardRepository.getCardInfos()
            .onEach { cards ->
                _uiState.update { it.copy(cardInfos = cards) }
                println("Card Infos geladen: ${cards.size} Karten (Quelle: ${if (remoteCardRepository != null) "Supabase" else "Lokal"})")
                println("DEBUG: Cards in UI State: ${_uiState.value.cardInfos.size}")
            }
            .launchIn(viewModelScope)
    }

    private fun loadSets(language: AppLanguage? = null) {
        // Bricht den vorherigen Job ab, um sicherzustellen, dass nur ein Repository beobachtet wird
        setsCollectionJob?.cancel()
        println("DEBUG: loadSets() called. Cancelling previous setsCollectionJob.")

        setsCollectionJob = viewModelScope.launch {
            setLoading(true)
            val loading_message = "Lade Sets..."
            _uiState.update { it.copy(loadingMessage = loading_message) }

            val currentLanguage = language ?: uiState.value.appLanguage // Verwende die aktuelle App-Sprache, wenn nicht angegeben
            val setsFromApi = apiService.getAllSets(currentLanguage.code)
            println("DEBUG: Sets von API geladen: ${setsFromApi.size} Sets (Sprache: ${currentLanguage.displayName})")
            if (setsFromApi.isNotEmpty()) {
                // Synchronisiert immer die lokale DB, die als Cache dient
                activeCardRepository.syncSets(setsFromApi)
            }
            activeCardRepository.getAllSets().onEach { setsFromDb ->
                _uiState.update { it.copy(sets = setsFromDb.sortedByDescending { it.releaseDate }) }
                println("DEBUG: Sets geladen: ${setsFromDb.size} Sets (Quelle: ${if (remoteCardRepository != null) "Supabase" else "Lokal"})")
            }.launchIn(viewModelScope)

            setLoading(false)
            _uiState.update { it.copy(loadingMessage = null) }
        }
    }

    fun deleteSelectedCard() {
        viewModelScope.launch {
            uiState.value.selectedCardDetails?.id?.let { cardId ->
                setLoading(true)
                activeCardRepository.deleteCardById(cardId)
                // Nach dem Löschen die Auswahl aufheben, damit die Detailansicht verschwindet
                loadCardInfos()
                clearSelectedCard()
                setLoading(false)
            }
        }
    }

    // --- Karte Hinzufügen Workflow ---

    fun fetchCardDetailsFromApi(setId: String, localId: String, language: CardLanguage) {
        viewModelScope.launch {
            setLoading(true)
            _uiState.update { it.copy(error = null, apiCardDetails = null) }
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
            val details = activeCardRepository.getFullCardDetails(cardId)
            _uiState.update { it.copy(selectedCardDetails = details) }
            setLoading(false)
        }
    }

    fun confirmAndSaveCard(
        cardDetails: TcgDexCardResponse,
        languageCode: String,
        abbreviation: String?,
        price: Double?,
        cardMarketLink: String,
        ownedCopies: Int,
        notes: String?
    ) {
        viewModelScope.launch {
            setLoading(true)
            val existingCard = activeCardRepository.findCardByTcgDexId(cardDetails.id, languageCode)
            if (existingCard != null) {
                activeCardRepository.updateCardUserData(
                    cardId = existingCard.id,
                    ownedCopies = existingCard.ownedCopies + ownedCopies,
                    notes = null,
                    currentPrice = price ?: existingCard.currentPrice,
                    lastPriceUpdate = if (price != null) Clock.System.now().toString() else null
                )
            } else {
                val englishCardDetails = apiService.getCardDetails(cardDetails.set.id, cardDetails.localId, CardLanguage.ENGLISH.code)
                if (englishCardDetails == null) {
                    _uiState.update { it.copy(error = "Konnte englische Kartendetails nicht abrufen.") }
                    setLoading(false)
                    return@launch
                }
                saveNewCard(cardDetails, englishCardDetails, abbreviation, price, languageCode, cardMarketLink, ownedCopies, notes)
            }
            setLoading(false)
            resetApiCardDetails()
        }
    }

    fun updateCard(
        cardId: Long,
        ownedCopies: Int,
        notes: String?,
        currentPrice: Double?
    ) {
        viewModelScope.launch {
            setLoading(true)
            activeCardRepository.updateCardUserData(
                cardId = cardId,
                ownedCopies = ownedCopies,
                notes = notes,
                currentPrice = currentPrice,
                lastPriceUpdate = if (currentPrice != null) Clock.System.now().toString() else null
            )
            // Lade die Kartendetails neu, um die Änderungen in der UI anzuzeigen
            loadCardInfos()
            selectCard(cardId)
            setLoading(false)
        }
    }

    private suspend fun saveNewCard(
        localCardDetails: TcgDexCardResponse,
        englishCardDetails: TcgDexCardResponse,
        abbreviation: String?,
        price: Double?,
        languageCode: String,
        marketLink: String,
        ownedCopies: Int,
        notes: String?
    ) {
        val completeImageUrl = localCardDetails.image?.let { "$it/high.jpg" }

        val newCard = PokemonCard(
            id = null, // ID ist null, da sie von der DB generiert wird
            tcgDexCardId = localCardDetails.id,
            nameLocal = localCardDetails.name,
            nameEn = englishCardDetails.name,
            language = languageCode,
            imageUrl = completeImageUrl,
            cardMarketLink = marketLink,
            ownedCopies = ownedCopies,
            notes = notes,
            setName = localCardDetails.set.name,
            localId = "${localCardDetails.localId} / ${localCardDetails.set.cardCount?.total ?: '?'}",
            currentPrice = price,
            lastPriceUpdate = if (price != null) Clock.System.now().toString() else null,
            rarity = localCardDetails.rarity,
            hp = localCardDetails.hp,
            types = localCardDetails.types ?: emptyList(),
            illustrator = localCardDetails.illustrator,
            stage = localCardDetails.stage,
            retreatCost = localCardDetails.retreat,
            regulationMark = localCardDetails.regulationMark,
            abilities = localCardDetails.abilities?.map { Ability(it.name, it.type, it.effect) } ?: emptyList(),
            attacks = localCardDetails.attacks?.map { Attack(it.cost, it.name, it.effect, it.damage) } ?: emptyList(),
            setId = localCardDetails.set.id,
            variantsJson = localCardDetails.variants?.let { Json.encodeToString(it) },
            legalJson = localCardDetails.legal?.let { Json.encodeToString(it)}
        )

        // Übergebe das einzelne Objekt an das Repository
        activeCardRepository.insertFullPokemonCard(newCard)
        loadCardInfos()
    }

    // --- Supabase Logik ---

    private fun loadSupabaseSettings() {
        viewModelScope.launch {
            val url = settingsRepository.getSetting("supabase_url") ?: ""
            val key = settingsRepository.getSetting("supabase_key") ?: ""
            _uiState.update { it.copy(supabaseUrl = url, supabaseKey = key) }
            if (url.isNotBlank() && key.isNotBlank()) {
                connectToSupabase(url, key)
            }
        }
    }

    fun connectToSupabase(url: String, key: String) {
        viewModelScope.launch {
            setLoading(true)
            try {
                val supabase = createSupabaseClient(url, key) { install(Postgrest) }

                try {
                    supabase.postgrest.from("PokemonCardEntity").select { limit(1) }

                    remoteCardRepository = SupabaseCardRepository(supabase.postgrest)

                    _uiState.update { it.copy(isSupabaseConnected = true) }

                    loadCardInfos()
                    loadSets()
                } catch (e: Exception) {
                    val errorMessage = e.message ?: "Unbekannter Fehler."
                    println("Fehler bei der Supabase-Verbindungstest: $errorMessage")

                    when {
                        errorMessage.contains("Invalid API key", ignoreCase = true) -> {
                            println("Ungültiger API-Schlüssel bei der Supabase-Verbindung: $errorMessage")
                            _uiState.update { it.copy(error = "Verbindung zu Supabase fehlgeschlagen: Ungültiger API-Schlüssel. Bitte überprüfen Sie Ihren Supabase 'anon' oder 'service_role' API-Schlüssel.") }
                        }
                        errorMessage.contains("UnresolvedAddressException", ignoreCase = true) ||
                                errorMessage.contains("Failed to connect", ignoreCase = true) ||
                                (errorMessage.contains("HTTP request to ", ignoreCase = true) && errorMessage.contains("(GET) failed with message", ignoreCase = true)) -> {
                            println("Fehler bei der Supabase-Verbindung: $errorMessage")
                            _uiState.update { it.copy(error = "Verbindung zu Supabase fehlgeschlagen: Ungültige URL oder Netzwerkproblem. Bitte überprüfen Sie die URL und Ihre Internetverbindung.") }
                        }
                        // This specific error indicates that the connection and authentication were successful,
                        // but the requested table does not exist.
                        errorMessage.contains("relation \"public.pokemoncardentity\" does not exist", ignoreCase = true) || errorMessage.contains("relation \"pokemoncardentity\" does not exist", ignoreCase = true) -> {
                            println("Tabelle 'PokemonCardEntity' fehlt in Supabase: $errorMessage")
                            _uiState.update { it.copy(error = "Verbindung erfolgreich, aber die Tabelle 'PokemonCardEntity' fehlt in Supabase. Bitte erstellen Sie die Tabellen manuell mit den bereitgestellten SQL-Skripten.") }
                        }
                        else -> {
                            println("Allgemeiner Fehler bei der Supabase-Verbindung: $errorMessage")
                            _uiState.update { it.copy(error = "Verbindung zu Supabase fehlgeschlagen: $errorMessage") }
                        }
                    }
                }
            } catch (e: Exception) {
                println("Fehler beim Erstellen des Supabase-Clients: ${e.message}")
                _uiState.update { it.copy(error = "Verbindung zu Supabase fehlgeschlagen: ${e.message}") }
            }
            setLoading(false)
        }
    }

    fun connectNewToSupabase(url: String, key: String) {
        viewModelScope.launch {
            setLoading(true)
            try {
                val supabase = createSupabaseClient(url, key) { install(Postgrest) }
                println("Supabase-Client erfolgreich erstellt: $url")

                try {
                    supabase.postgrest.from("PokemonCardEntity").select { limit(1) }

                    settingsRepository.saveSetting("supabase_url", url)
                    settingsRepository.saveSetting("supabase_key", key)

                    println("DEBUG: Before remoteCardRepository assignment in connectToSupabase. remoteCardRepository is: $remoteCardRepository")
                    remoteCardRepository = SupabaseCardRepository(supabase.postgrest) // Activate the Cloud DB here
                    println("DEBUG: After remoteCardRepository assignment in connectToSupabase. remoteCardRepository is: $remoteCardRepository")

                    _uiState.update { it.copy(isSupabaseConnected = true) }
                    print("DEBUG: Syncing local sets to Supabase")
                    syncSetsToSupabase()
                    loadCardInfos()
                    loadSets()
                    checkForSync()
                } catch (e: Exception) {
                    val errorMessage = e.message ?: "Unbekannter Fehler."
                    println("Fehler bei der Supabase-Verbindungstest: $errorMessage")

                    when {
                        errorMessage.contains("Invalid API key", ignoreCase = true) -> {
                            println("Ungültiger API-Schlüssel bei der Supabase-Verbindung: $errorMessage")
                            _uiState.update { it.copy(error = "Verbindung zu Supabase fehlgeschlagen: Ungültiger API-Schlüssel. Bitte überprüfen Sie Ihren Supabase 'anon' oder 'service_role' API-Schlüssel.") }
                        }
                        errorMessage.contains("UnresolvedAddressException", ignoreCase = true) ||
                                errorMessage.contains("Failed to connect", ignoreCase = true) ||
                                (errorMessage.contains("HTTP request to ", ignoreCase = true) && errorMessage.contains("(GET) failed with message", ignoreCase = true)) -> {
                            println("Fehler bei der Supabase-Verbindung: $errorMessage")
                            _uiState.update { it.copy(error = "Verbindung zu Supabase fehlgeschlagen: Ungültige URL oder Netzwerkproblem. Bitte überprüfen Sie die URL und Ihre Internetverbindung.") }
                        }
                        // This specific error indicates that the connection and authentication were successful,
                        // but the requested table does not exist.
                        errorMessage.contains("relation \"public.pokemoncardentity\" does not exist", ignoreCase = true) || errorMessage.contains("relation \"pokemoncardentity\" does not exist", ignoreCase = true) -> {
                            println("Tabelle 'PokemonCardEntity' fehlt in Supabase: $errorMessage")
                            _uiState.update { it.copy(error = "Verbindung erfolgreich, aber die Tabelle 'PokemonCardEntity' fehlt in Supabase. Bitte erstellen Sie die Tabellen manuell mit den bereitgestellten SQL-Skripten.") }
                        }
                        else -> {
                            println("Allgemeiner Fehler bei der Supabase-Verbindung: $errorMessage")
                            _uiState.update { it.copy(error = "Verbindung zu Supabase fehlgeschlagen: $errorMessage") }
                        }
                    }
                }
            } catch (e: Exception) {
                println("Fehler beim Erstellen des Supabase-Clients: ${e.message}")
                _uiState.update { it.copy(error = "Verbindung zu Supabase fehlgeschlagen: ${e.message}") }
            }
            setLoading(false)
        }
    }

    fun disconnectFromSupabase() {
        viewModelScope.launch {
            settingsRepository.saveSetting("supabase_url", "")
            settingsRepository.saveSetting("supabase_key", "")
            println("DEBUG: Before remoteCardRepository nullification in disconnectFromSupabase. remoteCardRepository is: $remoteCardRepository")
            remoteCardRepository = null
            println("DEBUG: After remoteCardRepository nullification in disconnectFromSupabase. remoteCardRepository is: $remoteCardRepository")
            _uiState.update { it.copy(isSupabaseConnected = false, supabaseUrl = "", supabaseKey = "") }
            loadCardInfos() // Lade die lokalen Karten neu
        }
    }

    private suspend fun checkForSync() {
        val localCards = localCardRepository.getCardInfos().first()
        if (localCards.isNotEmpty() && remoteCardRepository != null) {
            val remoteCards = remoteCardRepository!!.getCardInfos().first()
            if (remoteCards.isEmpty()) {
                _uiState.update { it.copy(syncPromptMessage = "${localCards.size} lokale Karten gefunden. Sollen diese in die Cloud hochgeladen werden? Die lokale Datenbank bleibt unverändert.") }
            }
        }
    }

    suspend fun syncSetsToSupabase(){
        withContext(Dispatchers.IO) {
            val localSets = localCardRepository.getAllSets().first()
            val remoteSets = remoteCardRepository?.getAllSets()?.first() ?: emptyList()
            val remoteSetIds = remoteSets.map { it.setId }.toSet()

            localSets.forEach { localSet ->
                if (localSet.setId !in remoteSetIds) {
                    // Set does not exist remotely, insert it
                    remoteCardRepository?.syncSets(listOf(localSet)) // syncSets handles upsert
                } else {
                    // Set exists remotely, check for abbreviation
                    val remoteSet = remoteSets.find { it.setId == localSet.setId }
                    if (remoteSet != null && remoteSet.abbreviation.isNullOrBlank() && !localSet.abbreviation.isNullOrBlank()) {
                        // Remote set has no abbreviation, but local does. Update remote.
                        remoteCardRepository?.updateSetAbbreviation(localSet.setId, localSet.abbreviation)
                    }
                }
            }
            loadSets()
        }
    }

    fun syncLocalToSupabase() {
        viewModelScope.launch {
            setLoading(true)
            dismissSyncPrompt()

            val localCards = localCardRepository.getCardInfos().first()
            val remoteCards = remoteCardRepository?.getCardInfos()?.first() ?: emptyList()
            val remoteCardKeys = remoteCards.map { "${it.tcgDexCardId}-${it.language}" }.toSet()

            localCards.forEach { localCardInfo ->
                val fullLocalCard = localCardRepository.getFullCardDetails(localCardInfo.id)
                if (fullLocalCard != null) {
                    val localCardKey = "${fullLocalCard.tcgDexCardId}-${fullLocalCard.language}"
                    if (localCardKey !in remoteCardKeys) {
                        remoteCardRepository?.insertFullPokemonCard(fullLocalCard)
                    }
                }
            }

            loadCardInfos() // Lade die Remote-Daten neu
            setLoading(false)
        }
    }

    fun dismissSyncPrompt() = _uiState.update { it.copy(syncPromptMessage = null) }
    private fun setLoading(isLoading: Boolean) = _uiState.update { it.copy(isLoading = isLoading) }
    fun resetApiCardDetails() = _uiState.update { it.copy(apiCardDetails = null, searchedCardLanguage = null) }
    fun clearSelectedCard() = _uiState.update { it.copy(selectedCardDetails = null) }
    fun clearError() = _uiState.update { it.copy(error = null) }
}