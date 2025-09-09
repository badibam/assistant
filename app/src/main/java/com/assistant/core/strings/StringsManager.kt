package com.assistant.core.strings

import android.content.Context

/**
 * Gestionnaire central des strings avec support des namespaces modulaires.
 * 
 * Gère la résolution des clés string avec préfixage automatique selon le namespace.
 * Supporte le discovery pattern : ajout de nouveaux namespaces sans modification du core.
 */
object StringsManager {
    
    /**
     * Fonction générique de résolution des strings.
     * 
     * @param namespace Type de string ("core", "shared", "tool", "theme", etc.)
     * @param key Clé de la string
     * @param context Contexte spécifique (tooltype, theme, etc.) - null pour namespaces globaux
     * @param androidContext Context Android pour accéder aux ressources
     * @return String localisée
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