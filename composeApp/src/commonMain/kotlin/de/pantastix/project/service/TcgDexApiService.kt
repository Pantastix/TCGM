package de.pantastix.project.service

import de.pantastix.project.model.SetInfo
import de.pantastix.project.model.api.TcgDexCardResponse
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
            val enSets = enSetsDeferred.await() // Die englische Liste ist die Master-Liste.

            // 2. Erstelle eine Map der lokalen Sets für schnellen Zugriff über die ID.
            val localSetMap = localSets.associateBy { it.id }

            // 3. Iteriere durch die englische Master-Liste und kombiniere sie mit den lokalen Daten.
            enSets.map { enSet ->
                val localSet = localSetMap[enSet.id]

                // Wende die Namensnormalisierung an
                val normalizedEnName = normalizeSetName(enSet.name)
                val normalizedLocalName = localSet?.name?.let { normalizeSetName(it) } ?: normalizedEnName

                // Erstelle das SetInfo-Objekt.
                SetInfo(
                    setId = enSet.id,
                    tcgIoSetId = null,
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
        return fetchSetData(language).map { set ->
            SetInfo(
                setId = set.id,
                tcgIoSetId = null,
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

    override suspend fun getCardDetails(setId: String, localId: String, languageCode: String): TcgDexCardResponse? {
        return try {
            client.get("$baseUrl/$languageCode/sets/$setId/$localId").body<TcgDexCardResponse>()
        } catch (e: Exception) {
            println("Fehler bei getCardDetails für $languageCode/$setId/$localId: ${e.message}")
            null
        }
    }
}