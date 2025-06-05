package de.pantastix.project.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import de.pantastix.project.db.CardDatabase
import java.io.File

actual class DatabaseDriverFactory {
    actual fun createDriver(databaseName: String): SqlDriver {
        val dbFile = File(
            System.getProperty("java.io.tmpdir"),
            databaseName
        ) // Speichert DB im Temp-Ordner, für Produktiv-App ggf. anderen Ort wählen
        val driver: SqlDriver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        // Erstelle das Schema nur, wenn die Datei noch nicht existiert (oder leer ist)
        // Alternativ: Migrations-Handling, wenn du das Schema später änderst.
        if (!dbFile.exists() || dbFile.length() == 0L) {
            try {
                CardDatabase.Schema.create(driver) // Stelle sicher, dass AppDatabase dem Namen in deiner SQLDelight-Konfig entspricht
                println("Database schema created at ${dbFile.absolutePath}")
            } catch (e: Exception) {
                println("Error creating database schema: ${e.message}")
                throw e
            }
        } else {
            println("Database already exists at ${dbFile.absolutePath}")
        }
        return driver
    }
}