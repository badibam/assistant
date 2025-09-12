package com.assistant.core.strings

import android.content.Context

/**
 * String providers for each namespace.
 * Encapsulate string resolution logic with automatic prefixing.
 */

/**
 * Provider for shared application strings (core + modules).
 * Includes common UI, buttons, actions, and all core functionalities.
 * Global namespace, no specific context.
 */
class SharedStrings(private val context: Context) {
    fun s(key: String): String {
        return StringsManager.strings("shared", key, context = null, context)
    }
}

/**
 * Provider for tooltype-specific strings.
 * Uses tooltype name directly as namespace.
 */
class ToolStrings(private val toolType: String, private val context: Context) {
    fun s(key: String): String {
        return StringsManager.strings(toolType, key, context = null, context)
    }
}