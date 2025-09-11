package com.assistant.core.update

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.assistant.core.strings.Strings

/**
 * Main update manager
 * Coordinates checking, downloading and installation
 */
class UpdateManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val updateChecker = UpdateChecker(context)
    private val updateDownloader = UpdateDownloader(context)
    
    companion object {
        private const val PREF_LAST_CHECK = "last_check_timestamp"
        private const val PREF_IGNORED_VERSION = "ignored_version"
        private const val PREF_AUTO_CHECK_ENABLED = "auto_check_enabled"
        private const val CHECK_INTERVAL_HOURS = 24 // Check once per day
    }
    
    /**
     * Automatically checks for updates if needed
     */
    fun scheduleUpdateCheck(onUpdateFound: (UpdateInfo) -> Unit = {}) {
        if (!isAutoCheckEnabled()) return
        if (!shouldCheckForUpdates()) return
        if (!updateChecker.isNetworkAvailable()) return
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val updateInfo = updateChecker.checkForUpdates()
                if (updateInfo != null && !isVersionIgnored(updateInfo.version)) {
                    onUpdateFound(updateInfo)
                }
                updateLastCheckTime()
            } catch (e: Exception) {
                println("Error during automatic check: ${e.message}")
            }
        }
    }
    
    /**
     * Forces manual update check
     */
    suspend fun checkForUpdatesManually(): CheckResult = withContext(Dispatchers.Main) {
        val s = Strings.`for`(context = context)
        
        if (!updateChecker.isNetworkAvailable()) {
            return@withContext CheckResult.NetworkError(s.shared("update_no_internet"))
        }
        
        return@withContext try {
            val updateInfo = updateChecker.checkForUpdates()
            if (updateInfo != null) {
                CheckResult.UpdateAvailable(updateInfo)
            } else {
                CheckResult.NoUpdate(s.shared("update_latest_version"))
            }
        } catch (e: Exception) {
            CheckResult.Error(s.shared("message_error").format(e.message ?: ""))
        }
    }
    
    /**
     * Downloads and installs an update
     */
    suspend fun downloadAndInstallUpdate(updateInfo: UpdateInfo, onProgress: (String) -> Unit = {}): InstallResult = withContext(Dispatchers.Main) {
        val s = Strings.`for`(context = context)
        
        if (!updateDownloader.canInstallPackages()) {
            return@withContext InstallResult.PermissionRequired(s.shared("update_permission_required"))
        }
        
        return@withContext try {
            onProgress(s.shared("update_downloading"))
            val downloadResult = updateDownloader.downloadAndInstall(updateInfo)
            
            when (downloadResult) {
                is DownloadResult.Success -> {
                    onProgress(s.shared("update_installation_started"))
                    InstallResult.Success(s.shared("update_success"))
                }
                is DownloadResult.Error -> {
                    InstallResult.Error(downloadResult.message)
                }
            }
        } catch (e: Exception) {
            InstallResult.Error(s.shared("message_error").format(e.message ?: ""))
        }
    }
    
    /**
     * Ignores a specific version
     */
    fun ignoreVersion(version: String) {
        prefs.edit().putString(PREF_IGNORED_VERSION, version).apply()
    }
    
    /**
     * Opens settings to authorize installation
     */
    fun openInstallPermissionSettings() {
        updateDownloader.openInstallPermissionSettings()
    }
    
    /**
     * Enables/disables automatic checking
     */
    fun setAutoCheckEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_AUTO_CHECK_ENABLED, enabled).apply()
    }
    
    fun isAutoCheckEnabled(): Boolean {
        return prefs.getBoolean(PREF_AUTO_CHECK_ENABLED, true)
    }
    
    /**
     * Checks if verification should be performed
     */
    private fun shouldCheckForUpdates(): Boolean {
        val lastCheck = prefs.getLong(PREF_LAST_CHECK, 0)
        val now = System.currentTimeMillis()
        val interval = CHECK_INTERVAL_HOURS * 60 * 60 * 1000L
        
        return (now - lastCheck) >= interval
    }
    
    /**
     * Updates timestamp of last check
     */
    private fun updateLastCheckTime() {
        prefs.edit().putLong(PREF_LAST_CHECK, System.currentTimeMillis()).apply()
    }
    
    /**
     * Checks if a version is ignored
     */
    private fun isVersionIgnored(version: String): Boolean {
        val ignoredVersion = prefs.getString(PREF_IGNORED_VERSION, null)
        return ignoredVersion == version
    }
    
    /**
     * Resets update preferences
     */
    fun resetUpdatePreferences() {
        prefs.edit().clear().apply()
    }
}

/**
 * Update check result
 */
sealed class CheckResult {
    data class UpdateAvailable(val updateInfo: UpdateInfo) : CheckResult()
    data class NoUpdate(val message: String) : CheckResult()
    data class NetworkError(val message: String) : CheckResult()
    data class Error(val message: String) : CheckResult()
}

/**
 * Update installation result
 */
sealed class InstallResult {
    data class Success(val message: String) : InstallResult()
    data class PermissionRequired(val message: String) : InstallResult()
    data class Error(val message: String) : InstallResult()
}