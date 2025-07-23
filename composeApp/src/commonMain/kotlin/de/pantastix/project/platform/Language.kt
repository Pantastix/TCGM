package de.pantastix.project.platform

import dev.icerock.moko.resources.desc.StringDesc

/**
 * Erwartet eine plattformspezifische Implementierung,
 * um den primären Sprachcode der aktuellen Systemsprache zu erhalten (z.B. "de", "en").
 */
expect fun getSystemLanguage(): String

/**
 * Setzt die Sprache der Anwendung global für moko-resources.
 * Dies ermöglicht eine dynamische Sprachumschaltung innerhalb der App.
 *
 * @param languageCode Der ISO 639-1 Code der zu setzenden Sprache (z.B. "en", "de").
 */
fun setAppLanguage(languageCode: String) {
    // Setzt den 'localeType' global. Alle stringResource-Aufrufe
    // werden ab jetzt diese Einstellung verwenden.
    StringDesc.localeType = StringDesc.LocaleType.Custom(languageCode)
    println("Moko Resources Sprache global auf '$languageCode' gesetzt.")
}