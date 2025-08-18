package de.pantastix.project.service

import de.pantastix.project.model.SetInfo
import de.pantastix.project.model.api.TcgDexCardResponse
import de.pantastix.project.model.api.TcgDexCardSearchResult
import de.pantastix.project.model.api.TcgDexSet
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Die konkrete Implementierung des TcgDexApiService mit dem Ktor HTTP-Client.
 */
class TcgDexApiService(
    private val client: HttpClient // KORRIGIERT: Der Client wird jetzt hier im Konstruktor erwartet
) : TcgApiService {

    private val json = Json { ignoreUnknownKeys = true }
    private val baseUrl = "https://api.tcgdex.net/v2"

    private val renameList = mapOf(
        "Macdonald" to "McDonald",
        "HeartGold SoulSilver" to "HeartGold & SoulSilver",
        "(Latias)" to "Latias",
        "(Latios)" to "Latios",
        "(Plusle)" to "Plusle",
        "(Minun)" to "Minun",
        "Pokémon Futsal 2020" to "Pokémon Futsal Collection",
    )

    override fun getName(): String {
        return "TCGdex.net API Service"
    }

    /**
     * Wendet die Korrekturen aus der `renameList` auf einen Set-Namen an.
     */
    private fun normalizeSetName(name: String): String {
        var normalizedName = name
        renameList.forEach { (key, value) ->
            if (normalizedName.contains(key, ignoreCase = true)) {
                normalizedName = normalizedName.replace(key, value, ignoreCase = true)
            }
        }
        return normalizedName
    }

    override suspend fun getAllSets(language: String): List<SetInfo> = coroutineScope {
        // Wenn die angeforderte Sprache bereits Englisch ist, machen wir nur eine Abfrage.
        if (language == "en") {
            return@coroutineScope fetchSingleLanguage("en")
        }

        try {
            // 1. Rufe die lokale und die englische Set-Liste parallel ab.
            val localSetsDeferred = async { fetchSetData(language) }
            val enSetsDeferred = async { fetchSetData("en") }

            val localSets = localSetsDeferred.await()
            val enSetsRaw = enSetsDeferred.await() // Die englische Liste ist die Master-Liste.

            // 2. Erstelle eine Map der lokalen Sets für schnellen Zugriff über die ID.
            val localSetMap = localSets.associateBy { it.id }

            val filterRegex = Regex("^A\\d+[a-zA-Z]?$")
            val enSets = enSetsRaw.filterNot { filterRegex.matches(it.id) }

            // 3. Iteriere durch die englische Master-Liste und kombiniere sie mit den lokalen Daten.
            enSets.mapIndexed { index, enSet ->
                val localSet = localSetMap[enSet.id]

                // Wende die Namensnormalisierung an
                val normalizedEnName = normalizeSetName(enSet.name)
                val normalizedLocalName = localSet?.name?.let { normalizeSetName(it) } ?: normalizedEnName

                // Erstelle das SetInfo-Objekt.
                SetInfo(
                    id = index,
                    setId = enSet.id,
                    abbreviation = null,
                    nameLocal = normalizedLocalName,
                    nameEn = normalizedEnName,
                    logoUrl = localSet?.logo ?: localSet?.symbol ?: enSet.logo ?: enSet.symbol,
                    cardCountOfficial = localSet?.cardCount?.official ?: enSet.cardCount?.official ?: 0,
                    cardCountTotal = localSet?.cardCount?.total ?: enSet.cardCount?.total ?: 0,
                    releaseDate = null
                )
            }
        } catch (e: Exception) {
            println("Fehler beim Abrufen der kombinierten Set-Listen von TCGdex.net: ${e.message}")
            emptyList()
        }
    }

    /**
     * Hilfsfunktion, um die rohen Set-Daten für eine einzelne Sprache abzurufen und zu parsen.
     */
    private suspend fun fetchSetData(language: String): List<TcgDexSet> {
        return try {
            val rawJsonArray = client.get("$baseUrl/$language/sets").body<JsonArray>()
            rawJsonArray.mapNotNull { jsonElement ->
                try {
                    json.decodeFromJsonElement<TcgDexSet>(jsonElement)
                } catch (e: Exception) {
                    println("!! Fehler beim Parsen eines einzelnen TCGdex-Sets ($language): ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            println("Fehler beim Abrufen der Set-Liste von TCGdex.net für Sprache '$language': ${e.message}")
            emptyList()
        }
    }

    /**
     * Vereinfachte Funktion für den Fall, dass nur Englisch abgefragt wird.
     */
    private suspend fun fetchSingleLanguage(language: String): List<SetInfo> {
        val rawSets = fetchSetData(language)

        val filterRegex = Regex("^A\\d+[a-zA-Z]?$")
        val filteredSets = rawSets.filterNot { filterRegex.matches(it.id) }

        return filteredSets.mapIndexed { index, set ->
            SetInfo(
                id = index,
                setId = set.id,
                abbreviation = null,
                nameLocal = set.name,
                nameEn = set.name,
                logoUrl = set.logo ?: set.symbol,
                cardCountOfficial = set.cardCount?.official ?: 0,
                cardCountTotal = set.cardCount?.total ?: 0,
                releaseDate = null
            )
        }
    }

//    override suspend fun getCardDetails(setId: String, localId: String, languageCode: String): TcgDexCardResponse? {
//        return try {
//            client.get("$baseUrl/$languageCode/sets/$setId/$localId").body<TcgDexCardResponse>()
//        } catch (e: Exception) {
//            println("Fehler bei getCardDetails für $languageCode/$setId/$localId: ${e.message}")
//            null
//        }
//    }

    override suspend fun getCardDetails(setId: String, localId: String, languageCode: String): TcgDexCardResponse? {
        // Schritt 1: Rufe die primären Kartendetails ab.
        val cardDetails = fetchPrimaryCardDetails(setId, localId, languageCode) ?: return null

        try {
            // Schritt 2: Rufe alle Karten mit demselben Namen ab, um die Version zu ermitteln.
            val allVersions = client.get("$baseUrl/$languageCode/cards") {
                parameter("name", cardDetails.name)
            }.body<List<TcgDexCardSearchResult>>()

            // Filtere die Ergebnisse, um nur Karten aus demselben Set zu behalten.
            val versionsInSameSet = allVersions.filter { it.id.startsWith("$setId-") }

            // Finde den Index (die Position) unserer spezifischen Karte in dieser gefilterten Liste.
            val versionIndex = versionsInSameSet.indexOfFirst { it.id == cardDetails.id }

            // Die Version ist der Index + 1 (da Zählung bei 1 beginnt).
            // Wenn die Karte nicht gefunden wird, bleibt die Version null.
            if (versionIndex != -1) {
                cardDetails.cardmarketVersion = versionIndex + 1
                cardDetails.totalCardmarketVersions = versionsInSameSet.size
                println("Cardmarket-Version für '${cardDetails.name}' (${cardDetails.id}) ermittelt: ${cardDetails.cardmarketVersion}")
            }

        } catch (e: Exception) {
            println("Fehler beim Ermitteln der Cardmarket-Version für ${cardDetails.id}: ${e.message}")
            // Fehler hier ist nicht kritisch, wir geben trotzdem die Basiskarte zurück.
        }

        return cardDetails
    }

    /**
     * Private Hilfsfunktion, die nur die Basis-Kartendetails von der API abruft.
     */
    private suspend fun fetchPrimaryCardDetails(setId: String, localId: String, languageCode: String): TcgDexCardResponse? {
        return try {
            client.get("$baseUrl/$languageCode/sets/$setId/$localId").body<TcgDexCardResponse>()
        } catch (e: Exception) {
            println("Fehler bei getCardDetails für $languageCode/$setId/$localId: ${e.message}")
            null
        }
    }
}