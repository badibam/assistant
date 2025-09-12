package com.assistant.core.strings

import android.content.Context

/**
 * Main public API for string access.
 * Single entry point for the entire modular string system.
 */
object Strings {
    
    /**
     * Creates strings context with automatic loading.
     * 
     * Typical usage:
     * ```
     * val s = Strings.for(tool = "tracking", context)
     * 
     * s.shared("save")           // shared_save
     * s.shared("ia_history_filter") // shared_ia_history_filter
     * s.tool("display_name")  // tracking_display_name
     * ```
     * 
     * @param tool Tooltype name if tool context required
     * @param context Android Context
     * @return StringsContext with loaded namespaces
     */
    fun `for`(tool: String? = null, context: Context) = 
        StringsFactory.`for`(tool = tool, context = context)

}