package com.assistant.core.ui.selectors.data

/**
 * Configuration for hierarchical navigation behavior in selectors
 */
data class NavigationConfig(
    val allowInstanceSelection: Boolean = true,     // Can stop at tool instances?
    val allowFieldSelection: Boolean = true,        // Can navigate to fields?
    val title: String = "",                         // Custom title or use default scope_selector_title
    val showQueryPreview: Boolean = false,          // Show SQL preview section?
    val showFieldSpecificSelectors: Boolean = true, // Show timestamp/name selectors?
    val requireCompleteSelection: Boolean = false   // Force selection to the deepest level?
) {
    /**
     * Determines if a selection is valid based on current level and config
     */
    fun isValidSelection(selectionLevel: SelectionLevel): Boolean {
        return when (selectionLevel) {
            SelectionLevel.ZONE -> true // Always valid to select zone
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
            SelectionLevel.FIELD -> false // Never go deeper than field
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