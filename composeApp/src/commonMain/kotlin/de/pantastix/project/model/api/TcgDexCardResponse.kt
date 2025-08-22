package de.pantastix.project.model.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient


@Serializable
data class TcgDexCardSearchResult(
    val id: String,
    val name: String
)

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
    val legal: TcgDexLegal? = null,
    val pricing: TcgDexPricing? = null,

    @Transient
    var cardmarketVersion: Int? = null,
    @Transient
    var totalCardmarketVersions: Int? = null
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
    val name: String? = null,
    val effect: String? = null,
    val damage: String? = null
)

@Serializable
data class TcgDexLegal(
    val standard: Boolean?,
    val expanded: Boolean?
)

@Serializable
data class TcgDexPricing(
    val cardmarket: TcgDexCardmarket? = null,
    val tcgplayer: TcgPlayer? = null
)

@Serializable
data class TcgDexCardmarket(
    val updated: String? = null,
    val unit: String? = null,
    val avg: Double? = null,
    val low: Double? = null,
    val trend: Double? = null,
    val avg1: Double? = null,
    val avg7: Double? = null,
    val avg30: Double? = null,
    val `avg-holo`: Double? = null,
    val `low-holo`: Double? = null,
    val `trend-holo`: Double? = null,
    val `avg1-holo`: Double? = null,
    val `avg7-holo`: Double? = null,
    val `avg30-holo`: Double? = null
)

@Serializable
data class TcgPlayer(
    val updated: String? = null,
    val unit: String? = null,
    val holofoil: TcgPlayerHolofoil? = null,
    val normal: TcgPlayerHolofoil? = null
)

@Serializable
data class TcgPlayerHolofoil(
    val lowPrice: Double? = null,
    val midPrice: Double? = null,
    val highPrice: Double? = null,
    val marketPrice: Double? = null,
    val directLowPrice: Double? = null
)