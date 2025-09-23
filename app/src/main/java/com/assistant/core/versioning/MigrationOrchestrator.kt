package com.assistant.core.versioning

import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.assistant.core.database.AppDatabase
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.tools.ToolTypeContract
import com.assistant.core.utils.LogManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * Central database migration orchestrator
 * Collects migrations via discovery pattern and executes them in order
 */
class MigrationOrchestrator(private val context: Context) {
    
    /**
     * Gets all available migrations (core + tools)
     */
    fun getAllMigrations(): Array<Migration> {
        val migrations = mutableListOf<Migration>()
        
        // Core migrations first
        migrations.addAll(getCoreMigrations())
        
        // Tool migrations no longer supported - all migrations are core migrations only
        
        // Final sort by start version to ensure order
        return migrations
            .sortedBy { it.startVersion }
            .toTypedArray()
    }
    
    /**
     * Core system migrations (zones, tool instances, etc.)
     */
    private fun getCoreMigrations(): List<Migration> {
        return listOf(
            // Migration 1→2 removed - unified architecture starts in v3
            CORE_MIGRATION_2_3,
            CORE_MIGRATION_3_4
        )
    }
    
    companion object {
        /**
         * Migration 1→2: Add unified tool_data table
         */
        val CORE_MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create unified tool_data table
                database.execSQL("""
                    CREATE TABLE tool_data (
                        id TEXT PRIMARY KEY NOT NULL,
                        tool_instance_id TEXT NOT NULL,
                        tooltype TEXT NOT NULL,
                        timestamp INTEGER,
                        name TEXT,
                        data TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        FOREIGN KEY (tool_instance_id) REFERENCES tool_instances(id) ON DELETE CASCADE
                    )
                """.trimIndent())

                // Index pour performances
                database.execSQL("CREATE INDEX idx_tool_data_instance ON tool_data(tool_instance_id)")
                database.execSQL("CREATE INDEX idx_tool_data_timestamp ON tool_data(timestamp)")
                database.execSQL("CREATE INDEX idx_tool_data_tooltype ON tool_data(tooltype)")
                database.execSQL("CREATE INDEX idx_tool_data_instance_timestamp ON tool_data(tool_instance_id, timestamp)")
                
                // Migration removed - complete wipe for unified architecture
                // Previous data will be lost (can be recovered by external script if needed)
            }
        }
        
        /**
         * Migration 2→3: Transition to clean unified architecture
         * No data migration - clean start
         */
        val CORE_MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Empty migration - tool_data table already exists in v2
                // Unified architecture already in place
            }
        }

        /**
         * Migration 3→4: Add enabled/active fields
         * Add active field to zones and enabled field to tool_instances
         */
        val CORE_MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add active field to zones table with default value true
                database.execSQL("ALTER TABLE zones ADD COLUMN active INTEGER NOT NULL DEFAULT 1")

                // Add enabled field to tool_instances table with default value true
                database.execSQL("ALTER TABLE tool_instances ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1")
            }
        }
    }
    
    /**
     * Executes all necessary migrations
     */
    fun performMigrations(database: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int): MigrationResult {
        val errors = mutableListOf<MigrationError>()
        val completedMigrations = mutableListOf<String>()
        
        try {
            val migrations = getAllMigrations()
                .filter { it.startVersion >= oldVersion && it.endVersion <= newVersion }
            
            for (migration in migrations) {
                try {
                    migration.migrate(database)
                    completedMigrations.add("${migration.startVersion} → ${migration.endVersion}")
                } catch (e: Exception) {
                    val error = MigrationError(
                        toolType = getToolTypeForMigration(migration),
                        operation = "Migration ${migration.startVersion} → ${migration.endVersion}",
                        error = e.message ?: "Unknown error",
                        suggestedAction = "Check data consistency or contact support"
                    )
                    errors.add(error)
                    
                    // Stop on critical error
                    break
                }
            }
            
        } catch (e: Exception) {
            errors.add(MigrationError(
                toolType = "system",
                operation = "Migration discovery",
                error = e.message ?: "Unknown error",
                suggestedAction = "Restart the application"
            ))
        }
        
        return MigrationResult(
            success = errors.isEmpty(),
            errors = errors,
            completedMigrations = completedMigrations,
            oldVersion = oldVersion,
            newVersion = newVersion
        )
    }
    
    /**
     * Determines the tool type responsible for a migration (for debugging)
     */
    private fun getToolTypeForMigration(migration: Migration): String {
        // Simple logic: if migration comes from a tool type, we can deduce it
        // For now, return "unknown"
        return "unknown"
    }
    
    /**
     * Generates a report of available migrations
     */
    fun getMigrationReport(): String {
        val report = JSONObject()
        val migrationsArray = JSONArray()
        
        try {
            getAllMigrations().forEach { migration ->
                migrationsArray.put(JSONObject().apply {
                    put("from", migration.startVersion)
                    put("to", migration.endVersion)
                    put("source", getToolTypeForMigration(migration))
                })
            }
            
            report.put("available_migrations", migrationsArray)
            report.put("total_count", migrationsArray.length())
            
        } catch (e: Exception) {
            report.put("error", e.message)
        }
        
        return report.toString(2)
    }

}

/**
 * Migration result
 */
data class MigrationResult(
    val success: Boolean,
    val errors: List<MigrationError>,
    val completedMigrations: List<String>,
    val oldVersion: Int,
    val newVersion: Int
)


/**
 * Migration error with debugging information
 */
data class MigrationError(
    val toolType: String,
    val operation: String,
    val error: String,
    val suggestedAction: String
) {
    fun toUserFriendlyMessage(): String {
        return "Error in $toolType during '$operation': $error\n" +
               "Suggested action: $suggestedAction"
    }
}