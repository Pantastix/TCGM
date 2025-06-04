package de.pantastix.project

data class PokemonCard(
    val id: Long? = null, // Null, wenn noch nicht in DB
    val name: String,
    val setName: String,
    val cardNumber: String,
    val rarity: String, //TODO Enum
    val language: String,
    val condition: String,
    val cardMarketLink: String,
    var currentPrice: Double?,
    var lastPriceUpdate: String?, // oder einen passenderen Datumstyp
    var imagePath: String?, // Lokaler Pfad oder Remote-URL
    var ownedCopies: Int = 0,
)