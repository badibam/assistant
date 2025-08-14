package com.assistant.tools.base

import kotlin.reflect.KClass

interface ToolType {
    /**
     * Retourne les entités Room que ce type d'outil utilise
     * Ces entités seront automatiquement ajoutées à la base de données
     */
    fun getEntities(): List<KClass<*>>
    
    /**
     * Nom unique du type d'outil (ex: "tracking", "objective")
     */
    fun getToolTypeName(): String
    
    /**
     * Version du schéma de données de cet outil
     * Utilisé pour les migrations spécifiques à l'outil
     */
    fun getSchemaVersion(): Int = 1
}