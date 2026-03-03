package de.pantastix.project.ai.migration

import io.github.jan.supabase.postgrest.Postgrest

interface MigrationManager {
    suspend fun migrateToLatest(postgrest: Postgrest)
    suspend fun getCurrentVersion(postgrest: Postgrest): Int
}
