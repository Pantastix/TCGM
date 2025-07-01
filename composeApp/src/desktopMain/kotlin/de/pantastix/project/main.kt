// composeApp/src/desktopMain/kotlin/de/pantastix/project/Main.kt
package de.pantastix.project

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.Window
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import de.pantastix.project.data.local.DatabaseDriverFactory
import de.pantastix.project.di.commonModule
import de.pantastix.project.ui.App
import de.pantastix.project.ui.theme.AppTheme
import org.koin.core.context.startKoin
import org.koin.dsl.module

val desktopModule = module {
    single<DatabaseDriverFactory> { DatabaseDriverFactory() }
}

fun main() = application {

    initKoin()

    val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)


    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        undecorated = true, // <<< ENTFERNT die Standard-Windows/macOS-Titelzeile
        title = "Trading Card Game Manager",
        transparent = true
    ) {
        AppTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 8.dp
            ) {
                AppWindowTitleBar(
                    title = "Trading Card Game Manager",
                    window = this.window, // Übergibt das Fenster-Objekt für Minimieren/Maximieren
                    onClose = { exitApplication() }
                ) {
                    App() // Hier wird deine Multiplattform-App geladen
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WindowScope.AppWindowTitleBar(
    title: String,
    window: ComposeWindow,
    onClose: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            WindowDraggableArea {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.weight(1f))

                        // Fenster-Buttons
                        IconButton(onClick = { window.isMinimized = true }) {
                            Icon(Icons.Default.Minimize, "Minimieren", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = {
                            if (window.placement == WindowPlacement.Maximized) {
                                window.placement = WindowPlacement.Floating
                            } else {
                                window.placement = WindowPlacement.Maximized
                            }
                        }) {
                            Icon(Icons.Default.Window, "Maximieren", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, "Schließen", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 4.dp,
                color = MaterialTheme.colorScheme.primary
            )

            // Der eigentliche App-Inhalt wird unter der Titelzeile platziert
            content()
        }
    }
}

fun initKoin() {
    startKoin {
        modules(commonModule, desktopModule)
    }
}