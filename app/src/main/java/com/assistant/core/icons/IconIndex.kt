package com.assistant.core.icons

import org.json.JSONObject

/**
 * The app's icon vocabulary: every Lucide icon, its tags and categories, and the names Lucide
 * used to give some of them.
 *
 * Read from assets/icons/index.json, which scripts/generate_icons.py writes from the Lucide copy
 * in third_party/lucide. Holds no Android type, so the search the picker and the AI share is
 * tested as it runs.
 */
class IconIndex(
    val version: String,
    val icons: List<Icon>,
    val categories: List<Category>,
    private val aliases: Map<String, String>
) {
    data class Icon(val name: String, val tags: List<String>, val categories: List<String>)

    /** A Lucide category, and the icon that stands for it in the picker. */
    data class Category(val id: String, val icon: String, val count: Int)

    /**
     * One search: the best [matches] and how many there were in all, spread by category so a
     * search too wide to list says where to narrow it.
     */
    data class SearchResult(
        val total: Int,
        val matches: List<Icon>,
        val countByCategory: Map<String, Int>
    ) {
        val truncated: Boolean get() = matches.size < total
    }

    private val byName = icons.associateBy { it.name }
    private val categoryIds = categories.map { it.id }.toSet()

    /** Whether [id] is one of Lucide's categories. */
    fun isCategory(id: String): Boolean = id in categoryIds

    /**
     * The current name of the icon [name] designates, or null when it designates none.
     *
     * A former name leads to the icon it became: Lucide renames icons now and then, and a name
     * stored before, or learned by the model before, still finds its icon.
     */
    fun resolve(name: String): String? = when {
        name in byName -> name
        else -> aliases[name]
    }

    /**
     * Icons in any of [categories] (all of them when empty) whose name or tags hold any of the
     * words of [query] (every icon when empty), best first, at most [limit].
     *
     * Ranking, per word: the name itself, then a name containing the word, then a tag equal to
     * it, then a tag containing it. An icon is scored on its best word, then on how many words
     * it matched, so a search by synonyms puts first what answers most of them.
     */
    fun search(query: List<String>, categories: List<String>, limit: Int): SearchResult {
        val words = query.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val inCategories = if (categories.isEmpty()) icons
        else icons.filter { icon -> icon.categories.any { it in categories } }

        val scored = inCategories.mapNotNull { icon ->
            if (words.isEmpty()) return@mapNotNull icon to (0 to 0)
            val ranks = words.mapNotNull { rank(icon, it) }
            if (ranks.isEmpty()) null else icon to (ranks.min() to -ranks.size)
        }.sortedWith(compareBy({ it.second.first }, { it.second.second }, { it.first.name }))

        val countByCategory = scored
            .flatMap { (icon, _) -> icon.categories }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .toMap()

        return SearchResult(
            total = scored.size,
            matches = scored.take(limit).map { it.first },
            countByCategory = countByCategory
        )
    }

    /** How well [icon] answers [word]: lower is better, null when it does not. */
    private fun rank(icon: Icon, word: String): Int? = when {
        icon.name == word -> 0
        icon.name.split('-').contains(word) -> 1
        icon.name.contains(word) -> 2
        icon.tags.any { it.lowercase() == word } -> 3
        icon.tags.any { it.lowercase().contains(word) } -> 4
        else -> null
    }

    companion object {
        /** Parse the index scripts/generate_icons.py writes. */
        fun parse(json: String): IconIndex {
            val root = JSONObject(json)

            val iconsArray = root.getJSONArray("icons")
            val icons = (0 until iconsArray.length()).map { i ->
                val o = iconsArray.getJSONObject(i)
                Icon(
                    name = o.getString("name"),
                    tags = o.getJSONArray("tags").let { a -> (0 until a.length()).map { a.getString(it) } },
                    categories = o.getJSONArray("categories").let { a -> (0 until a.length()).map { a.getString(it) } }
                )
            }

            val counts = icons.flatMap { it.categories }.groupingBy { it }.eachCount()
            val categoriesArray = root.getJSONArray("categories")
            val categories = (0 until categoriesArray.length()).map { i ->
                val o = categoriesArray.getJSONObject(i)
                val id = o.getString("id")
                Category(id = id, icon = o.getString("icon"), count = counts[id] ?: 0)
            }

            val aliasesObject = root.getJSONObject("aliases")
            val aliases = aliasesObject.keys().asSequence().associateWith { aliasesObject.getString(it) }

            return IconIndex(root.getString("version"), icons, categories, aliases)
        }
    }
}
