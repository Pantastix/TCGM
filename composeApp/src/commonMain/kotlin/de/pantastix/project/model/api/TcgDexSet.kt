package de.pantastix.project.model.api

import kotlinx.serialization.Serializable

/**
 * Dieses Datenmodell ist nur ein Beispiel für die Set-Antwort.
 * Du solltest deine bereits erstellten TcgDexModels.kt hierfür verwenden.
 */
@Serializable
data class TcgDexSet(
    val id: String? = null,
    val name: String? = null,
    val logo: String? = null,
    val symbol: String? = null,
    val cardCount: TcgDexCardCount? = null,
    val cards: List<TcgDexCardResponse>? = null,
    val abbreviation: TcgDexSetAbbreviation? = null,
    val releaseDate: String? = null
)

@Serializable
data class TcgDexSetAbbreviation(
    val official: String? = null
)

@Serializable
data class TcgDexCardCount(
    val official: Int,
    val total: Int
)
