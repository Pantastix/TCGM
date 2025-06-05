package de.pantastix.project.model

import app.cash.sqldelight.db.SqlDriver

expect class DatabaseDriverFactory {
    fun createDriver(databaseName: String): SqlDriver
}