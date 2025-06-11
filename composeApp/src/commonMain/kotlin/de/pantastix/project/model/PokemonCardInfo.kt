package de.pantastix.project.model

// Enthält nur die wichtigsten Infos für die Listenansicht
data class PokemonCardInfo(
    val id: Long, // Die ID aus unserer Sammlungs-DB
    val nameDe: String,
    val setName: String,
    val imageUrl: String?,
    val ownedCopies: Int,
    val currentPrice: Double?
)