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
        private const val PREF_APP_VERSION = "app_version"
        private const val PREF_FIRST_LAUNCH = "first_launch"
    }
    
    /**
     * Current installed app version
     */
    fun getCurrentAppVersion(): Int {
        return prefs.getInt(PREF_APP_VERSION, 0)
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
        val targetVersion = com.assistant.BuildConfig.VERSION_CODE

        return MigrationInfo(
            needsMigration = currentApp < targetVersion,
            fromVersion = currentApp,
            toVersion = targetVersion
        )
    }
    
    /**
     * Finalizes migration by updating all versions
     */
    fun completeMigration() {
        prefs.edit()
            .putInt(PREF_APP_VERSION, com.assistant.BuildConfig.VERSION_CODE)
            .putBoolean(PREF_FIRST_LAUNCH, false)
            .apply()
    }

    /**
     * Generates version report for debugging
     */
    fun getVersionReport(): String {
        return JSONObject().apply {
            put("installed_version", getCurrentAppVersion())
            put("target_version", com.assistant.BuildConfig.VERSION_CODE)
            put("version_name", com.assistant.BuildConfig.VERSION_NAME)
            put("first_launch", isFirstLaunch())
        }.toString(2)
    }
}

/**
 * Information about necessary migrations
 *
 * Note: Database schema migrations are handled by Room directly via @Database(version = X)
 * This only tracks app version for JSON data migrations
 */
data class MigrationInfo(
    val needsMigration: Boolean,
    val fromVersion: Int,
    val toVersion: Int
)