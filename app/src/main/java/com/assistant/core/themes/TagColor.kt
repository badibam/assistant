package com.assistant.core.themes

/**
 * The colors an option of a CHOICE field can carry, by name.
 *
 * A config stores the name, never a color value: each theme decides what a name looks like in
 * each mode (ThemeContract.getTagColor), so a choice keeps its meaning when the user changes
 * theme, mode or hue.
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
