package de.pantastix.project.model.api

import kotlinx.serialization.Serializable

/**
 * Repräsentiert die gesamte Antwort von der PokemonTCG.io Sets API.
 */
@Serializable
data class PokemonTcgIoSetResponse(
    val data: List<PokemonTcgIoSet>,
    val page: Int? = null, // Optional, da nicht immer benötigt
    val pageSize: Int? = null, // Optional
    val count: Int? = null, // Optional
    val totalCount: Int? = null // Optional
)

/**
 * Repräsentiert ein einzelnes Set-Objekt von der PokemonTCG.io API.
 */
@Serializable
data class PokemonTcgIoSet(
    val id: String,
    val name: String,
    val series: String,
    val printedTotal: Int,
    val total: Int,
    val legalities: Map<String, String>? = null, // Kann null sein
    val ptcgoCode: String? = null, // Kann null sein
    val releaseDate: String,
    val updatedAt: String,
    val images: PokemonTcgIoSetImages? = null // Kann null sein
)

/**
 * Repräsentiert die Bild-URLs für ein PokemonTCG.io Set.
 */
@Serializable
data class PokemonTcgIoSetImages(
    val symbol: String? = null, // Kann null sein
    val logo: String? = null // Kann null sein
)