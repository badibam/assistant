package com.assistant.core.services

import android.content.Context
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.icons.Icons
import com.assistant.core.strings.Strings
import org.json.JSONArray
import org.json.JSONObject

/**
 * The icon vocabulary, as the AI queries it through the ICONS command.
 *
 * - overview: how many icons there are, the categories with their size, and how many results a
 *   search returns at most. No icon is listed: that is what a search is for.
 * - search: the best icons for `categories` and/or `query`, both lists, with the total and its
 *   spread by category, so a search too wide to list says where to narrow it.
 *
 * The search is IconIndex's, the one the icon picker runs: the same words find the same icons
 * for the user and for the model.
 */
class IconService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(context = context)

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        return when (operation) {
            "overview" -> overview()
            "search" -> search(params)
            else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
        }
    }

    private fun overview(): OperationResult {
        val index = Icons.index(context)
        return OperationResult.success(mapOf(
            "total" to index.icons.size,
            "categories" to index.categories.map { mapOf("id" to it.id, "count" to it.count) },
            "max_results" to MAX_RESULTS
        ))
    }

    private fun search(params: JSONObject): OperationResult {
        val categories = stringList(params, "categories")
            ?: return OperationResult.error(s.shared("service_error_icons_param_not_list").format("categories"))
        val query = stringList(params, "query")
            ?: return OperationResult.error(s.shared("service_error_icons_param_not_list").format("query"))

        val index = Icons.index(context)
        categories.firstOrNull { !index.isCategory(it) }?.let {
            return OperationResult.error(s.shared("service_error_icons_unknown_category").format(it))
        }

        val result = index.search(query, categories, MAX_RESULTS)
        return OperationResult.success(mapOf(
            "total" to result.total,
            "truncated" to result.truncated,
            "icons" to result.matches.map { mapOf("name" to it.name, "tags" to it.tags) },
            "count_by_category" to result.countByCategory
        ))
    }

    /** The list under [key]: empty when absent, null when present but not a list of strings. */
    private fun stringList(params: JSONObject, key: String): List<String>? {
        if (!params.has(key)) return emptyList()
        val array = params.opt(key) as? JSONArray ?: return null
        return (0 until array.length()).map { array.opt(it) as? String ?: return null }
    }

    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)
        return when (operation) {
            "overview" -> s.shared("action_verbalize_icons_overview")
            "search" -> s.shared("action_verbalize_icons_search")
            else -> s.shared("action_verbalize_unknown")
        }
    }

    companion object {
        /** Results one search returns at most. */
        const val MAX_RESULTS = 30
    }
}
