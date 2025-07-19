package de.pantastix.project.service

import de.pantastix.project.model.SetInfo
import de.pantastix.project.model.api.TcgDexCardResponse
import de.pantastix.project.model.api.TcgDexSet
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
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

    override fun getName(): String {
        return "TCGdex.net API Service"
    }

//    override suspend fun getAllSets(language: String): List<SetInfo> = coroutineScope {
//        try {
//            val tcgDexSets = client.get("$baseUrl/$language/sets").body<List<TcgDexSet>>()
//
//            tcgDexSets.map { tcgDexSet ->
//                SetInfo(
//                    setId = tcgDexSet.id,
//                    abbreviation = null,
//                    nameLocal = tcgDexSet.name,
//                    nameEn = tcgDexSet.name,
//                    logoUrl = tcgDexSet.logo ?: tcgDexSet.symbol,
//                    cardCountOfficial = tcgDexSet.cardCount?.official ?: 0,
//                    cardCountTotal = tcgDexSet.cardCount?.total ?: 0,
//                    releaseDate = null
//                )
//            }
//        } catch (e: Exception) {
//            println("Fehler beim Abrufen der Sets von TCGdex.net für Sprache '$language': ${e.message}")
//            emptyList()
//        }
//    }

    override suspend fun getAllSets(language: String): List<SetInfo> {
        return try {
            // 1. Rufe die Antwort als generisches JsonArray ab, nicht als typisierte Liste.
            val rawJsonArray = client.get("$baseUrl/$language/sets").body<JsonArray>()

            println("TCGdex API hat ${rawJsonArray.size} Set-Objekte in der Roh-Antwort geliefert.")

            // 2. Verwende mapNotNull, um jedes Element sicher zu verarbeiten.
            rawJsonArray.mapNotNull { jsonElement ->
                try {
                    // 3. Versuche, ein einzelnes Element in unser TcgDexSet-Objekt zu dekodieren.
                    val tcgDexSet = json.decodeFromJsonElement<TcgDexSet>(jsonElement)

                    // Erfolgreich dekodiert, jetzt in das gemeinsame SetInfo-Modell umwandeln.
                    SetInfo(
                        setId = tcgDexSet.id,
                        abbreviation = null,
                        nameLocal = tcgDexSet.name,
                        nameEn = tcgDexSet.name, // Wird später vom CombinedService überschrieben
                        logoUrl = tcgDexSet.logo ?: tcgDexSet.symbol,
                        cardCountOfficial = tcgDexSet.cardCount?.official ?: 0,
                        cardCountTotal = tcgDexSet.cardCount?.total ?: 0,
                        releaseDate = null
                    )
                } catch (e: Exception) {
                    // 4. Wenn die Dekodierung für DIESES EINE Set fehlschlägt:
                    println("!! Fehler beim Verarbeiten eines einzelnen Sets von TCGdex.net. Set wird übersprungen. Fehler: ${e.message}")
                    println("!! Fehlerhaftes JSON-Element: $jsonElement")
                    null // mapNotNull wird dieses fehlerhafte Element aus der finalen Liste entfernen.
                }
            }
        } catch (e: Exception) {
            // Dieser Catch-Block fängt jetzt nur noch größere Probleme ab (z.B. Netzwerkfehler).
            println("Schwerwiegender Fehler beim Abrufen der Set-Liste von TCGdex.net für Sprache '$language': ${e.message}")
            emptyList()
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