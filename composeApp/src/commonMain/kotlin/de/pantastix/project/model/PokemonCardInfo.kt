package de.pantastix.project.model

import kotlinx.serialization.Serializable

// Enthält nur die wichtigsten Infos für die Listenansicht
@Serializable
data class PokemonCardInfo(
    val id: Long, // Die ID aus unserer Sammlungs-DB
    val tcgDexCardId: String,
    val language: String,
    val nameLocal: String,
    val setName: String,
    val imageUrl: String?,
    val ownedCopies: Int,
    val currentPrice: Double?
)