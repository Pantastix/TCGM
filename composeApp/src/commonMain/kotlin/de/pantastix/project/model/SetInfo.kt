package de.pantastix.project.model

import kotlinx.serialization.Serializable

@Serializable
data class SetInfo(
    val id: Int,
    val setId: String,
    val abbreviation: String?,
    val nameLocal: String,
    val nameEn: String,
    val logoUrl: String?,
    val cardCountOfficial: Int,
    val cardCountTotal: Int,
    val releaseDate: String?
)