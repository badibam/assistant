package com.assistant.core.navigation.data

/**
 * Nœud dans l'arbre de navigation des schémas
 */
data class SchemaNode(
    val path: String,           // "zones.health" ou "tools.weight_tracker.value"
    val displayName: String,    // Nom affiché à l'utilisateur
    val type: NodeType,         // ZONE, TOOL, FIELD
    val hasChildren: Boolean,   // Pour UI expand/collapse
    val toolType: String? = null,    // Pour les outils : "tracking", "goal", etc.
    val fieldType: String? = null    // Pour les champs : "string", "number", "boolean", etc.
)

/**
 * Types de nœuds dans l'arbre de navigation
 */
enum class NodeType {
    ZONE,   // Zone de l'application
    TOOL,   // Instance d'outil
    FIELD   // Champ de données
}

/**
 * Résultat de récupération de données contextuelles
 */
data class ContextualDataResult(
    val status: DataResultStatus,
    val data: List<Any> = emptyList(),
    val message: String? = null,
    val totalCount: Int = 0
)

/**
 * Statut du résultat de récupération de données
 */
enum class DataResultStatus {
    OK,              // Données complètes retournées
    TRUNCATED,       // Données partielles (trop nombreuses)
    FALLBACK,        // Stats/résumé au lieu des valeurs
    TIMEOUT,         // Calcul trop long
    ERROR            // Erreur technique
}