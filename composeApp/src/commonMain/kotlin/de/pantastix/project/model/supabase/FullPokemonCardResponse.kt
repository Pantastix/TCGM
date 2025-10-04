package de.pantastix.project.model.supabase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NestedSetEntity(
    val nameLocal: String
)

@Serializable
data class FullPokemonCardResponse(
    val id: Long,
    val setId: String,
    val tcgDexCardId: String,
    val nameLocal: String,
    val nameEn: String,
    val language: String,
    val localId: String,
    val imageUrl: String? = null,
    val cardMarketLink: String? = null,
    val ownedCopies: Int,
    val notes: String? = null,
    val rarity: String? = null,
    val hp: Int? = null,
    val types: String? = null,
    val illustrator: String? = null,
    val stage: String? = null,
    val retreatCost: Int? = null,
    val regulationMark: String? = null,
    val currentPrice: Double? = null,
    val lastPriceUpdate: String? = null,
    val selectedPriceSource: String? = null,
    val variantsJson: String? = null,
    val abilitiesJson: String? = null,
    val attacksJson: String? = null,
    val legalJson: String? = null,
    @SerialName("SetEntity")
    val setEntity: NestedSetEntity? = null
)
