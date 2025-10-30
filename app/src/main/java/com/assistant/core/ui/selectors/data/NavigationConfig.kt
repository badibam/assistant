package com.assistant.core.ui.selectors.data

/**
 * Configuration for hierarchical navigation behavior in selectors
 */
data class NavigationConfig(
    // Level selection permissions
    val allowZoneSelection: Boolean = true,         // Can confirm selection at zone level?
    val allowInstanceSelection: Boolean = true,     // Can confirm selection at instance level?
    val allowFieldSelection: Boolean = true,        // Can confirm selection at field level?
    val allowValueSelection: Boolean = true,        // Can navigate to value selection level?

    // Context-aware selection (new for tool_executions support)
    val allowedContexts: List<PointerContext> = listOf(
        PointerContext.GENERIC,
        PointerContext.CONFIG,
        PointerContext.DATA,
        PointerContext.EXECUTIONS
    ),                                              // Which contexts can be selected
    val defaultContext: PointerContext = PointerContext.GENERIC, // Default selected context

    // UI configuration
    val title: String = "",                         // Custom title or use default scope_selector_title
    val useRelativeLabels: Boolean = false          // Use relative period labels (true for AUTOMATION context)
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