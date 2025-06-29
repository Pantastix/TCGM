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
class KtorTcgDexApiService(
    private val client: HttpClient // KORRIGIERT: Der Client wird jetzt hier im Konstruktor erwartet
) : TcgDexApiService {

    private val baseUrl = "https://api.tcgdex.net/v2"

    override suspend fun getAllSets(language: String): List<SetInfo> = coroutineScope {
        try {
            val localSetsDeferred = async { client.get("$baseUrl/$language/sets").body<List<TcgDexSet>>() }
            val englishSetsDeferred = async { client.get("$baseUrl/en/sets").body<List<TcgDexSet>>() }

            // KORRIGIERT: Hier stand fälschlicherweise `germanSetsDeferred`.
            val localSets = localSetsDeferred.await()
            val englishSets = englishSetsDeferred.await()

            val englishSetMap = englishSets.associateBy { it.id }

            localSets.mapNotNull { localSet ->
                englishSetMap[localSet.id]?.let { englishSet ->
                    // KORRIGIERT: Das `SetInfo`-Objekt wird jetzt mit allen verfügbaren Daten erstellt.
                    SetInfo(
                        setId = localSet.id,
                        abbreviation = null, // Die API liefert kein Kürzel, wird manuell gesetzt
                        nameLocal = localSet.name,
                        nameEn = englishSet.name,
                        logoUrl = localSet.logo ?: localSet.symbol,
                        cardCountOfficial = localSet.cardCount?.official ?: 0,
                        cardCountTotal = localSet.cardCount?.total ?: 0,
                        releaseDate = null // Die API liefert kein Datum in dieser Ansicht
                    )
                }
            }
        } catch (e: Exception) {
            println("Fehler beim Abrufen der Set-Liste: ${e.message}")
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