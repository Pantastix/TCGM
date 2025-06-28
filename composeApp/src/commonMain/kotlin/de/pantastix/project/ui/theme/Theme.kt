package de.pantastix.project.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Wir definieren nur ein Dark-Mode-Farbschema, da die App primär dunkel sein soll.
private val AppDarkColorScheme = darkColorScheme(
    primary = PastelGreen,       // Die Haupt-Akzentfarbe (z.B. für Buttons)
    onPrimary = DarkText,        // Farbe für Text auf der Primärfarbe
    secondary = MediumGray,      // Sekundäre Akzentfarbe
    onSecondary = LightText,     // Farbe für Text auf der Sekundärfarbe
    background = DarkBackground, // Farbe für den App-Hintergrund
    onBackground = LightText,    // Farbe für Text auf dem Hintergrund
    surface = DarkSurface,       // Farbe für Oberflächen wie Karten oder Dialoge
    onSurface = LightText,       // Farbe für Text auf Oberflächen
    error = Color(0xFFCF6679),   // Standard-Rot für Fehlermeldungen
    onError = Color.Black
)

/**
 * Dies ist unser Haupt-Theme-Composable.
 * Wir wickeln unsere gesamte App in diese Funktion, um das Theme anzuwenden.
 */
@Composable
fun AppTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(), // Wir können es später anpassbar machen
    content: @Composable () -> Unit
) {
    val colors = AppDarkColorScheme // Wir verwenden immer unser dunkles Schema

    MaterialTheme(
        colorScheme = colors,
        // Hier könntest du auch die Typografie (Schriftarten, -größen) definieren
        // typography = AppTypography,
        content = content
    )
}