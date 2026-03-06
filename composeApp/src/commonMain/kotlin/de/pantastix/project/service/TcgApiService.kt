package de.pantastix.project.service

import de.pantastix.project.model.SetInfo
import de.pantastix.project.model.api.TcgDexCardResponse

/**
 * Das Interface (der "Vertrag") für unseren API-Service.
 * Es legt fest, welche Operationen möglich sind, ohne die Implementierungsdetails preiszugeben.
 */
interface TcgApiService {

    /**
     * Liefert den Namen des API-Services.
     * Dies kann nützlich sein, um zu unterscheiden, welcher Service verwendet wird.
     * @return Der Name des API-Services.
     */
    fun getName(): String

    /**
     * Ruft alle Sets von der TCGdex API ab und kombiniert die deutschen und englischen Namen.
     * @return Eine Liste von [SetInfo]-Objekten.
     */
    suspend fun getAllSets(language: String): List<SetInfo>

    /**
     * Ruft die Details eines einzelnen Sets ab.
     * @param setId Die offizielle Set-ID (z.B. "sv10").
     * @param language Der Sprachcode für die gewünschte Sprache (z.B. "de" für Deutsch).
     * @return Ein [SetInfo]-Objekt oder null bei einem Fehler.
     */
//    suspend fun  getSingleSet(setId: String, language: String): SetInfo?

    /**
     * Ruft die deutschen Kartendetails für eine spezifische Karte ab.
     * @param setId Die offizielle Set-ID (z.B. "sv10").
     * @param localId Die Nummer der Karte im Set (z.B. "051").
     * @return Ein [TcgDexCardResponse]-Objekt oder null bei einem Fehler.
     */
    suspend fun getCardDetails(setId: String, localId: String, language: String): TcgDexCardResponse?

    suspend fun getSetCards(setId: String): List<TcgDexCardResponse>

    suspend fun getSetDetails(setId: String, language: String): de.pantastix.project.model.api.TcgDexSet?

    /**
     * Sucht nach Karten basierend auf ihrem Namen über die API.
     * @param name Der Name der Karte (z.B. "Gengar").
     * @param language Der Sprachcode.
     * @param setId Optional: Das Set, in dem gesucht werden soll.
     * @return Eine Liste von Suchergebnissen.
     */
    suspend fun searchCardsByName(name: String, language: String, setId: String? = null): List<de.pantastix.project.model.api.TcgDexCardSearchResult>
    }