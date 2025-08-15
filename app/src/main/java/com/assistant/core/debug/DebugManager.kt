package com.assistant.core.debug

import androidx.compose.runtime.mutableStateListOf
import android.util.Log

/**
 * Manager centralisé pour le debug
 * - Logs Android (adb logcat)
 * - Messages dans l'interface utilisateur
 * - Historique des actions
 */
object DebugManager {
    
    private const val TAG = "ASSISTANT_DEBUG"
    
    /**
     * Liste des messages debug pour affichage dans l'UI
     * Utilise mutableStateListOf pour recomposition automatique
     */
    private val _debugMessages = mutableStateListOf<String>()
    val debugMessages: List<String> = _debugMessages
    
    /**
     * Limite du nombre de messages à garder en mémoire
     */
    private const val MAX_MESSAGES = 50
    
    /**
     * Fonction principale de debug
     * @param message Le message à logger
     * @param showInUI Si true, affiche aussi dans l'interface (par défaut true)
     */
    fun debug(message: String, showInUI: Boolean = true) {
        // Log Android (visible dans logcat)
        Log.d(TAG, message)
        
        // Ajout dans l'UI si demandé
        if (showInUI) {
            addToUI(message)
        }
    }
    
    /**
     * Ajoute un message à la zone debug de l'UI
     */
    private fun addToUI(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        
        val formattedMessage = "[$timestamp] $message"
        
        _debugMessages.add(0, formattedMessage) // Ajouter en haut
        
        // Limiter le nombre de messages
        if (_debugMessages.size > MAX_MESSAGES) {
            _debugMessages.removeAt(_debugMessages.size - 1)
        }
    }
    
    /**
     * Vide la zone debug
     */
    fun clearDebug() {
        _debugMessages.clear()
        debug("Debug cleared", showInUI = false)
    }
    
    /**
     * Debug spécifique pour les clics boutons
     */
    fun debugButtonClick(buttonName: String) {
        debug("🔘 Clic: $buttonName")
    }
    
    /**
     * Debug pour les navigations
     */
    fun debugNavigation(from: String, to: String) {
        debug("🧭 Navigation: $from → $to")
    }
    
    /**
     * Debug pour les erreurs
     */
    fun debugError(error: String, exception: Throwable? = null) {
        val message = "❌ Erreur: $error"
        debug(message)
        exception?.let { 
            Log.e(TAG, "Exception details", it)
        }
    }
}