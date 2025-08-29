package com.assistant.core.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Gestionnaire de téléchargement et installation des mises à jour
 */
class UpdateDownloader(private val context: Context) {
    
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    
    /**
     * Télécharge et installe une mise à jour
     */
    suspend fun downloadAndInstall(updateInfo: UpdateInfo): DownloadResult {
        if (!updateInfo.isDownloadable()) {
            return DownloadResult.Error("Aucune URL de téléchargement disponible")
        }
        
        return try {
            val apkFile = downloadApk(updateInfo)
            if (apkFile != null && apkFile.exists()) {
                installApk(apkFile)
                DownloadResult.Success(apkFile)
            } else {
                DownloadResult.Error("Échec du téléchargement")
            }
        } catch (e: Exception) {
            DownloadResult.Error("Erreur: ${e.message}")
        }
    }
    
    /**
     * Télécharge l'APK via DownloadManager
     */
    private suspend fun downloadApk(updateInfo: UpdateInfo): File? = suspendCancellableCoroutine { continuation ->
        try {
            val fileName = "assistant-${updateInfo.version}.apk"
            val destination = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
            
            // Supprimer ancien fichier si existant
            if (destination.exists()) {
                destination.delete()
            }
            
            val request = DownloadManager.Request(Uri.parse(updateInfo.downloadUrl)).apply {
                setTitle("Assistant - Mise à jour ${updateInfo.version}")
                setDescription("Téléchargement de la nouvelle version...")
                setDestinationUri(Uri.fromFile(destination))
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                setAllowedOverRoaming(false)
            }
            
            val downloadId = downloadManager.enqueue(request)
            
            // Écouter la fin du téléchargement
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
                    if (id == downloadId) {
                        context?.unregisterReceiver(this)
                        
                        val query = DownloadManager.Query().setFilterById(downloadId)
                        val cursor = downloadManager.query(query)
                        
                        if (cursor.moveToFirst()) {
                            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                            cursor.close()
                            
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                continuation.resume(destination)
                            } else {
                                continuation.resume(null)
                            }
                        } else {
                            cursor.close()
                            continuation.resume(null)
                        }
                    }
                }
            }
            
            context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            
            // Annulation si coroutine annulée
            continuation.invokeOnCancellation {
                try {
                    context.unregisterReceiver(receiver)
                    downloadManager.remove(downloadId)
                } catch (e: Exception) {
                    // Ignore
                }
            }
            
        } catch (e: Exception) {
            continuation.resume(null)
        }
    }
    
    /**
     * Lance l'installation de l'APK
     */
    private fun installApk(apkFile: File) {
        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            context.startActivity(installIntent)
            
        } catch (e: Exception) {
            throw Exception("Impossible de lancer l'installation: ${e.message}")
        }
    }
    
    /**
     * Vérifie si l'application peut installer des packages
     */
    fun canInstallPackages(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            // Sur Android < 8.0, vérifier "Sources inconnues" n'est pas possible programmatiquement
            true
        }
    }
    
    /**
     * Ouvre les paramètres pour autoriser l'installation depuis sources inconnues
     */
    fun openInstallPermissionSettings() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}

/**
 * Résultat d'un téléchargement de mise à jour
 */
sealed class DownloadResult {
    data class Success(val apkFile: File) : DownloadResult()
    data class Error(val message: String) : DownloadResult()
}