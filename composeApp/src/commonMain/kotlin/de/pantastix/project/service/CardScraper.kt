package de.pantastix.project.service

data class ScrapedCardData(
    val price30d: Double?,
    val imageUrl: String?
)

interface CardScraper {
    /**
     * Versucht, die Daten von der gegebenen CardMarket-URL zu extrahieren.
     * @param url Die vollständige URL zur Produktseite der Karte.
     * @return Ein [ScrapedCardData]-Objekt bei Erfolg, sonst null.
     */
    suspend fun scrapeCardData(url: String): ScrapedCardData?
}