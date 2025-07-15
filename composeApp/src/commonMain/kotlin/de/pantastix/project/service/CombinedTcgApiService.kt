package de.pantastix.project.service

import de.pantastix.project.model.SetInfo
import de.pantastix.project.model.api.TcgDexCardResponse
import de.pantastix.project.ui.viewmodel.CardLanguage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class CombinedTcgApiService(
    private val localApiService: TcgApiService,
    private val EnApiService: TcgApiService
): TcgApiService {


    /**
     * Ruft alle Sets ab, kombiniert und priorisiert Daten von TCGdex.net und PokemonTCG.io.
     * @param language Die gewünschte Sprache für lokale Set-Namen.
     * @return Eine Liste von [SetInfo]-Objekten, die kombinierte Daten enthalten.
     */
    override suspend fun getAllSets(language: String): List<SetInfo> = coroutineScope {
        try {
            // Parallel die Daten von beiden APIs abrufen
            val enSetsDeferred = async { EnApiService.getAllSets(CardLanguage.ENGLISH.code) } // Immer englische Sets von PokemonTCG.io
            val localSetsDeferred = async { localApiService.getAllSets(language) } // Lokale Sets von TCGdex.net

            val enSets = enSetsDeferred.await()
            val localSets = localSetsDeferred.await()

            // Maps für schnellen Zugriff erstellen
            val localSetMap = localSets.associateBy { it.setId }

            // Über die PokemonTCG.io Sets iterieren (diese haben Vorrang)
            enSets.mapNotNull { enSet ->
                val localSet = localSetMap[enSet.setId]

                // Nur Sets berücksichtigen, die in beiden Quellen existieren
                if (localSet != null) {
                    SetInfo(
                        setId = enSet.setId,
                        abbreviation = enSet.abbreviation, // Von PokemonTCG.io
                        nameLocal = localSet.nameLocal, // Von TCGdex.net (lokal)
                        nameEn = enSet.nameEn, // Von PokemonTCG.io (englisch)
                        logoUrl = localSet.logoUrl, // Von TCGdex.net
                        cardCountOfficial = localSet.cardCountOfficial, // Von TCGdex.net
                        cardCountTotal = localSet.cardCountTotal, // Von TCGdex.net
                        releaseDate = enSet.releaseDate // Von PokemonTCG.io (geparst)
                    )
                } else {
                    // Set von PokemonTCG.io hat keine Entsprechung in den lokalen TCGdex Sets
                    // und wird daher übersprungen.
                    println("Set '${enSet.nameEn}' (${enSet.setId}) from English API has no local equivalent in TCGdex.")
                    null
                }
            }
        } catch (e: Exception) {
            println("Error while fetching sets: ${e.message}")
            emptyList()
        }
    }

    /**
     * Delegiert den Aufruf für Kartendetails an den TcgDexApiServiceImpl,
     * da PokemonTCG.io diese Funktionalität nicht bietet.
     * @param setId Die offizielle Set-ID.
     * @param localId Die Nummer der Karte im Set.
     * @param languageCode Der Sprachcode für die Kartendetails.
     * @return Ein [TcgDexCardResponse]-Objekt oder null bei einem Fehler.
     */
    override suspend fun getCardDetails(setId: String, localId: String, languageCode: String): TcgDexCardResponse? {
        return localApiService.getCardDetails(setId, localId, languageCode)
    }

}