package com.assistant.core.versioning

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Gestionnaire de version de l'application et des migrations
 */
class AppVersionManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("app_version", Context.MODE_PRIVATE)
    
    companion object {
        const val CURRENT_APP_VERSION = 1
        const val CURRENT_DATABASE_VERSION = 1
        const val CURRENT_CONFIG_VERSION = 1
        
        private const val PREF_APP_VERSION = "app_version"
        private const val PREF_DATABASE_VERSION = "database_version"
        private const val PREF_CONFIG_VERSION = "config_version"
        private const val PREF_FIRST_LAUNCH = "first_launch"
    }
    
    /**
     * Version actuelle de l'app installée
     */
    fun getCurrentAppVersion(): Int {
        return prefs.getInt(PREF_APP_VERSION, 0)
    }
    
    /**
     * Version actuelle de la base de données
     */
    fun getCurrentDatabaseVersion(): Int {
        return prefs.getInt(PREF_DATABASE_VERSION, 0)
    }
    
    /**
     * Version actuelle des configurations
     */
    fun getCurrentConfigVersion(): Int {
        return prefs.getInt(PREF_CONFIG_VERSION, 0)
    }
    
    /**
     * Première installation de l'app
     */
    fun isFirstLaunch(): Boolean {
        return prefs.getBoolean(PREF_FIRST_LAUNCH, true)
    }
    
    /**
     * Met à jour la version de l'app
     */
    fun updateAppVersion(version: Int) {
        prefs.edit().putInt(PREF_APP_VERSION, version).apply()
    }
    
    /**
     * Met à jour la version de la base de données
     */
    fun updateDatabaseVersion(version: Int) {
        prefs.edit().putInt(PREF_DATABASE_VERSION, version).apply()
    }
    
    /**
     * Met à jour la version des configurations
     */
    fun updateConfigVersion(version: Int) {
        prefs.edit().putInt(PREF_CONFIG_VERSION, version).apply()
    }
    
    /**
     * Marque la première installation comme terminée
     */
    fun markFirstLaunchComplete() {
        prefs.edit().putBoolean(PREF_FIRST_LAUNCH, false).apply()
    }
    
    /**
     * Vérifie si des migrations sont nécessaires
     */
    fun needsMigration(): MigrationInfo {
        val currentApp = getCurrentAppVersion()
        val currentDb = getCurrentDatabaseVersion()
        val currentConfig = getCurrentConfigVersion()
        
        return MigrationInfo(
            needsAppMigration = currentApp < CURRENT_APP_VERSION,
            needsDatabaseMigration = currentDb < CURRENT_DATABASE_VERSION,
            needsConfigMigration = currentConfig < CURRENT_CONFIG_VERSION,
            fromAppVersion = currentApp,
            toAppVersion = CURRENT_APP_VERSION,
            fromDatabaseVersion = currentDb,
            toDatabaseVersion = CURRENT_DATABASE_VERSION,
            fromConfigVersion = currentConfig,
            toConfigVersion = CURRENT_CONFIG_VERSION
        )
    }
    
    /**
     * Finalise la migration en mettant à jour toutes les versions
     */
    fun completeMigration() {
        prefs.edit()
            .putInt(PREF_APP_VERSION, CURRENT_APP_VERSION)
            .putInt(PREF_DATABASE_VERSION, CURRENT_DATABASE_VERSION)
            .putInt(PREF_CONFIG_VERSION, CURRENT_CONFIG_VERSION)
            .putBoolean(PREF_FIRST_LAUNCH, false)
            .apply()
    }
    
    /**
     * Génère un rapport de version pour debugging
     */
    fun getVersionReport(): String {
        return JSONObject().apply {
            put("app_version", getCurrentAppVersion())
            put("target_app_version", CURRENT_APP_VERSION)
            put("database_version", getCurrentDatabaseVersion())
            put("target_database_version", CURRENT_DATABASE_VERSION)
            put("config_version", getCurrentConfigVersion())
            put("target_config_version", CURRENT_CONFIG_VERSION)
            put("first_launch", isFirstLaunch())
        }.toString(2)
    }
}

/**
 * Informations sur les migrations nécessaires
 */
data class MigrationInfo(
    val needsAppMigration: Boolean,
    val needsDatabaseMigration: Boolean,
    val needsConfigMigration: Boolean,
    val fromAppVersion: Int,
    val toAppVersion: Int,
    val fromDatabaseVersion: Int,
    val toDatabaseVersion: Int,
    val fromConfigVersion: Int,
    val toConfigVersion: Int
) {
    fun needsAnyMigration(): Boolean {
        return needsAppMigration || needsDatabaseMigration || needsConfigMigration
    }
}