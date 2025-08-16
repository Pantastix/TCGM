package de.pantastix.project.ui.viewmodel

import de.pantastix.project.coroutines.ioDispatcher
import de.pantastix.project.model.Ability
import de.pantastix.project.model.Attack
import de.pantastix.project.model.PokemonCard
import de.pantastix.project.model.PokemonCardInfo
import de.pantastix.project.model.SetInfo
import de.pantastix.project.model.api.*
import de.pantastix.project.platform.getSystemLanguage
import de.pantastix.project.platform.setAppLanguage
import de.pantastix.project.repository.CardRepository
import de.pantastix.project.repository.SettingsRepository
import de.pantastix.project.repository.SupabaseCardRepository
import de.pantastix.project.service.TcgApiService
import de.pantastix.project.service.UpdateChecker
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import java.io.File
import kotlin.system.exitProcess
import kotlin.time.ExperimentalTime

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
    val isInitialized: Boolean = false,
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
    val syncPromptMessage: String? = null,
    val disconnectPromptMessage: String? = null,
    val updateInfo: UpdateInfo? = null,
    val setsUpdateWarning: String? = null
)

@OptIn(ExperimentalTime::class)
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

    /**
     * Initializes the ViewModel by loading initial data and setting up the environment.
     */
    fun initialize() {
        viewModelScope.launch {
            _uiState.update { it.copy(loadingMessage = "Initialisiere Sprache...") }
            initializeLanguage()

            _uiState.update { it.copy(loadingMessage = "Prüfe Cloud-Verbindung...") }
            // Diese Funktion wartet jetzt, bis die Verbindung steht.
            initializeSupabaseConnection()

            val isFirstLaunch = activeCardRepository.getAllSets().first().isEmpty()

            val success = if (isFirstLaunch) {
                handleFirstLaunchSetLoading()
            } else {
                handleSubsequentLaunchSetLoading()
            }

            if (success) {
                _uiState.update { it.copy(loadingMessage = "Lade Kartensammlung...") }
                startBackgroundDataListeners()
                val update = UpdateChecker.checkForUpdate()
                _uiState.update { it.copy(isInitialized = true, updateInfo = update, isLoading = false, loadingMessage = null) }
            } else {
                // Beim Erststart bleibt die App im Fehlerzustand, wenn das Laden fehlschlägt.
                _uiState.update { it.copy(isLoading = false, loadingMessage = null) }
            }
        }
    }

    /**
     * Behandelt das Laden der Sets beim allerersten Start der App.
     * Versucht es 3 Mal, bevor ein blockierender Fehler angezeigt wird.
     * @return `true` bei Erfolg, `false` bei endgültigem Fehlschlag.
     */
    private suspend fun handleFirstLaunchSetLoading(): Boolean {
        for (attempt in 1..3) {
            if( attempt > 1) {
                _uiState.update { it.copy(loadingMessage = "Lade Set-Informationen (Versuch $attempt/3)...") }
            }else{
                _uiState.update { it.copy(loadingMessage = "Lade Set-Informationen") }
            }
            val setsFromApi = apiService.getAllSets(uiState.value.appLanguage.code)

            if (setsFromApi.isNotEmpty()) {
                activeCardRepository.syncSets(setsFromApi)
                return true // Erfolg!
            }

            if (attempt < 3) {
                _uiState.update { it.copy(loadingMessage = "Fehler beim Laden. Nächster Versuch in 5 Sekunden...") }
                delay(5000)
            } else {
                // Nach 3 Fehlversuchen wird ein permanenter Fehler angezeigt.
                _uiState.update { it.copy(error = "Konnte Set-Informationen nach 3 Versuchen nicht laden. Bitte prüfe deine Internetverbindung und starte die App neu.") }
                return false // Endgültiger Fehlschlag.
            }
        }
        return false
    }

    /**
     * Behandelt das Laden der Sets bei jedem normalen App-Start (wenn bereits Daten vorhanden sind).
     * @return Immer `true`, da die App mit den zwischengespeicherten Daten fortfahren kann.
     */
    private suspend fun handleSubsequentLaunchSetLoading(): Boolean {
        _uiState.update { it.copy(loadingMessage = "Lade Set-Informationen") }
        val setsFromApi = apiService.getAllSets(uiState.value.appLanguage.code)
        if (setsFromApi.isNotEmpty()) {

            for (set in setsFromApi) {
                println("DEBUG: Set geladen: ${set.nameLocal} (${set.setId}) (${set.id} - ${set.cardCountTotal ?: "unbekannt"} Karten")
            }
            activeCardRepository.syncSets(setsFromApi)
            _uiState.update { it.copy(setsUpdateWarning = null) }
        } else {
            _uiState.update { it.copy(setsUpdateWarning = "Set-Liste konnte nicht aktualisiert werden. Angezeigte Daten sind möglicherweise nicht aktuell.") }
        }
        return true // Die App kann immer fortfahren.
    }

    /**
     * Startet den In-App-Updater mit der angegebenen Download-URL.
     * Erwartet, dass die Datei "updater.jar" im aktuellen Arbeitsverzeichnis vorhanden ist.
     * Nur für Desktop-Plattformen gedacht.
     *
     * @param downloadUrl Die URL, von der die neue Version heruntergeladen werden soll.
     */
    fun startUpdate(downloadUrl: String) {
        val resourcesDir = System.getProperty("compose.application.resources.dir")
        if (resourcesDir == null) {
            _uiState.update { it.copy(error = "Ressourcen-Verzeichnis nicht gefunden. Update nicht möglich.") }
            return
        }
        val updaterJar = File(resourcesDir, "updater.jar")

        if (!updaterJar.exists()) {
            _uiState.update { it.copy(error = "Updater-Datei (updater.jar) nicht gefunden. Pfad: ${updaterJar.absolutePath}") }
            return
        }

        try {
            val fileName = downloadUrl.substringAfterLast('/')
            ProcessBuilder("java", "-jar", updaterJar.absolutePath, downloadUrl, fileName).start()

            // Wenn der Start keine Exception wirft, beenden wir die App sofort.
            // Der Updater läuft jetzt als eigenständiger Prozess.
            exitProcess(0)
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Updater konnte nicht gestartet werden: ${e.message}") }
        }
    }

    fun dismissUpdateDialog() {
        _uiState.update { it.copy(updateInfo = null) }
    }

    // --- Einstellungs- und Sprachlogik ---
    private suspend fun initializeLanguage() {
        var savedLangCode = settingsRepository.getSetting("language")
        if (savedLangCode == null) {
            val systemLangCode = getSystemLanguage()
            val defaultLanguage = AppLanguage.entries.find { it.code == systemLangCode } ?: AppLanguage.ENGLISH
            settingsRepository.saveSetting("language", defaultLanguage.code)
            savedLangCode = defaultLanguage.code
        }
        val language = AppLanguage.entries.find { it.code == savedLangCode } ?: AppLanguage.GERMAN
        setAppLanguage(language.code) // Setzt die Sprache für moko-resources
        _uiState.update { it.copy(appLanguage = language) }
    }

    fun setAppLanguage(language: AppLanguage) {
        viewModelScope.launch {
            // 1. Ladezustand aktivieren und UI blockieren
            setLoading(true, "Sprache wird geändert...")

            // 2. Einstellung speichern und globale Sprache für moko-resources setzen
            settingsRepository.saveSetting("language", language.code)
            setAppLanguage(language.code)

            // 3. UI-Zustand aktualisieren (löst Neukomposition in App.kt aus)
            _uiState.update { it.copy(appLanguage = language) }

            // 4. Sets in der neuen Sprache laden UND auf den Abschluss warten
            loadSets(language).join()

            // 5. Ladezustand deaktivieren und UI wieder freigeben
            setLoading(false)
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

    private fun loadSets(language: AppLanguage? = null): Job {
        setsCollectionJob?.cancel()
        setsCollectionJob = viewModelScope.launch {
            val currentLanguage = language ?: uiState.value.appLanguage
            val setsFromApi = apiService.getAllSets(currentLanguage.code)
            if (setsFromApi.isNotEmpty()) {
                activeCardRepository.syncSets(setsFromApi)
            }
            activeCardRepository.getAllSets().onEach { setsFromDb ->
                _uiState.update { it.copy(sets = setsFromDb.sortedByDescending { it.releaseDate }) }
            }.launchIn(viewModelScope)
        }
        return setsCollectionJob!!
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

    fun fetchCardDetailsByNameAndNumber(
        cardName: String,
        cardNumberInput: String, // z.B. "161/086"
        language: CardLanguage
    ) {
        viewModelScope.launch {
            setLoading(true, "Suche Karte...")
            _uiState.update { it.copy(error = null, apiCardDetails = null) }

            val parts = cardNumberInput.split("/")
            if (parts.size != 2) {//TODO: intl
                _uiState.update { it.copy(error = "Ungültiges Kartenformat. Bitte 'Nummer/Setgröße' verwenden.") }
                setLoading(false)
                return@launch
            }
            val localId = parts[0].trim()
            val officialCount = parts[1].trim().toIntOrNull()

            if (officialCount == null) { //TODO: intl
                _uiState.update { it.copy(error = "Ungültige Setgröße in der Eingabe.") }
                setLoading(false)
                return@launch
            }

            val potentialSets = activeCardRepository.getSetsByOfficialCount(officialCount)
            if (potentialSets.isEmpty()) {
                _uiState.update { it.copy(error = "Keine Sets mit $officialCount offiziellen Karten gefunden.") }
                setLoading(false)
                return@launch
            }

            val cardDetailJobs = potentialSets.map { set ->
                async { apiService.getCardDetails(set.setId, localId, language.code) }
            }

            val foundCards = cardDetailJobs.awaitAll().filterNotNull()

            if (foundCards.isEmpty()) {
                _uiState.update { it.copy(error = "Keine Karte mit der Nummer '$localId' in den passenden Sets gefunden.") }
                setLoading(false)
                return@launch
            }

            val normalizedInputName = cardName.replace(Regex("[\\s-]"), "").lowercase()
            val bestMatch = foundCards.find { card ->
                val normalizedCardName = card.name.replace(Regex("[\\s-]"), "").lowercase()
                normalizedCardName.contains(normalizedInputName)
            }

            if (bestMatch == null) {
                _uiState.update { it.copy(error = "Keine Karte mit dem Namen '$cardName' in den gefundenen Sets gefunden.") }
            } else {
                _uiState.update { it.copy(apiCardDetails = bestMatch, searchedCardLanguage = language) }
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
                val englishCardDetails =
                    apiService.getCardDetails(cardDetails.set.id, cardDetails.localId, CardLanguage.ENGLISH.code)
                if (englishCardDetails == null) {
                    _uiState.update { it.copy(error = "Konnte englische Kartendetails nicht abrufen.") }
                    setLoading(false)
                    return@launch
                }
                saveNewCard(
                    cardDetails,
                    englishCardDetails,
                    abbreviation,
                    price,
                    languageCode,
                    cardMarketLink,
                    ownedCopies,
                    notes
                )
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
            legalJson = localCardDetails.legal?.let { Json.encodeToString(it) }
        )

        // Übergebe das einzelne Objekt an das Repository
        activeCardRepository.insertFullPokemonCard(newCard)

        if(abbreviation != null) {
            activeCardRepository.updateSetAbbreviation(
                setId = localCardDetails.set.id,
                abbreviation = abbreviation
            )
        }

        loadCardInfos()
    }

    // --- Supabase Logik ---

    private suspend fun initializeSupabaseConnection() {
        val url = settingsRepository.getSetting("supabase_url") ?: ""
        val key = settingsRepository.getSetting("supabase_key") ?: ""
        _uiState.update { it.copy(supabaseUrl = url, supabaseKey = key) }
        if (url.isNotBlank() && key.isNotBlank()) {
            connectToSupabase(url, key)
        }
    }

    private suspend fun connectToSupabase(url: String, key: String) {
        try {
            val supabase = createSupabaseClient(url, key) { install(Postgrest) }
            supabase.postgrest.from("PokemonCardEntity").select { limit(1) }
            remoteCardRepository = SupabaseCardRepository(supabase.postgrest)
            _uiState.update { it.copy(isSupabaseConnected = true) }
        } catch (e: Exception) {
            println("Supabase-Verbindung beim Start fehlgeschlagen: ${e.message}")
            remoteCardRepository = null
            _uiState.update { it.copy(isSupabaseConnected = false) }
        }
    }

    private suspend fun loadInitialData() {
        // Lade Sets
        val currentLanguage = uiState.value.appLanguage
        val setsFromApi = apiService.getAllSets(currentLanguage.code)
        if (setsFromApi.isNotEmpty()) {
            activeCardRepository.syncSets(setsFromApi)
        }
        val initialSets = activeCardRepository.getAllSets().first()

        // Lade Karten
        val initialCards = activeCardRepository.getCardInfos().first()

        // Aktualisiere den State mit beiden Ergebnissen auf einmal
        _uiState.update {
            it.copy(
                sets = initialSets.sortedByDescending { set -> set.releaseDate },
                cardInfos = initialCards
            )
        }
    }

    private fun startBackgroundDataListeners() {
        listenForCardUpdates()
        listenForSetUpdates()
    }

    private fun listenForCardUpdates() {
        cardInfosCollectionJob?.cancel()
        cardInfosCollectionJob = activeCardRepository.getCardInfos()
            .onEach { cards -> _uiState.update { it.copy(cardInfos = cards) } }
            .launchIn(viewModelScope)
    }

    private fun listenForSetUpdates() {
        setsCollectionJob?.cancel()
        setsCollectionJob = activeCardRepository.getAllSets()
            .onEach { setsFromDb ->
                _uiState.update { it.copy(sets = setsFromDb.sortedByDescending { it.releaseDate }) }
            }
            .launchIn(viewModelScope)
    }

    fun connectNewToSupabase(url: String, key: String) {
        viewModelScope.launch {
            setLoading(true, "Verbinde mit Supabase...")
            try {
                val supabase = createSupabaseClient(url, key) { install(Postgrest) }
                supabase.postgrest.from("PokemonCardEntity").select { limit(1) }

                // Verbindung erfolgreich, speichere Einstellungen und wechsle das Repository
                settingsRepository.saveSetting("supabase_url", url)
                settingsRepository.saveSetting("supabase_key", key)
                remoteCardRepository = SupabaseCardRepository(supabase.postgrest)
                _uiState.update { it.copy(isSupabaseConnected = true, supabaseKey = key, supabaseUrl = url) }

                // Lade die Sets neu. Dies synchronisiert die API-Sets mit der neuen Supabase-DB.
                loadSets().join()
                // Lade die Karteninfos neu (jetzt von Supabase).
                loadCardInfos()

                // Prüfe, ob eine Migration von LOKALEN KARTEN zur (jetzt leeren) Cloud-DB nötig ist.
                checkForSync()

            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Verbindung zu Supabase fehlgeschlagen: ${e.message}") }
            }
            setLoading(false)
        }
    }

    /**
     * Leitet den Disconnect-Prozess ein, indem ein Bestätigungsdialog angezeigt wird.
     */
    fun disconnectFromSupabase() {
        if (remoteCardRepository != null) {
            _uiState.update { it.copy(disconnectPromptMessage = "Möchten Sie Ihre Cloud-Sammlung auf dieses Gerät herunterladen, bevor die Verbindung getrennt wird? Nicht heruntergeladene Daten bleiben in Ihrer Supabase-Cloud, sind aber in der App nicht mehr sichtbar.") }
        }
    }

    /**
     * Schließt den Disconnect-Dialog ohne Aktion.
     */
    fun dismissDisconnectPrompt() {
        _uiState.update { it.copy(disconnectPromptMessage = null) }
    }

    /**
     * Führt die eigentliche Trennung von Supabase durch, nachdem der Nutzer eine Wahl getroffen hat.
     * @param migrateData Wenn true, werden die Daten von der Cloud zur lokalen DB migriert.
     */
    fun confirmDisconnect(migrateData: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(disconnectPromptMessage = null) }

            if (migrateData && remoteCardRepository != null) {
                setLoading(true, "Migriere Daten von Cloud zu Lokal...")
                val setsToMigrate = remoteCardRepository!!.getAllSets().first()
                val cardsToMigrate = remoteCardRepository!!.getCardInfos().first().mapNotNull {
                    remoteCardRepository!!.getFullCardDetails(it.id)
                }
                localCardRepository.clearAllData()
                localCardRepository.syncSets(setsToMigrate)
                cardsToMigrate.forEach { card ->
                    localCardRepository.insertFullPokemonCard(card)
                }
            }

            setLoading(true, "Trenne Verbindung...")
            settingsRepository.saveSetting("supabase_url", "")
            settingsRepository.saveSetting("supabase_key", "")
            remoteCardRepository = null // Repository-Wechsel
            _uiState.update { it.copy(isSupabaseConnected = false, supabaseUrl = "", supabaseKey = "") }

            // Lade die Daten neu (jetzt aus der lokalen DB).
            // loadSets synchronisiert die lokale DB wieder mit der API.
            loadSets().join()
            loadCardInfos()

            setLoading(false)
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

            loadCardInfos()
            setLoading(false)
        }
    }

    fun dismissSyncPrompt() = _uiState.update { it.copy(syncPromptMessage = null) }
    fun resetApiCardDetails() = _uiState.update { it.copy(apiCardDetails = null, searchedCardLanguage = null) }
    private fun setLoading(isLoading: Boolean, message: String? = null) =
        _uiState.update { it.copy(isLoading = isLoading, loadingMessage = message) }
    fun dismissSetsUpdateWarning() { _uiState.update { it.copy(setsUpdateWarning = null) } }

    fun clearSelectedCard() = _uiState.update { it.copy(selectedCardDetails = null) }
    fun clearError() = _uiState.update { it.copy(error = null) }
}