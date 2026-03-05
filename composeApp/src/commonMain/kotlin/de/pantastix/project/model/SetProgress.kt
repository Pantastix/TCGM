package de.pantastix.project.model

import kotlinx.serialization.Serializable

@Serializable
data class SetProgress(
    val setId: String,
    val name: String,
    val logoUrl: String?,
    val cardCountOfficial: Int,
    val releaseDate: String?,
    val ownedUniqueCount: Long,
    val totalPhysicalCount: Long,
    val artRarePlusCount: Long,
    val totalValue: Double
)
