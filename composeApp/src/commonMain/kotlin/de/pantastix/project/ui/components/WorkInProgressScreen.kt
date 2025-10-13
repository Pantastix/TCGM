package de.pantastix.project.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.pantastix.project.shared.resources.MR
import dev.icerock.moko.resources.compose.stringResource

/**
 * Zeigt einen Platzhalter-Screen für eine Funktion an, die sich noch in Entwicklung befindet.
 */
@Composable
fun WorkInProgressScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
//            .background(Color.LightGray.copy(alpha = 0.3f)) // Leichter Hintergrund
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Ein passendes Emoji oder eine Grafik
        Text(
            text = "🚧",
            style = MaterialTheme.typography.displayLarge,
            fontSize = MaterialTheme.typography.displayLarge.fontSize * 1.5 // Emoji größer machen
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Die Hauptüberschrift
        Text(
            text = "Coming Soon!",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Eine kurze Beschreibung
        Text(
            text = stringResource(MR.strings.wip_description),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}