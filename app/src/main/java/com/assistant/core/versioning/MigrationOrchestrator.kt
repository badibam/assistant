package com.assistant.core.versioning

import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.assistant.core.tools.ToolTypeManager
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
            // Exemple de migration core future
            // CORE_MIGRATION_1_2
        )
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