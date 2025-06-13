package de.pantastix.project.repository

interface SettingsRepository {
    suspend fun getSetting(key: String): String?
    suspend fun saveSetting(key: String, value: String)
}