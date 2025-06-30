package de.pantastix.project.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import de.pantastix.project.db.cards.CardDatabase
import de.pantastix.project.db.settings.SettingsDatabase
import java.io.File

actual class DatabaseDriverFactory {
    // Private Hilfsfunktion, um das App-Verzeichnis zu erstellen
    private fun getAppDir(): File {
        val userHome = System.getProperty("user.home")
        val appDir = File(userHome, ".tcgm")
        if (!appDir.exists()) {
            appDir.mkdirs()
        }
        return appDir
    }

    // Die createDriver-Funktion wurde angepasst, um zwei verschiedene DBs zu unterstützen
    actual fun createDriver(databaseName: String): SqlDriver {
        val dbFile = File(getAppDir(), databaseName)
        println("Datenbank-Pfad: ${dbFile.absolutePath}")

        val driver: SqlDriver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")

        try {
            // Wir entscheiden anhand des Namens, welches Schema wir erstellen müssen
            if (databaseName == "cards.db") {
                CardDatabase.Schema.create(driver)
            } else if (databaseName == "settings.db") {
                SettingsDatabase.Schema.create(driver)
            }
        } catch (e: Exception) {
            // Dieser Fehler ist oft normal, wenn die DB schon existiert.
        }

        return driver
    }
}