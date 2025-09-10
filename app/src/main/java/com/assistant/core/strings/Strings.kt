package com.assistant.core.strings

import android.content.Context

/**
 * API publique principale pour l'accès aux strings.
 * Point d'entrée unique pour tout le système de strings modulaire.
 */
object Strings {
    
    /**
     * Crée un contexte strings avec chargement automatique.
     * 
     * Usage typique:
     * ```
     * val s = Strings.for(tool = "tracking", context)
     * 
     * s.shared("save")           // shared_save
     * s.shared("ia_history_filter") // shared_ia_history_filter
     * s.tool("display_name")  // tracking_display_name
     * ```
     * 
     * @param tool Nom du tooltype si contexte tool requis
     * @param context Context Android
     * @return StringsContext avec namespaces chargés
     */
    fun `for`(tool: String? = null, context: Context) = 
        StringsFactory.`for`(tool = tool, context = context)

}