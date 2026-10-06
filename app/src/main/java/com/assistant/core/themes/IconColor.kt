package com.assistant.core.themes

/**
 * The colour a zone's or a tool's icon can carry (`icon_color`): a tag's name, which each theme
 * draws its own way (ThemeContract.ItemIcon). Grey is not one: on an icon it would not stand
 * apart from no colour, which is the neutral icon, drawn in the text's ink.
 */
object IconColor {

    /** The setting's key, in a zone's settings and in a tool's config alike. */
    const val KEY = "icon_color"

    /** The names an icon colour can take, in the order they are offered. */
    val NAMES: List<String> = TagColor.entries.filter { it != TagColor.GREY }.map { it.name }

    /** The options of the setting, each its tag's name and its own swatch, as a CHOICE field stores them. */
    fun options(text: (String) -> String): List<Map<String, Any>> =
        com.assistant.core.fields.ChoiceSettings.storedOptions(
            NAMES,
            labels = NAMES.associateWith { text("tag_color_${it.lowercase()}") },
            colors = NAMES.associateWith { TagColor.valueOf(it) }
        )

    /** The colour of a stored name; null for none. A stored name is one of [NAMES], the services refusing any other. */
    fun of(stored: String?): TagColor? = stored?.takeIf { it.isNotBlank() }?.let { TagColor.valueOf(it) }

    /** Whether [given], a name about to be stored, is one of [NAMES] or none. */
    fun accepts(given: String?): Boolean = given.isNullOrBlank() || given in NAMES
}
