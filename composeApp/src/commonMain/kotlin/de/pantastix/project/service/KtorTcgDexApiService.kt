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

//    private val client = HttpClient {
//        install(ContentNegotiation) {
//            json(Json {
//                ignoreUnknownKeys = true
//                isLenient = true
//            })
//        }
//    }

    private val baseUrl = "https://api.tcgdex.net/v2"

    override suspend fun getAllSets(): List<SetInfo> = coroutineScope {
        try {
            // Explizite Typangabe im body<...>() Aufruf behebt den Typinferenz-Fehler.
            val germanSetsDeferred = async { client.get("$baseUrl/de/sets").body<List<TcgDexSet>>() }
            val englishSetsDeferred = async { client.get("$baseUrl/en/sets").body<List<TcgDexSet>>() }

            val germanSets = germanSetsDeferred.await()
            val englishSets = englishSetsDeferred.await()

            val englishSetMap = englishSets.associateBy { it.id }

            // Der map-Zugriff funktioniert jetzt, da die Typen klar sind.
            germanSets.mapNotNull { germanSet ->
                englishSetMap[germanSet.id]?.let { englishSet ->
                    SetInfo(
                        setId = germanSet.id,
                        nameDe = germanSet.name,
                        nameEn = englishSet.name,
                        logoUrl = germanSet.logo ?: englishSet.logo
                    )
                }
            }
        } catch (e: Exception) {
            println("Fehler beim Abrufen der Set-Liste: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getGermanCardDetails(setId: String, localId: String): TcgDexCardResponse? {
        return try {
            // Explizite Typangabe im body<...>() Aufruf behebt den Typinferenz-Fehler.
            client.get("$baseUrl/de/sets/$setId/$localId").body<TcgDexCardResponse>()
        } catch (e: Exception) {
            println("Fehler beim Abrufen der deutschen Kartendetails für $setId/$localId: ${e.message}")
            null
        }
    }

    override suspend fun getEnglishCardDetails(setId: String, localId: String): TcgDexCardResponse? {
        return try {
            // Explizite Typangabe im body<...>() Aufruf behebt den Typinferenz-Fehler.
            client.get("$baseUrl/en/sets/$setId/$localId").body<TcgDexCardResponse>()
        } catch (e: Exception) {
            println("Fehler beim Abrufen der englischen Kartendetails für $setId/$localId: ${e.message}")
            null
        }
    }
}