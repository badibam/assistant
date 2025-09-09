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
     * s.tool?.s("display_name")  // tool_tracking_display_name
     * ```
     * 
     * @param tool Nom du tooltype si contexte tool requis
     * @param context Context Android
     * @return StringsContext avec namespaces chargés
     */
    fun `for`(tool: String? = null, context: Context) = 
        StringsFactory.`for`(tool = tool, context = context)
    
    /**
     * Variante extensible pour futurs namespaces.
     * 
     * @param tool Nom du tooltype 
     * @param theme Nom du thème
     * @param zone Nom de la zone
     * @param context Context Android
     */
    fun `for`(
        tool: String? = null,
        theme: String? = null, 
        zone: String? = null,
        context: Context
    ) = StringsFactory.`for`(tool = tool, theme = theme, zone = zone, context = context)
}