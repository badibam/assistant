package com.assistant.core.versioning

import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.assistant.core.database.AppDatabase
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.tools.ToolTypeContract
import org.json.JSONArray
import org.json.JSONObject

/**
 * Orchestrateur central des migrations de base de données
 * Collecte les migrations via discovery pattern et les exécute dans l'ordre
 */
class MigrationOrchestrator(private val context: Context) {
    
    /**
     * Récupère toutes les migrations disponibles (core + outils)
     */
    fun getAllMigrations(): Array<Migration> {
        val migrations = mutableListOf<Migration>()
        
        // Migrations core en premier
        migrations.addAll(getCoreMigrations())
        
        // Migrations des outils découverts par ordre alphabétique pour cohérence
        try {
            val allToolTypes = ToolTypeManager.getAllToolTypes().values
            allToolTypes.sortedBy { it.getDisplayName() }.forEach { toolType ->
                try {
                    val toolMigrations = toolType.getDatabaseMigrations()
                    migrations.addAll(toolMigrations)
                } catch (e: Exception) {
                    // Log l'erreur mais continue avec les autres outils
                    println("Erreur lors de la récupération des migrations pour ${toolType.getDisplayName()}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            println("Erreur lors du discovery des migrations des outils: ${e.message}")
        }
        
        // Tri final par version de départ pour assurer l'ordre
        return migrations
            .sortedBy { it.startVersion }
            .toTypedArray()
    }
    
    /**
     * Migrations du système core (zones, tool instances, etc.)
     */
    private fun getCoreMigrations(): List<Migration> {
        return listOf(
            CORE_MIGRATION_1_2
        )
    }
    
    companion object {
        /**
         * Migration 1→2: Ajout table tool_data unifiée
         */
        val CORE_MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Création table tool_data unifiée
                database.execSQL("""
                    CREATE TABLE tool_data (
                        id TEXT PRIMARY KEY NOT NULL,
                        tool_instance_id TEXT NOT NULL,
                        tooltype TEXT NOT NULL,
                        data_version INTEGER NOT NULL DEFAULT 1,
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
                database.execSQL("CREATE INDEX idx_tool_data_tooltype_version ON tool_data(tooltype, data_version)")
                
                // Migration des données tracking_data vers tool_data si la table existe
                try {
                    database.execSQL("""
                        INSERT INTO tool_data (id, tool_instance_id, tooltype, data_version, timestamp, name, data, created_at, updated_at)
                        SELECT 
                            id,
                            tool_instance_id,
                            'tracking' as tooltype,
                            1 as data_version,
                            recorded_at as timestamp,
                            name,
                            value as data,
                            created_at,
                            updated_at
                        FROM tracking_data
                    """.trimIndent())
                    
                    // Supprimer l'ancienne table après migration réussie
                    database.execSQL("DROP TABLE tracking_data")
                    
                } catch (e: Exception) {
                    // Table tracking_data n'existe pas ou migration échoue
                    // Ne pas bloquer la migration, juste logger
                    println("Migration tracking_data ignorée: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Exécute toutes les migrations nécessaires
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
                        error = e.message ?: "Erreur inconnue",
                        suggestedAction = "Vérifier la cohérence des données ou contacter le support"
                    )
                    errors.add(error)
                    
                    // Arrêter en cas d'erreur critique
                    break
                }
            }
            
        } catch (e: Exception) {
            errors.add(MigrationError(
                toolType = "system",
                operation = "Discovery des migrations",
                error = e.message ?: "Erreur inconnue",
                suggestedAction = "Redémarrer l'application"
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
     * Détermine le type d'outil responsable d'une migration (pour debugging)
     */
    private fun getToolTypeForMigration(migration: Migration): String {
        // Logique simple : si la migration vient d'un tool type, on peut le déduire
        // Pour l'instant, on retourne "unknown"
        return "unknown"
    }
    
    /**
     * Génère un rapport des migrations disponibles
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

    /**
     * Effectue les migrations de données au démarrage de l'application
     * Appelé après discovery des tooltypes pour migrer les données obsolètes
     */
    suspend fun performStartupMigrations(context: Context): DataMigrationResult {
        val errors = mutableListOf<MigrationError>()
        val migratedTooltypes = mutableListOf<String>()
        
        try {
            // 1. Discovery pur des tooltypes
            val allToolTypes = ToolTypeManager.getAllToolTypes()
            
            // 2. Accès à la base de données
            val database = AppDatabase.getDatabase(context)
            val dao = database.baseToolDataDao()
            
            // 3. Scan versions data en DB
            val dataVersions = dao.getTooltypeMinVersions()
            
            // 4. Migration autonome par tooltype
            dataVersions.forEach { (tooltype, minVersion) ->
                val toolTypeContract = allToolTypes[tooltype]
                
                if (toolTypeContract is ToolTypeContract) {
                    val currentVersion = toolTypeContract.getCurrentDataVersion()
                    
                    if (minVersion < currentVersion) {
                        try {
                            migrateTooltypeData(tooltype, toolTypeContract, dao, context)
                            migratedTooltypes.add("$tooltype: v$minVersion → v$currentVersion")
                        } catch (e: Exception) {
                            errors.add(MigrationError(
                                toolType = tooltype,
                                operation = "Migration données v$minVersion → v$currentVersion",
                                error = e.message ?: "Erreur inconnue",
                                suggestedAction = "Vérifier intégrité des données ou contacter support"
                            ))
                        }
                    }
                } else {
                    // ToolType ne supporte pas les migrations de données
                    // C'est OK, pas d'erreur
                }
            }
            
        } catch (e: Exception) {
            errors.add(MigrationError(
                toolType = "system",
                operation = "Migration startup des données",
                error = e.message ?: "Erreur inconnue",
                suggestedAction = "Redémarrer l'application ou contacter support"
            ))
        }
        
        return DataMigrationResult(
            success = errors.isEmpty(),
            errors = errors,
            migratedTooltypes = migratedTooltypes
        )
    }
    
    /**
     * Migre les données d'un tooltype spécifique
     */
    private suspend fun migrateTooltypeData(
        tooltype: String,
        toolTypeContract: ToolTypeContract,
        dao: com.assistant.core.database.dao.BaseToolDataDao,
        context: Context
    ) {
        val entries = dao.getByTooltype(tooltype)
        val currentVersion = toolTypeContract.getCurrentDataVersion()
        
        entries.forEach { entry ->
            if (entry.dataVersion < currentVersion) {
                // Upgrade des données
                val upgradedData = toolTypeContract.upgradeDataIfNeeded(entry.data, entry.dataVersion)
                
                // Mise à jour en base
                val updatedEntity = entry.copy(
                    data = upgradedData,
                    dataVersion = currentVersion,
                    updatedAt = System.currentTimeMillis()
                )
                
                dao.update(updatedEntity)
            }
        }
    }
}

/**
 * Résultat d'une migration
 */
data class MigrationResult(
    val success: Boolean,
    val errors: List<MigrationError>,
    val completedMigrations: List<String>,
    val oldVersion: Int,
    val newVersion: Int
)

/**
 * Résultat de migration des données au démarrage
 */
data class DataMigrationResult(
    val success: Boolean,
    val errors: List<MigrationError>,
    val migratedTooltypes: List<String>
)

/**
 * Erreur de migration avec informations de debugging
 */
data class MigrationError(
    val toolType: String,
    val operation: String,
    val error: String,
    val suggestedAction: String
) {
    fun toUserFriendlyMessage(): String {
        return "Erreur dans $toolType lors de '$operation': $error\n" +
               "Action suggérée: $suggestedAction"
    }
}