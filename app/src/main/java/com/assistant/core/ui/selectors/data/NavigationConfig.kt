package com.assistant.core.ui.selectors.data

/**
 * Configuration for hierarchical navigation behavior in selectors
 */
data class NavigationConfig(
    val allowZoneSelection: Boolean = true,         // Can confirm selection at zone level?
    val allowInstanceSelection: Boolean = true,     // Can confirm selection at instance level?
    val allowFieldSelection: Boolean = true,        // Can confirm selection at field level?
    val allowValueSelection: Boolean = true,        // Can navigate to value selection level?
    val title: String = "",                         // Custom title or use default scope_selector_title
    val showQueryPreview: Boolean = false,          // Show SQL preview section?
    val showFieldSpecificSelectors: Boolean = true  // Show timestamp/name selectors?
) {
    /**
     * Determines if a selection can be confirmed at the current level
     */
    fun isValidSelection(selectionLevel: SelectionLevel): Boolean {
        return when (selectionLevel) {
            SelectionLevel.ZONE -> allowZoneSelection
            SelectionLevel.INSTANCE -> allowInstanceSelection
            SelectionLevel.FIELD -> allowFieldSelection
        }
    }

    /**
     * Determines if navigation should continue to next level
     */
    fun shouldNavigateToNextLevel(currentLevel: SelectionLevel, hasChildren: Boolean): Boolean {
        if (!hasChildren) return false

        return when (currentLevel) {
            SelectionLevel.ZONE -> true // Always allow zone → instance
            SelectionLevel.INSTANCE -> allowFieldSelection
            SelectionLevel.FIELD -> allowValueSelection // Only show value selectors if allowed
        }
    }
}

/**
 * Levels of selection in zone scope navigation
 */
enum class SelectionLevel {
    ZONE,     // Zone level
    INSTANCE, // Tool instance level
    FIELD     // Data field level
}