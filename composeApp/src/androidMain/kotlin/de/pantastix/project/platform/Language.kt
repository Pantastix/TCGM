package de.pantastix.project.platform

import java.util.Locale

/**
 * Tatsächliche Implementierung für Android.
 * Liest die Standardsprache des Geräts aus.
 */
actual fun getSystemLanguage(): String {
    return Locale.getDefault().language
}