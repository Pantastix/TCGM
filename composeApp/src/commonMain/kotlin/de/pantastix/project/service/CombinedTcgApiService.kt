package de.pantastix.project.service

import de.pantastix.project.model.SetInfo
import de.pantastix.project.model.api.TcgDexCardResponse
import de.pantastix.project.ui.viewmodel.CardLanguage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class CombinedTcgApiService(
    private val localApiService: TcgApiService,
    private val enApiService: TcgApiService,
//    private val abbreviationApiService: TcgApiService,
): TcgApiService {
    override fun getName(): String {
        return "Combined TCG API Service"
    }

    private fun normalizeSetId(id: String): String {
        // Regel 1: McDonald's Kollektionen (zuerst, da sehr spezifisch)
        // Beispiel: "mcdonalds2021" -> "mcd21"
        if (id.startsWith("mcdonalds20")) {
            return "mcd" + id.substring(11)
        }

        // Regel 2: Suffix ".5" ersetzen
        // Beispiel: "swsh12.5" -> "swsh12pt5"
        var normalized = id.replace(".5", "pt5")

        // Regel 3: Führende Nullen bei Nummern entfernen
        // Beispiel: "sv04" -> "sv4"
        // Regex: Findet eine Gruppe von Buchstaben ([a-zA-Z]+), gefolgt von '0', gefolgt vom Rest (\\d+.*).
        val leadingZeroRegex = Regex("([a-zA-Z]+)0(\\d+.*)")
        if (leadingZeroRegex.matches(normalized)) {
            normalized = leadingZeroRegex.replace(normalized, "$1$2")
        }

        return normalized
    }

    private fun denormalizeSetId(id: String): String {
        return id.replace("pt5", ".5")
    }

    /**
     * Ruft alle Sets ab, kombiniert und priorisiert Daten von TCGdex.net und PokemonTCG.io.
     * @param language Die gewünschte Sprache für lokale Set-Namen.
     * @return Eine Liste von [SetInfo]-Objekten, die kombinierte Daten enthalten.
     */
    override suspend fun getAllSets(language: String): List<SetInfo> = coroutineScope {
        try {
            // Parallel die Daten von beiden APIs abrufen
            val enSetsDeferred = async { enApiService.getAllSets(CardLanguage.ENGLISH.code) } // Immer englische Sets von PokemonTCG.io
            val localSetsDeferred = async { localApiService.getAllSets(language) } // Lokale Sets von TCGdex.net

            val enSets = enSetsDeferred.await()
            val localSets = localSetsDeferred.await()

            // Maps für schnellen Zugriff erstellen
            val localSetMap = localSets.associateBy { normalizeSetId(it.setId) }

            println("Fetched ${enSets.size} sets from ${enApiService.getName()} and ${localSets.size} sets from ${localApiService.getName()}.")
            println("Local sets map size: ${localSetMap.size}")

            // Über die PokemonTCG.io Sets iterieren (diese haben Vorrang)
            enSets.mapNotNull { enSet ->
                val normalizedId = normalizeSetId(enSet.setId)
                val localSet = localSetMap[normalizedId]

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
                    println("Set '${enSet.nameEn}' (${enSet.setId}) has no local equivalent. Using English data as fallback.")
                    SetInfo(
                        setId = enSet.setId,
                        abbreviation = enSet.abbreviation,
                        nameLocal = enSet.nameEn,
                        nameEn = enSet.nameEn,
                        logoUrl = enSet.logoUrl,
                        cardCountOfficial = enSet.cardCountOfficial,
                        cardCountTotal = enSet.cardCountTotal,
                        releaseDate = enSet.releaseDate
                    )
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