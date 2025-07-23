package de.pantastix.project.platform

import java.util.Locale

/**
 * Tatsächliche Implementierung für die JVM (Desktop).
 * Liest die Standardsprache des Systems aus.
 */
actual fun getSystemLanguage(): String {
    return Locale.getDefault().language
}