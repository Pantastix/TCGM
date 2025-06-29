package de.pantastix.project.model

import kotlinx.serialization.Serializable

@Serializable
data class Ability(
    val name: String,
    val type: String,
    val effect: String
)

@Serializable
data class Attack(
    val cost: List<String>? = emptyList(),
    val name: String,
    val effect: String? = null,
    val damage: String? = null
)

// Das ist unser Haupt-Datenmodell für die Detailansicht einer Karte.
data class PokemonCard(
    val id: Long, // Die ID aus unserer lokalen Sammlungs-DB
    val tcgDexCardId: String,
    val nameLocal: String,
    val nameEn: String,
    val language: String,
    val imageUrl: String?,
    var cardMarketLink: String?,
    var ownedCopies: Int,
    var notes: String?,

    // Zugehörige Set-Informationen
    val setName: String,
    val localId: String, // z.B. "051 / 244"

    // Preisinformationen
    var currentPrice: Double?,
    var lastPriceUpdate: String?,

    // Detail-Informationen
    val rarity: String?,
    val hp: Int?,
    val types: List<String>,
    val illustrator: String?,
    val stage: String?,
    val retreatCost: Int?,
    val regulationMark: String?,

    // Komplexe Daten als Objekte
    val abilities: List<Ability>,
    val attacks: List<Attack>
)