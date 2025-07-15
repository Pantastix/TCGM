package de.pantastix.project.service

import de.pantastix.project.model.SetInfo
import de.pantastix.project.model.api.PokemonTcgIoSetResponse
import de.pantastix.project.model.api.TcgDexCardResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class TcgIoApiService (val client: HttpClient // KORRIGIERT: Der Client wird jetzt hier im Konstruktor erwartet
) : TcgApiService {

    private val baseUrl = "https://api.pokemontcg.io/v2"

    /**
     * Ruft alle englischen Sets von PokemonTCG.io ab.
     * Der 'language'-Parameter wird hier ignoriert, da diese API nur englische Daten liefert.
     * @param language Der Sprachcode (wird hier ignoriert).
     * @return Eine Liste von [SetInfo]-Objekten mit englischen Daten von PokemonTCG.io.
     */
    override suspend fun getAllSets(language: String): List<SetInfo> {
        return try {
            val pokemonTcgIoSets = client.get("$baseUrl/sets").body<PokemonTcgIoSetResponse>().data

            pokemonTcgIoSets.map { pokemonTcgIoSet ->
                val releaseDate = try {
                    // Datum parsieren von YYYY/MM/DD zu YYYY-MM-DD
                    val parsedDate = LocalDate.parse(pokemonTcgIoSet.releaseDate, DateTimeFormatter.ofPattern("yyyy/MM/dd"))
                    parsedDate.format(DateTimeFormatter.ISO_LOCAL_DATE) // Format zu YYYY-MM-DD
                } catch (e: Exception) {
                    println("Fehler beim Parsen des Datums '${pokemonTcgIoSet.releaseDate}' für Set ${pokemonTcgIoSet.id}: ${e.message}")
                    null
                }

                SetInfo(
                    setId = pokemonTcgIoSet.id,
                    abbreviation = pokemonTcgIoSet.ptcgoCode,
                    nameLocal = pokemonTcgIoSet.name, // Hier wird der englische Name als lokaler Name verwendet
                    nameEn = pokemonTcgIoSet.name,
                    logoUrl = pokemonTcgIoSet.images?.logo ?: pokemonTcgIoSet.images?.symbol, // Logos von PokemonTCG.io
                    cardCountOfficial = pokemonTcgIoSet.printedTotal,
                    cardCountTotal = pokemonTcgIoSet.total,
                    releaseDate = releaseDate
                )
            }
        } catch (e: Exception) {
            println("Fehler beim Abrufen der Sets von PokemonTCG.io: ${e.message}")
            emptyList()
        }
    }

    /**
     * Diese Methode ist in dieser Implementierung nicht vorgesehen und wirft eine Exception.
     * Die Kartendetails werden von TcgDexApiServiceImpl gehandhabt.
     */
    override suspend fun getCardDetails(setId: String, localId: String, languageCode: String): TcgDexCardResponse? {
        throw UnsupportedOperationException("getCardDetails is not supported in PokemonTcgIoApiServiceImpl. Use TcgDexApiServiceImpl instead.")
    }


}