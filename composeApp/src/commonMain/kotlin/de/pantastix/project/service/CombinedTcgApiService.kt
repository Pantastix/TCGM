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

    /**
     * Ruft alle Sets ab, kombiniert und priorisiert Daten von TCGdex.net und PokemonTCG.io.
     * @param language Die gewünschte Sprache für lokale Set-Namen.
     * @return Eine Liste von [SetInfo]-Objekten, die kombinierte Daten enthalten.
     */
    override suspend fun getAllSets(language: String): List<SetInfo> = coroutineScope {
        try {
            val tcgIoSetsDeferred = async { enApiService.getAllSets(CardLanguage.ENGLISH.code) }
            val tcgDexSetsDeferred = async { localApiService.getAllSets(language) }

            val rawTcgIoSets = tcgIoSetsDeferred.await()
            val tcgDexSets = tcgDexSetsDeferred.await()

            val tcgIoSetsByPtcgCode = rawTcgIoSets
                .filter { !it.abbreviation.isNullOrBlank() }
                .groupBy { it.abbreviation!! }

            val mergedSets = tcgIoSetsByPtcgCode.map { (_, group) ->
                if (group.size > 1) {
                    val baseSet = group.first()
                    val totalOfficial = group.sumOf { it.cardCountOfficial }
                    val totalCards = group.sumOf { it.cardCountTotal }
                    baseSet.copy(
                        cardCountOfficial = totalOfficial,
                        cardCountTotal = totalCards
                    )
                } else {
                    group.first()
                }
            }
            val setsWithoutPtcgCode = rawTcgIoSets.filter { it.abbreviation.isNullOrBlank() }
            val tcgIoSets = mergedSets + setsWithoutPtcgCode


            // 2. Erstelle zwei Maps der TCGdex-Sets für schnellen Zugriff.
            val tcgDexSetMapByName = tcgDexSets.associateBy { it.nameEn.lowercase().replace(Regex("\\s|'"), "") }
            // Die Schlüssel dieser Map sind jetzt normalisiert.
            val tcgDexSetMapById = tcgDexSets.associateBy { normalizeSetId(it.setId) }

            println("Fetched ${tcgIoSets.size} sets from ${enApiService.getName()} and ${tcgDexSets.size} sets from ${localApiService.getName()}.")

            tcgIoSets.mapNotNull { tcgIoSet ->
                val normalizedEnName = tcgIoSet.nameEn.lowercase().replace(Regex("\\s|'"), "")
                var tcgDexSet = tcgDexSetMapByName[normalizedEnName]

                if (tcgDexSet == null) {
                    val normalizedId = normalizeSetId(tcgIoSet.setId)
                    tcgDexSet = tcgDexSetMapById[normalizedId]
                    if (tcgDexSet != null) {
//                        println("Set '${tcgIoSet.nameEn}' wurde über die ID '${tcgIoSet.setId}' als Fallback gefunden.")
                    }
                }

                if (tcgDexSet != null) {
                    SetInfo(
                        setId = tcgIoSet.setId, // ID von TCG.io ist führend
                        abbreviation = tcgIoSet.abbreviation, // Abkürzung von TCG.io
                        nameLocal = tcgDexSet.nameLocal, // Lokaler Name von TCGdex
                        nameEn = tcgIoSet.nameEn, // Englischer Name von TCG.io
                        logoUrl = tcgDexSet.logoUrl, // Logo von TCGdex
                        cardCountOfficial = tcgDexSet.cardCountOfficial, // Kartenzahl von TCGdex
                        cardCountTotal = tcgDexSet.cardCountTotal, // Kartenzahl von TCGdex
                        releaseDate = tcgIoSet.releaseDate // Release-Datum von TCG.io
                    )
                } else {
                    println("Set '${tcgIoSet.nameEn}' hat kein Gegenstück in TCGdex. Wird aus der Liste entfernt.")
                    null
                }
            }
        } catch (e: Exception) {
            println("Error while fetching and combining sets: ${e.message}")
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