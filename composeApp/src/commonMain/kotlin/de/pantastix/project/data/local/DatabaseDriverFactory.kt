package de.pantastix.project.data.local

import app.cash.sqldelight.db.SqlDriver

expect class DatabaseDriverFactory {
    fun createDriver(databaseName: String): SqlDriver
}