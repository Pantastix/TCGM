package de.pantastix.project.platform

/**
 * Erwartet eine plattformspezifische Implementierung,
 * um den primären Sprachcode der aktuellen Systemsprache zu erhalten (z.B. "de", "en").
 */
expect fun getSystemLanguage(): String