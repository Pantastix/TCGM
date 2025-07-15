package de.pantastix.project.service

import de.pantastix.project.model.SetInfo
import de.pantastix.project.model.api.TcgDexCardResponse
import de.pantastix.project.model.api.TcgDexSet
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Die konkrete Implementierung des TcgDexApiService mit dem Ktor HTTP-Client.
 */
class TcgDexApiService(
    private val client: HttpClient // KORRIGIERT: Der Client wird jetzt hier im Konstruktor erwartet
) : TcgApiService {

    private val baseUrl = "https://api.tcgdex.net/v2"

    override suspend fun getAllSets(language: String): List<SetInfo> = coroutineScope {
        try {
            val tcgDexSets = client.get("$baseUrl/$language/sets").body<List<TcgDexSet>>()

            tcgDexSets.map { tcgDexSet ->
                SetInfo(
                    setId = tcgDexSet.id,
                    abbreviation = null,
                    nameLocal = tcgDexSet.name,
                    nameEn = tcgDexSet.name,
                    logoUrl = tcgDexSet.logo ?: tcgDexSet.symbol,
                    cardCountOfficial = tcgDexSet.cardCount?.official ?: 0,
                    cardCountTotal = tcgDexSet.cardCount?.total ?: 0,
                    releaseDate = null
                )
            }
        } catch (e: Exception) {
            println("Fehler beim Abrufen der Sets von TCGdex.net für Sprache '$language': ${e.message}")
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