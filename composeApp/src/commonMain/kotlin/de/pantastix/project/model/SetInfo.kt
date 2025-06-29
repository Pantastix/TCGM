package de.pantastix.project.model

data class SetInfo(
    val setId: String,
    val abbreviation: String?,
    val nameLocal: String,
    val nameEn: String,
    val logoUrl: String?,
    val cardCountOfficial: Int,
    val cardCountTotal: Int,
    val releaseDate: String?
)