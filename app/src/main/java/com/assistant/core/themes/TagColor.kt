package com.assistant.core.themes

/**
 * The colors an option of a CHOICE field can carry, by name.
 *
 * A config stores the name, never a color value: each theme decides what a name looks like in
 * each of its palettes (ThemeContract.getTagColor), so a choice keeps its meaning when the user
 * changes theme or switches between light and dark.
 */
enum class TagColor {
    RED,
    ORANGE,
    YELLOW,
    GREEN,
    TEAL,
    BLUE,
    PURPLE,
    PINK,
    GREY
}
