package com.assistant.core.navigation

import android.content.Context
import com.assistant.core.tools.ToolTypeContract
import org.json.JSONObject

/**
 * Extensions pour l'intégration du DataNavigator avec l'architecture existante
 */

/**
 * Extension pour ToolTypeContract : récupération du schéma data résolu
 */
fun ToolTypeContract.getDataSchemaResolved(configJson: String, context: Context): String? {
    val dataSchema = getSchema("data", context) ?: return null

    return try {
        val configMap = parseJsonToMap(configJson)
        com.assistant.core.validation.SchemaResolver.resolve(dataSchema, configMap)
    } catch (e: Exception) {
        // En cas d'erreur de résolution, retourner schéma original
        dataSchema
    }
}

/**
 * Helper pour parser JSON vers Map
 */
private fun parseJsonToMap(jsonString: String): Map<String, Any> {
    return try {
        val json = JSONObject(jsonString)
        val map = mutableMapOf<String, Any>()
        json.keys().forEach { key ->
            map[key] = json.get(key)
        }
        map
    } catch (e: Exception) {
        emptyMap()
    }
}

/**
 * Extension pour faciliter la navigation depuis l'UI
 */
fun DataNavigator.getPathDisplayName(path: String): String {
    return when {
        path.startsWith("zones.") -> {
            val zoneName = path.substringAfter("zones.")
            "Zone: $zoneName"
        }
        path.startsWith("tools.") -> {
            val toolName = path.substringAfter("tools.").substringBefore(".")
            "Outil: $toolName"
        }
        path.contains(".") -> {
            val fieldName = path.substringAfterLast(".")
            "Champ: $fieldName"
        }
        else -> path
    }
}

/**
 * Extension pour construire des critères de filtrage basés sur le contexte
 */
suspend fun DataNavigator.buildFilterCriteria(
    selectedPath: String,
    filterType: FilterType = FilterType.ALL
): FilterCriteria {
    return when (filterType) {
        FilterType.DISTINCT_VALUES -> {
            val result = getDistinctValues(selectedPath)
            FilterCriteria(
                path = selectedPath,
                availableValues = result.data.map { it.toString() },
                resultStatus = result.status
            )
        }
        FilterType.STATS_SUMMARY -> {
            val result = getStatsSummary(selectedPath)
            FilterCriteria(
                path = selectedPath,
                statsInfo = result.data.map { it.toString() },
                resultStatus = result.status
            )
        }
        FilterType.ALL -> {
            val distinctResult = getDistinctValues(selectedPath)
            val statsResult = getStatsSummary(selectedPath)
            FilterCriteria(
                path = selectedPath,
                availableValues = distinctResult.data.map { it.toString() },
                statsInfo = statsResult.data.map { it.toString() },
                resultStatus = distinctResult.status
            )
        }
    }
}

/**
 * Types de filtrage disponibles
 */
enum class FilterType {
    DISTINCT_VALUES,  // Valeurs distinctes uniquement
    STATS_SUMMARY,    // Résumé statistique uniquement
    ALL              // Tout ce qui est disponible
}

/**
 * Critères de filtrage construits pour un path
 */
data class FilterCriteria(
    val path: String,
    val availableValues: List<String> = emptyList(),
    val statsInfo: List<String> = emptyList(),
    val resultStatus: com.assistant.core.navigation.data.DataResultStatus
)