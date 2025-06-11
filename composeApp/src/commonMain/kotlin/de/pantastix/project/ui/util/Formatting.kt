package de.pantastix.project.ui.util

import kotlin.math.roundToInt

fun formatPrice(price: Double): String {
    // Multipliziere mit 100, runde zur nächsten Ganzzahl und teile dann wieder durch 100.0
    // Das ist ein einfacher Weg, um auf zwei Nachkommastellen zu runden, ohne plattformspezifische APIs.
    val rounded = (price * 100).roundToInt() / 100.0
    val parts = rounded.toString().split('.')
    val integerPart = parts[0]
    val decimalPart = parts.getOrNull(1)?.padEnd(2, '0') ?: "00"
    return "$integerPart,$decimalPart €"
}