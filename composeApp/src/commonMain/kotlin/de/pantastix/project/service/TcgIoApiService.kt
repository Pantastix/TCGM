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
    private val renameList = mapOf(
        "—" to "-"
    )

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

    override fun getName(): String {
        return "PokemonTCG.io API Service"
    }

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

                val normalizedName = normalizeSetName(pokemonTcgIoSet.name)

                SetInfo(
                    setId = pokemonTcgIoSet.id,
                    tcgIoSetId = pokemonTcgIoSet.id,
                    abbreviation = pokemonTcgIoSet.ptcgoCode,
                    nameLocal = normalizedName, // Hier wird der englische Name als lokaler Name verwendet
                    nameEn = normalizedName,
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