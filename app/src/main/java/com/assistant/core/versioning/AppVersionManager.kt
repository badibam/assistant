package com.assistant.core.versioning

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Application version and migration manager
 */
class AppVersionManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("app_version", Context.MODE_PRIVATE)
    
    companion object {
        const val CURRENT_APP_VERSION = 8
        const val CURRENT_DATABASE_VERSION = 1
        const val CURRENT_CONFIG_VERSION = 1
        
        private const val PREF_APP_VERSION = "app_version"
        private const val PREF_DATABASE_VERSION = "database_version"
        private const val PREF_CONFIG_VERSION = "config_version"
        private const val PREF_FIRST_LAUNCH = "first_launch"
    }
    
    /**
     * Current installed app version
     */
    fun getCurrentAppVersion(): Int {
        return prefs.getInt(PREF_APP_VERSION, 0)
    }
    
    /**
     * Current database version
     */
    fun getCurrentDatabaseVersion(): Int {
        return prefs.getInt(PREF_DATABASE_VERSION, 0)
    }
    
    /**
     * Current configuration version
     */
    fun getCurrentConfigVersion(): Int {
        return prefs.getInt(PREF_CONFIG_VERSION, 0)
    }
    
    /**
     * First app installation
     */
    fun isFirstLaunch(): Boolean {
        return prefs.getBoolean(PREF_FIRST_LAUNCH, true)
    }
    
    /**
     * Updates app version
     */
    fun updateAppVersion(version: Int) {
        prefs.edit().putInt(PREF_APP_VERSION, version).apply()
    }
    
    /**
     * Updates database version
     */
    fun updateDatabaseVersion(version: Int) {
        prefs.edit().putInt(PREF_DATABASE_VERSION, version).apply()
    }
    
    /**
     * Updates configuration version
     */
    fun updateConfigVersion(version: Int) {
        prefs.edit().putInt(PREF_CONFIG_VERSION, version).apply()
    }
    
    /**
     * Marks first installation as complete
     */
    fun markFirstLaunchComplete() {
        prefs.edit().putBoolean(PREF_FIRST_LAUNCH, false).apply()
    }
    
    /**
     * Checks if migrations are necessary
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
     * Finalizes migration by updating all versions
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
     * Generates version report for debugging
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
 * Information about necessary migrations
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