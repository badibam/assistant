package com.assistant.core.ai.enrichments

import android.content.Context
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
    abstract fun getSchema(context: Context): String

    /**
     * 🔍 Pointer - Référencer des données existantes
     */
    data class Pointer(
        val selectedPath: String,                    // Chemin sélectionné via ZoneScopeSelector
        val selectedValues: List<String> = emptyList(), // Valeurs spécifiques sélectionnées
        val selectionLevel: String,                  // ZONE, INSTANCE, FIELD
        val importance: String = "important",        // optionnel, important, essentiel
        val période: String? = null,                 // Filtre temporel optionnel
        val description: String? = null              // Description optionnelle
    ) : Enrichment() {

        override fun getDisplayLabel(): String {
            val baseLabel = when (selectionLevel) {
                "ZONE" -> "données zone"
                "INSTANCE" -> "données outil"
                "FIELD" -> "champ données"
                else -> "données"
            }

            return if (période != null) {
                "$baseLabel $période"
            } else {
                baseLabel
            }
        }

        override fun getSchema(context: Context): String = PointerEnrichmentSchema.getSchema(context)
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

        override fun getSchema(context: Context): String = PointerEnrichmentSchema.getSchema(context) // TODO: implement UseEnrichmentSchema
    }

    /**
     * ✨ Créer - Nouvelle instance d'outil
     */
    data class Create(
        val toolType: String,
        val zoneName: String,
        val suggestedName: String? = null
    ) : Enrichment() {
        override fun getDisplayLabel(): String = "créer $toolType"
        override fun getSchema(context: Context): String = PointerEnrichmentSchema.getSchema(context) // TODO: implement CreateEnrichmentSchema
    }

    /**
     * 🔧 Modifier - Config/paramètres outil existant
     */
    data class Modify(
        val toolInstanceId: String,
        val aspect: String = "config",
        val description: String? = null
    ) : Enrichment() {
        override fun getDisplayLabel(): String = "modifier $toolInstanceId"
        override fun getSchema(context: Context): String = PointerEnrichmentSchema.getSchema(context) // TODO: implement ModifyEnrichmentSchema
    }

    /**
     * 📁 Organiser - Zones, déplacement, hiérarchie
     */
    data class Organize(
        val action: String, // "move", "create_zone", "delete_zone"
        val elementId: String,
        val targetId: String? = null
    ) : Enrichment() {
        override fun getDisplayLabel(): String = "organiser $action"
        override fun getSchema(context: Context): String = PointerEnrichmentSchema.getSchema(context) // TODO: implement OrganizeEnrichmentSchema
    }

    /**
     * 📚 Documenter - Métadonnées, descriptions
     */
    data class Document(
        val elementType: String, // "zone", "tool", "app"
        val elementId: String,
        val docType: String = "description"
    ) : Enrichment() {
        override fun getDisplayLabel(): String = "documenter $elementType"
        override fun getSchema(context: Context): String = PointerEnrichmentSchema.getSchema(context) // TODO: implement DocumentEnrichmentSchema
    }
}