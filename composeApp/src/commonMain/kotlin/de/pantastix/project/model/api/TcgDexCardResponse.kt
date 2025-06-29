package de.pantastix.project.model.api

import kotlinx.serialization.Serializable

/**
 * Dieses Datenmodell repräsentiert die komplette JSON-Antwort für eine einzelne Karte von der TCGdex API.
 */
@Serializable
data class TcgDexCardResponse(
    val id: String,
    val localId: String,
    var name: String,
    val image: String? = null,
    val category: String,
    val rarity: String?,
    val set: TcgDexSet,
    val illustrator: String? = null,
    val variants: TcgDexVariants? = null,
    val hp: Int? = null,
    val types: List<String>? = emptyList(),
    val stage: String? = null,
    val abilities: List<TcgDexAbility>? = emptyList(),
    val attacks: List<TcgDexAttack>? = emptyList(),
    val retreat: Int? = null,
    val effect: String? = null,
    val trainerType: String? = null,
    val regulationMark: String? = null,
    val legal: TcgDexLegal? = null
)

@Serializable
data class TcgDexVariants(
    val normal: Boolean?,
    val reverse: Boolean?,
    val holo: Boolean?,
    val firstEdition: Boolean?
)

@Serializable
data class TcgDexAbility(
    val name: String,
    val type: String,
    val effect: String
)

@Serializable
data class TcgDexAttack(
    val cost: List<String>? = emptyList(),
    val name: String,
    val effect: String? = null,
    val damage: String? = null
)

@Serializable
data class TcgDexLegal(
    val standard: Boolean?,
    val expanded: Boolean?
)