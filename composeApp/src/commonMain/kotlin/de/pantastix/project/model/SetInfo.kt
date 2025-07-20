package de.pantastix.project.model

import kotlinx.serialization.Serializable

@Serializable
data class SetInfo(
    val setId: String,
    val tcgIoSetId: String?,
    val abbreviation: String?,
    val nameLocal: String,
    val nameEn: String,
    val logoUrl: String?,
    val cardCountOfficial: Int,
    val cardCountTotal: Int,
    val releaseDate: String?
)