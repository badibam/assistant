package com.assistant.core.update

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Gestionnaire principal des mises à jour
 * Coordonne vérification, téléchargement et installation
 */
class UpdateManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val updateChecker = UpdateChecker(context)
    private val updateDownloader = UpdateDownloader(context)
    
    companion object {
        private const val PREF_LAST_CHECK = "last_check_timestamp"
        private const val PREF_IGNORED_VERSION = "ignored_version"
        private const val PREF_AUTO_CHECK_ENABLED = "auto_check_enabled"
        private const val CHECK_INTERVAL_HOURS = 24 // Vérifier une fois par jour
    }
    
    /**
     * Vérifie automatiquement les mises à jour si nécessaire
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
                println("Erreur lors de la vérification automatique: ${e.message}")
            }
        }
    }
    
    /**
     * Force une vérification manuelle des mises à jour
     */
    suspend fun checkForUpdatesManually(): CheckResult = withContext(Dispatchers.Main) {
        if (!updateChecker.isNetworkAvailable()) {
            return@withContext CheckResult.NetworkError("Aucune connexion internet")
        }
        
        return@withContext try {
            val updateInfo = updateChecker.checkForUpdates()
            if (updateInfo != null) {
                CheckResult.UpdateAvailable(updateInfo)
            } else {
                CheckResult.NoUpdate("Vous avez la dernière version")
            }
        } catch (e: Exception) {
            CheckResult.Error("Erreur: ${e.message}")
        }
    }
    
    /**
     * Télécharge et installe une mise à jour
     */
    suspend fun downloadAndInstallUpdate(updateInfo: UpdateInfo, onProgress: (String) -> Unit = {}): InstallResult = withContext(Dispatchers.Main) {
        if (!updateDownloader.canInstallPackages()) {
            return@withContext InstallResult.PermissionRequired("Autorisation d'installation requise")
        }
        
        return@withContext try {
            onProgress("Téléchargement en cours...")
            val downloadResult = updateDownloader.downloadAndInstall(updateInfo)
            
            when (downloadResult) {
                is DownloadResult.Success -> {
                    onProgress("Installation lancée...")
                    InstallResult.Success("Mise à jour téléchargée et installation lancée")
                }
                is DownloadResult.Error -> {
                    InstallResult.Error(downloadResult.message)
                }
            }
        } catch (e: Exception) {
            InstallResult.Error("Erreur: ${e.message}")
        }
    }
    
    /**
     * Ignore une version spécifique
     */
    fun ignoreVersion(version: String) {
        prefs.edit().putString(PREF_IGNORED_VERSION, version).apply()
    }
    
    /**
     * Ouvre les paramètres pour autoriser l'installation
     */
    fun openInstallPermissionSettings() {
        updateDownloader.openInstallPermissionSettings()
    }
    
    /**
     * Active/désactive la vérification automatique
     */
    fun setAutoCheckEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_AUTO_CHECK_ENABLED, enabled).apply()
    }
    
    fun isAutoCheckEnabled(): Boolean {
        return prefs.getBoolean(PREF_AUTO_CHECK_ENABLED, true)
    }
    
    /**
     * Vérifie s'il faut effectuer une vérification
     */
    private fun shouldCheckForUpdates(): Boolean {
        val lastCheck = prefs.getLong(PREF_LAST_CHECK, 0)
        val now = System.currentTimeMillis()
        val interval = CHECK_INTERVAL_HOURS * 60 * 60 * 1000L
        
        return (now - lastCheck) >= interval
    }
    
    /**
     * Met à jour l'horodatage de la dernière vérification
     */
    private fun updateLastCheckTime() {
        prefs.edit().putLong(PREF_LAST_CHECK, System.currentTimeMillis()).apply()
    }
    
    /**
     * Vérifie si une version est ignorée
     */
    private fun isVersionIgnored(version: String): Boolean {
        val ignoredVersion = prefs.getString(PREF_IGNORED_VERSION, null)
        return ignoredVersion == version
    }
    
    /**
     * Réinitialise les préférences de mise à jour
     */
    fun resetUpdatePreferences() {
        prefs.edit().clear().apply()
    }
}

/**
 * Résultat d'une vérification de mise à jour
 */
sealed class CheckResult {
    data class UpdateAvailable(val updateInfo: UpdateInfo) : CheckResult()
    data class NoUpdate(val message: String) : CheckResult()
    data class NetworkError(val message: String) : CheckResult()
    data class Error(val message: String) : CheckResult()
}

/**
 * Résultat d'une installation de mise à jour
 */
sealed class InstallResult {
    data class Success(val message: String) : InstallResult()
    data class PermissionRequired(val message: String) : InstallResult()
    data class Error(val message: String) : InstallResult()
}