package com.assistant.core.strings

import android.content.Context

/**
 * Central strings manager with modular namespace support.
 * 
 * Manages string key resolution with automatic prefixing by namespace.
 * Supports discovery pattern: adding new namespaces without core modification.
 */
object StringsManager {
    
    /**
     * Generic string resolution function.
     * 
     * @param namespace String type ("core", "shared", "tool", "theme", etc.)
     * @param key String key
     * @param context Specific context (tooltype, theme, etc.) - null for global namespaces
     * @param androidContext Android Context for resource access
     * @return Localized string
     */
    fun strings(namespace: String, key: String, context: String? = null, androidContext: Context): String {
        val resourceKey = buildResourceKey(namespace, key, context)
        return getStringResource(resourceKey, androidContext)
    }
    
    /**
     * Construit la clé de ressource selon la convention de nommage.
     * 
     * Format: 
     * - Sans contexte: ${namespace}_${key}
     * - Avec contexte: ${namespace}_${context}_${key}
     */
    private fun buildResourceKey(namespace: String, key: String, context: String?): String {
        return if (context != null) {
            "${namespace}_${context}_${key}"
        } else {
            "${namespace}_${key}"
        }
    }
    
    /**
     * Récupère la string depuis les ressources Android.
     * Gère les cas d'erreur avec fallback approprié.
     */
    private fun getStringResource(resourceKey: String, context: Context): String {
        try {
            val resourceId = context.resources.getIdentifier(
                resourceKey, 
                "string", 
                context.packageName
            )
            
            if (resourceId != 0) {
                return context.getString(resourceId)
            } else {
                android.util.Log.w("StringsManager", "String resource not found: $resourceKey")
                return "[$resourceKey]" // Debug fallback
            }
        } catch (e: Exception) {
            android.util.Log.e("StringsManager", "Error loading string resource: $resourceKey", e)
            return "[$resourceKey]" // Debug fallback
        }
    }
}