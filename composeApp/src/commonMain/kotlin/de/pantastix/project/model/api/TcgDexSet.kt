package de.pantastix.project.model.api

import kotlinx.serialization.Serializable

/**
 * Dieses Datenmodell ist nur ein Beispiel für die Set-Antwort.
 * Du solltest deine bereits erstellten TcgDexModels.kt hierfür verwenden.
 */
@Serializable
data class TcgDexSet(
    val id: String,
    val name: String,
    val logo: String? = null,
    val symbol: String? = null, // Feld hinzugefügt
    val cardCount: TcgDexCardCount? = null
)

@Serializable
data class TcgDexCardCount(
    val official: Int,
    val total: Int
)
