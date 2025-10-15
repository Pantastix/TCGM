package de.pantastix.project.data.local

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import de.pantastix.project.db.cards.CardDatabase
import de.pantastix.project.db.settings.SettingsDatabase

actual class DatabaseDriverFactory(private val context: Context) {

    // actual fun benötigt KEIN 'actual' Keyword, wenn es sich nicht um eine `expect/actual` Klasse handelt.
    // Wenn du eine `expect class DatabaseDriverFactory` in commonMain hast, behalte das `actual`.
//    actual fun createDriver(databaseName: String): SqlDriver {
//        return AndroidSqliteDriver(
//            CardDatabase.Schema, // Dein Schema-Objekt
//            context,
//            databaseName
//        )
//    }
    actual fun createDriver(databaseName: String): SqlDriver {
        return when (databaseName) {
            "cards.db" -> AndroidSqliteDriver(CardDatabase.Schema, context, databaseName)
            "settings.db" -> AndroidSqliteDriver(SettingsDatabase.Schema, context, databaseName)
            else -> throw IllegalArgumentException("Unbekannter Datenbankname: $databaseName")
        }
    }
}