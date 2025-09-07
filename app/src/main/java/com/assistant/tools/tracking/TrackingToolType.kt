package com.assistant.tools.tracking

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.assistant.core.tools.ToolTypeContract
import com.assistant.core.tools.BaseSchemas
import com.assistant.core.services.ExecutableService
import com.assistant.core.validation.ValidationResult
import com.assistant.core.utils.NumberFormatting
import com.assistant.core.database.entities.ToolDataEntity
import com.assistant.tools.tracking.handlers.TrackingTypeFactory
import com.assistant.tools.tracking.TrackingSchemas
import com.assistant.tools.tracking.ui.TrackingConfigScreen
import com.assistant.tools.tracking.ui.TrackingScreen
import org.json.JSONObject

/**
 * Tracking Tool Type implementation
 * Provides static metadata for tracking tool instances
 */
object TrackingToolType : ToolTypeContract {
    
    override fun getDisplayName(): String = "Suivi"
    
    override fun getDefaultConfig(): String {
        return """
        {
            "name": "",
            "description": "",
            "management": "",
            "config_validation": "",
            "data_validation": "",
            "display_mode": "",
            "icon_name": "activity",
            "type": "numeric",
            "items": []
        }
        """.trimIndent()
    }
    
    override fun getConfigSchema(): String {
        // Combine base schema with tracking-specific schema from external object
        return BaseSchemas.createExtendedSchema(
            BaseSchemas.getBaseConfigSchema(),
            TrackingSchemas.CONFIG_SCHEMA
        )
    }
    
    override fun getDataSchema(): String? {
        // Combine base schema with tracking-specific schema from external object
        return BaseSchemas.createExtendedSchema(
            BaseSchemas.getBaseDataSchema(),
            TrackingSchemas.DATA_SCHEMA
        )
    }
    
    
    override fun getAvailableOperations(): List<String> {
        return listOf(
            "add_entry", "get_entries", "update_entry", "delete_entry", "delete_all_entries",
            "start_activity", "stop_activity", "stop_all"
        )
    }
    
    override fun getDefaultIconName(): String {
        return "activity"
    }
    
    @Composable
    override fun getConfigScreen(
        zoneId: String,
        onSave: (config: String) -> Unit,
        onCancel: () -> Unit,
        existingToolId: String?,
        onDelete: (() -> Unit)?
    ) {
        TrackingConfigScreen(
            zoneId = zoneId,
            onSave = onSave,
            onCancel = onCancel,
            existingToolId = existingToolId,
            onDelete = onDelete
        )
    }
    
    override fun getService(context: Context): ExecutableService {
        return com.assistant.core.services.ToolDataService(context)
    }
    
    override fun getDao(context: Context): Any {
        val database = com.assistant.core.database.AppDatabase.getDatabase(context)
        val baseDao = database.toolDataDao()
        
        // Utilise l'implémentation générique qui suffit pour le tracking standard
        return com.assistant.core.database.dao.DefaultExtendedToolDataDao(baseDao, "tracking")
    }
    
    override fun getDatabaseEntities(): List<Class<*>> {
        return listOf(ToolDataEntity::class.java)
    }
    
    @Composable
    override fun getUsageScreen(
        toolInstanceId: String,
        configJson: String,
        zoneName: String,
        onNavigateBack: () -> Unit,
        onLongClick: () -> Unit
    ) {
        TrackingScreen(
            toolInstanceId = toolInstanceId,
            zoneName = zoneName,
            onNavigateBack = onNavigateBack,
            onConfigureClick = onLongClick
        )
    }
    
    override fun validateData(data: Any, operation: String): ValidationResult {
        // Legacy method - validation is now handled by SchemaValidator V3
        // This method is kept for compatibility but all validation is centralized
        return ValidationResult.success()
    }
    
    override fun getDatabaseMigrations(): List<Migration> {
        return emptyList() // Migrations gérées par le core maintenant
    }
    
    override fun migrateConfig(fromVersion: Int, configJson: String): String {
        return when (fromVersion) {
            1 -> {
                // Exemple: Migration config v1 → v2
                // val config = JSONObject(configJson)
                // config.put("new_field", "default_value")
                // config.toString()
                configJson // Pas de migration nécessaire pour l'instant
            }
            else -> configJson // Pas de migration connue
        }
    }
    
    // Migrations d'exemple (pour référence future)
    private object Migrations {
        // Exemple de migration de base de données (pour référence future)
        /*
        val TRACKING_MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Exemple: Ajouter une colonne
                database.execSQL("ALTER TABLE tracking_data ADD COLUMN category TEXT DEFAULT ''")
                
                // Exemple: Migrer des données
                database.execSQL("UPDATE tracking_data SET category = 'health' WHERE tool_instance_name LIKE '%santé%'")
            }
        }
        */
    }
    
    // ═══ Data Migration Capabilities ═══
    override fun getCurrentDataVersion(): Int = 2
    
    override fun upgradeDataIfNeeded(rawData: String, fromVersion: Int): String {
        return when (fromVersion) {
            1 -> upgradeV1ToV2(rawData)
            else -> rawData // Pas de migration nécessaire
        }
    }
    
    /**
     * Migration v1 → v2 : Ajout du champ type si manquant
     */
    private fun upgradeV1ToV2(v1Data: String): String {
        return try {
            val json = JSONObject(v1Data)
            // Ajouter le champ type si absent (v2 requirement)
            if (!json.has("type")) {
                // Inférer le type depuis les données existantes
                val inferredType = when {
                    json.has("quantity") -> "numeric"
                    json.has("items") -> "choice"
                    json.has("duration") -> "timer"
                    json.has("checked") -> "boolean"
                    json.has("level") -> "scale"
                    else -> "text"
                }
                json.put("type", inferredType)
            }
            json.toString()
        } catch (e: Exception) {
            // En cas d'erreur, retourner les données originales
            v1Data
        }
    }
    
    /**
     * Get user-friendly field name for display
     * @param fieldName The technical field name (e.g., "quantity", "name")
     * @return User-friendly field name for display (e.g., "Quantité", "Nom")
     */
    override fun getFormFieldName(fieldName: String): String {
        return when(fieldName) {
            "default_quantity" -> "Qté par défaut"
            "quantity" -> "Quantité"
            "name" -> "Nom" 
            "unit" -> "Unité"
            "text" -> "Texte"
            "rating" -> "Note"
            "min_value" -> "Valeur min"
            "max_value" -> "Valeur max"
            "min_label" -> "Libellé min"
            "max_label" -> "Libellé max"
            "state" -> "État"
            "true_label" -> "Libellé vrai"
            "false_label" -> "Libellé faux"
            "selected_option" -> "Option sélectionnée"
            "available_options" -> "Options disponibles"
            "increment" -> "Incrément"
            "activity" -> "Activité"
            "duration_minutes" -> "Durée"
            else -> "Champ non reconnu"
        }
    }
}