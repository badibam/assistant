package com.assistant.core.strings

import android.content.Context

/**
 * Providers de strings pour chaque namespace.
 * Encapsulent la logique de résolution des strings avec préfixage automatique.
 */

/**
 * Provider pour les strings partagées de l'application (core + modules).
 * Inclut UI commune, boutons, actions, et toutes les fonctionnalités core.
 * Namespace global, pas de contexte spécifique.
 */
class SharedStrings(private val context: Context) {
    fun s(key: String): String {
        return StringsManager.strings("shared", key, context = null, context)
    }
}

/**
 * Provider pour les strings spécifiques à un tooltype.
 * Utilise directement le nom du tooltype comme namespace.
 */
class ToolStrings(private val toolType: String, private val context: Context) {
    fun s(key: String): String {
        return StringsManager.strings(toolType, key, context = null, context)
    }
}