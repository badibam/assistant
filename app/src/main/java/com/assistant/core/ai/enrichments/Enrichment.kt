package com.assistant.core.ai.enrichments

import com.assistant.core.ai.enrichments.schemas.*

/**
 * Sealed class représentant les différents types d'enrichissements utilisateur
 *
 * Chaque enrichissement:
 * - Affiche un libellé condensé à l'utilisateur
 * - Fournit un schema JSON complet pour l'IA
 * - Contient les paramètres spécifiques au type
 */
sealed class Enrichment {

    abstract fun getDisplayLabel(): String
    abstract fun getSchema(): String

    /**
     * 🔍 Données - Lecture/accès aux données existantes
     */
    data class Data(
        val zoneId: String,
        val period: String,
        val toolInstanceId: String? = null,
        val detailLevel: String = "summary"
    ) : Enrichment() {

        override fun getDisplayLabel(): String {
            return if (toolInstanceId != null) {
                "données $toolInstanceId $period"
            } else {
                "données zone $period"
            }
        }

        override fun getSchema(): String = DataEnrichmentSchema.SCHEMA
    }

    /**
     * 📝 Utiliser - Ajouter dans outil existant
     */
    data class Use(
        val toolInstanceId: String,
        val operation: String = "create",
        val timestamp: Long? = null
    ) : Enrichment() {

        override fun getDisplayLabel(): String {
            return "utiliser $toolInstanceId"
        }

        override fun getSchema(): String = UseEnrichmentSchema.SCHEMA
    }

    /**
     * ✨ Créer - Nouvelle instance d'outil
     */
    data class Create(
        val toolType: String,
        val zoneName: String,
        val suggestedName: String? = null
    ) : Enrichment() {

        override fun getDisplayLabel(): String {
            return "créer $toolType"
        }

        override fun getSchema(): String = CreateEnrichmentSchema.SCHEMA
    }

    /**
     * 🔧 Modifier - Config/paramètres outil existant
     */
    data class Modify(
        val toolInstanceId: String,
        val aspect: String = "config",
        val description: String? = null
    ) : Enrichment() {

        override fun getDisplayLabel(): String {
            return "modifier $toolInstanceId"
        }

        override fun getSchema(): String = ModifyEnrichmentSchema.SCHEMA
    }

    /**
     * 📁 Organiser - Zones, déplacement, hiérarchie
     */
    data class Organize(
        val action: String, // "move", "create_zone", "delete_zone"
        val elementId: String,
        val targetId: String? = null
    ) : Enrichment() {

        override fun getDisplayLabel(): String {
            return "organiser $action"
        }

        override fun getSchema(): String = OrganizeEnrichmentSchema.SCHEMA
    }

    /**
     * 📚 Documenter - Métadonnées, descriptions
     */
    data class Document(
        val elementType: String, // "zone", "tool", "app"
        val elementId: String,
        val docType: String = "description"
    ) : Enrichment() {

        override fun getDisplayLabel(): String {
            return "documenter $elementType"
        }

        override fun getSchema(): String = DocumentEnrichmentSchema.SCHEMA
    }
}