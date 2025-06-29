package de.pantastix.project.service

import de.pantastix.project.model.SetInfo
import de.pantastix.project.model.api.TcgDexCardResponse

/**
 * Das Interface (der "Vertrag") für unseren API-Service.
 * Es legt fest, welche Operationen möglich sind, ohne die Implementierungsdetails preiszugeben.
 */
interface TcgDexApiService {
    /**
     * Ruft alle Sets von der TCGdex API ab und kombiniert die deutschen und englischen Namen.
     * @return Eine Liste von [SetInfo]-Objekten.
     */
    suspend fun getAllSets(language: String): List<SetInfo>

    /**
     * Ruft die deutschen Kartendetails für eine spezifische Karte ab.
     * @param setId Die offizielle Set-ID (z.B. "sv10").
     * @param localId Die Nummer der Karte im Set (z.B. "051").
     * @return Ein [TcgDexCardResponse]-Objekt oder null bei einem Fehler.
     */
    suspend fun getCardDetails(setId: String, localId: String, languageCode: String): TcgDexCardResponse?
}