package com.assistant.core.strings

import android.content.Context

/**
 * Factory pour créer des instances de strings avec chargement automatique.
 * 
 * Charge automatiquement les namespaces disponibles selon le contexte demandé.
 * API finale: s.shared() et s.tool() directement.
 */
class StringsContext(
    private val sharedStrings: SharedStrings,
    private val toolStrings: ToolStrings?
) {
    
    /**
     * Accès aux strings partagées (app core + modules)
     */
    fun shared(key: String): String = sharedStrings.s(key)
    
    /**
     * Accès aux strings spécifiques au tooltype
     */
    fun tool(key: String): String = toolStrings?.s(key) ?: "[$key]"
}

object StringsFactory {
    
    /**
     * Crée un contexte strings avec chargement automatique.
     * 
     * @param tool Nom du tooltype si contexte tool requis
     * @param context Context Android
     * @return StringsContext avec namespaces chargés automatiquement
     */
    fun `for`(tool: String? = null, context: Context): StringsContext {
        return StringsContext(
            sharedStrings = SharedStrings(context),
            toolStrings = tool?.let { ToolStrings(it, context) }
        )
    }
    
    /**
     * Variante avec paramètres nommés pour différents contextes.
     * Extensible pour futurs namespaces.
     */
    fun `for`(
        tool: String? = null,
        theme: String? = null,
        zone: String? = null,
        context: Context
    ): StringsContext {
        return StringsContext(
            sharedStrings = SharedStrings(context),
            toolStrings = tool?.let { ToolStrings(it, context) }
            // Extensible pour theme, zone, etc.
        )
    }
}