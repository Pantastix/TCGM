package de.pantastix.project.repository

import de.pantastix.project.coroutines.ioDispatcher
import de.pantastix.project.db.settings.SettingsDatabaseQueries
import kotlinx.coroutines.withContext

class SettingsRepositoryImpl(
    private val queries: SettingsDatabaseQueries
) : SettingsRepository {

    override suspend fun getSetting(key: String): String? {
        return withContext(ioDispatcher) {
            queries.getSetting(key).executeAsOneOrNull()
        }
    }

    override suspend fun saveSetting(key: String, value: String) {
        withContext(ioDispatcher) {
            queries.insertOrReplaceSetting(key, value)
        }
    }
}