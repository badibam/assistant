package com.assistant.core.strings

import android.content.Context

/**
 * Factory for creating string instances with automatic loading.
 * 
 * Automatically loads available namespaces according to requested context.
 * Final API: s.shared() and s.tool() directly.
 */
class StringsContext(
    private val sharedStrings: SharedStrings,
    private val toolStrings: ToolStrings?
) {
    
    /**
     * Access to shared strings (app core + modules)
     */
    fun shared(key: String): String = sharedStrings.s(key)
    
    /**
     * Access to tooltype-specific strings
     */
    fun tool(key: String): String = toolStrings?.s(key) ?: "[$key]"
}

object StringsFactory {
    
    /**
     * Creates strings context with automatic loading.
     * 
     * @param tool Tooltype name if tool context required
     * @param context Android Context
     * @return StringsContext with automatically loaded namespaces
     */
    fun `for`(tool: String? = null, context: Context): StringsContext {
        return StringsContext(
            sharedStrings = SharedStrings(context),
            toolStrings = tool?.let { ToolStrings(it, context) }
        )
    }
    
}