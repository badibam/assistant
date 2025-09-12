package com.assistant.core.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import com.assistant.core.strings.Strings

/**
 * Update checker via GitHub Releases API
 */
class UpdateChecker(private val context: Context) {
    
    companion object {
        private const val GITHUB_API_URL = "https://api.github.com/repos/badibam/assistant/releases/latest"
        private const val USER_AGENT = "Assistant-Android-App"
    }
    
    /**
     * Checks if a new version is available
     */
    suspend fun checkForUpdates(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val latestRelease = fetchLatestRelease()
            val currentVersion = getCurrentVersionCode()
            val latestVersion = parseVersionCode(latestRelease.getString("tag_name"))
            
            if (latestVersion > currentVersion) {
                val s = Strings.`for`(context = context)
                return@withContext UpdateInfo(
                    version = latestRelease.getString("tag_name"),
                    versionCode = latestVersion,
                    changelog = latestRelease.optString("body", s.shared("update_changelog_default")),
                    downloadUrl = findApkDownloadUrl(latestRelease),
                    releaseDate = latestRelease.getString("published_at"),
                    isPrerelease = latestRelease.optBoolean("prerelease", false)
                )
            }
            
            null
        } catch (e: Exception) {
            println("Error checking for updates: ${e.message}")
            null
        }
    }
    
    /**
     * Fetches latest release information from GitHub API
     */
    private suspend fun fetchLatestRelease(): JSONObject = withContext(Dispatchers.IO) {
        val url = URL(GITHUB_API_URL)
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP Error: $responseCode")
            }
            
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = reader.use { it.readText() }
            
            JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }
    
    /**
     * Finds APK download URL in release assets
     */
    private fun findApkDownloadUrl(release: JSONObject): String? {
        try {
            val assets = release.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    return asset.getString("browser_download_url")
                }
            }
        } catch (e: Exception) {
            println("Error searching for APK: ${e.message}")
        }
        return null
    }
    
    /**
     * Gets current application versionCode
     */
    private fun getCurrentVersionCode(): Int {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionCode
        } catch (e: Exception) {
            1 // Default version if error
        }
    }
    
    /**
     * Parses version tag (e.g., "v1.2.0") to versionCode
     * Assumes format: v{major}.{minor}.{patch} → {major}{minor}{patch}
     */
    private fun parseVersionCode(tagName: String): Int {
        return try {
            val version = tagName.removePrefix("v").split(".")
            if (version.size >= 3) {
                val major = version[0].toIntOrNull() ?: 0
                val minor = version[1].toIntOrNull() ?: 0
                val patch = version[2].toIntOrNull() ?: 0
                
                // Convert to versionCode (e.g., 1.2.3 → 10203)
                major * 10000 + minor * 100 + patch
            } else {
                tagName.removePrefix("v").toIntOrNull() ?: 0
            }
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * Checks network connectivity
     */
    fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
                as android.net.ConnectivityManager
            val activeNetwork = connectivityManager.activeNetworkInfo
            activeNetwork?.isConnectedOrConnecting ?: false
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Information about an available update
 */
data class UpdateInfo(
    val version: String,         // ex: "v1.2.0"
    val versionCode: Int,        // ex: 10200
    val changelog: String,       // Release notes
    val downloadUrl: String?,    // APK download URL
    val releaseDate: String,     // Release date
    val isPrerelease: Boolean    // Beta/preview version
) {
    fun isDownloadable(): Boolean = !downloadUrl.isNullOrBlank()
    
    fun getFormattedChangelog(): String {
        return changelog.take(500) + if (changelog.length > 500) "..." else ""
    }
}