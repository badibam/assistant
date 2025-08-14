package com.assistant.core.database

import com.assistant.tools.base.ToolType
import kotlin.reflect.KClass

/**
 * Registre des types d'outils disponibles dans l'application
 * Permet la découverte automatique des entités pour la base de données
 */
object ToolTypeRegistry {
    private val registeredToolTypes = mutableListOf<ToolType>()
    
    /**
     * Enregistre un nouveau type d'outil
     * À appeler au démarrage de l'application pour chaque outil disponible
     */
    fun registerToolType(toolType: ToolType) {
        if (registeredToolTypes.none { it.getToolTypeName() == toolType.getToolTypeName() }) {
            registeredToolTypes.add(toolType)
        }
    }
    
    /**
     * Retourne toutes les entités de tous les outils enregistrés
     * Utilisé par AppDatabase pour configurer Room
     */
    fun getAllEntities(): List<KClass<*>> {
        return registeredToolTypes.flatMap { it.getEntities() }
    }
    
    /**
     * Récupère un type d'outil par son nom
     */
    fun getToolType(typeName: String): ToolType? {
        return registeredToolTypes.find { it.getToolTypeName() == typeName }
    }
    
    /**
     * Retourne tous les types d'outils enregistrés
     */
    fun getAllToolTypes(): List<ToolType> {
        return registeredToolTypes.toList()
    }
}