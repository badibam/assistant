package com.assistant.core.ai.enrichments

import android.content.Context
import com.assistant.core.ai.enrichments.schemas.*
import com.assistant.core.strings.Strings

/**
 * Sealed class représentant les différents types d'enrichissements utilisateur
 *
 * Chaque enrichissement:
 * - Affiche un libellé condensé à l'utilisateur
 * - Fournit un schema JSON complet pour l'IA
 * - Contient les paramètres spécifiques au type
 */
sealed class Enrichment {

    abstract fun getDisplayLabel(context: Context): String
    abstract fun getSchema(context: Context): String

    /**
     * 🔍 Pointer - Référencer des données existantes
     */
    data class Pointer(
        val selectedPath: String,                    // Chemin sélectionné via ZoneScopeSelector
        val selectedValues: List<String> = emptyList(), // Valeurs spécifiques sélectionnées
        val selectionLevel: String,                  // ZONE, INSTANCE, FIELD
        val période: String? = null,                 // Filtre temporel optionnel
        val description: String? = null              // Description optionnelle
    ) : Enrichment() {

        override fun getDisplayLabel(context: Context): String {
            val s = Strings.`for`(context = context)
            val baseLabel = when (selectionLevel) {
                "ZONE" -> s.shared("ai_data_zone")
                "INSTANCE" -> s.shared("ai_data_tool")
                "FIELD" -> s.shared("ai_data_field")
                else -> s.shared("ai_data_generic")
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

        override fun getDisplayLabel(context: Context): String {
            val s = Strings.`for`(context = context)
            return s.shared("ai_enrichment_use").format(toolInstanceId)
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
        override fun getDisplayLabel(context: Context): String {
            val s = Strings.`for`(context = context)
            return s.shared("ai_enrichment_create").format(toolType)
        }
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
        override fun getDisplayLabel(context: Context): String {
            val s = Strings.`for`(context = context)
            return s.shared("ai_enrichment_modify").format(toolInstanceId)
        }
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
        override fun getDisplayLabel(context: Context): String {
            val s = Strings.`for`(context = context)
            return s.shared("ai_enrichment_organize").format(action)
        }
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
        override fun getDisplayLabel(context: Context): String {
            val s = Strings.`for`(context = context)
            return s.shared("ai_enrichment_document").format(elementType)
        }
        override fun getSchema(context: Context): String = PointerEnrichmentSchema.getSchema(context) // TODO: implement DocumentEnrichmentSchema
    }
}