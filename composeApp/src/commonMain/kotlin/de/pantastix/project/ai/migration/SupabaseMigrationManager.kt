package de.pantastix.project.ai.migration

import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.serialization.json.*
import kotlinx.coroutines.delay

class SupabaseMigrationManager : MigrationManager {

    private val TARGET_VERSION = 2

    override suspend fun getCurrentVersion(postgrest: Postgrest): Int {
        return try {
            val response = postgrest.from("schema_version").select().decodeSingleOrNull<JsonObject>()
            response?.get("version")?.jsonPrimitive?.int ?: 0
        } catch (e: Exception) {
            println("SupabaseMigration: Could not fetch version, assuming 0: ${e.message}")
            0
        }
    }

    override suspend fun migrateToLatest(postgrest: Postgrest) {
        var current = getCurrentVersion(postgrest)
        println("SupabaseMigration: Current version is $current, target is $TARGET_VERSION")
        
        while (current < TARGET_VERSION) {
            val nextVersion = current + 1
            println("SupabaseMigration: Migrating to version $nextVersion...")
            
            val sql = getMigrationSql(nextVersion)
            if (sql.isNotBlank()) {
                try {
                    // Using the existing execute_migration RPC from SupabaseGuideScreen.kt
                    postgrest.rpc("execute_migration", buildJsonObject {
                        put("sql_command", sql)
                    })
                    
                    // Update version in DB
                    if (nextVersion == 1) {
                         postgrest.from("schema_version").upsert(buildJsonObject {
                             put("id", 1)
                             put("version", nextVersion)
                         })
                    } else {
                         postgrest.from("schema_version").update({
                             set("version", nextVersion)
                         }) {
                             filter { eq("id", 1) }
                         }
                    }
                    current = nextVersion
                    println("SupabaseMigration: Successfully migrated to $current")
                } catch (e: Exception) {
                    println("SupabaseMigration: Error migrating to $nextVersion: ${e.message}")
                    throw e
                }
            } else {
                current = nextVersion
            }
        }
    }

    private fun getMigrationSql(version: Int): String {
        return when (version) {
            1 -> {
                """
                CREATE TABLE IF NOT EXISTS public.schema_version (
                    id INT PRIMARY KEY DEFAULT 1,
                    version INT NOT NULL,
                    CONSTRAINT one_row CHECK (id = 1)
                );
                INSERT INTO public.schema_version (id, version) VALUES (1, 1) ON CONFLICT (id) DO NOTHING;
                """.trimIndent()
            }
            2 -> {
                """
                CREATE TABLE IF NOT EXISTS public."TypeReference" (
                    "id" TEXT PRIMARY KEY,
                    "name_de" TEXT NOT NULL,
                    "name_en" TEXT NOT NULL,
                    "name_fr" TEXT,
                    "name_es" TEXT,
                    "name_it" TEXT,
                    "name_pt" TEXT,
                    "name_jp" TEXT
                );

                INSERT INTO public."TypeReference" (id, name_de, name_en, name_fr, name_jp) VALUES 
                ('water', 'Wasser', 'Water', 'Eau', '水'),
                ('fire', 'Feuer', 'Fire', 'Feu', '炎'),
                ('grass', 'Pflanze', 'Grass', 'Plante', '草'),
                ('lightning', 'Elektro', 'Lightning', 'Électrique', '雷'),
                ('psychic', 'Psycho', 'Psychic', 'Psy', '超'),
                ('fighting', 'Kampf', 'Fighting', 'Lucha', '闘'),
                ('darkness', 'Unlicht', 'Darkness', 'Obscurité', '悪'),
                ('metal', 'Metall', 'Metal', 'Métal', '鋼'),
                ('dragon', 'Drache', 'Dragon', 'Dragon', '竜'),
                ('colorless', 'Farblos', 'Colorless', 'Incolore', '無'),
                ('fairy', 'Fee', 'Fairy', 'Fée', 'フェアリー')
                ON CONFLICT (id) DO NOTHING;
                """.trimIndent()
            }
            else -> ""
        }
    }
}
