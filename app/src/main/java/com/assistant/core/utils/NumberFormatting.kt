package com.assistant.core.utils

/**
 * Number formatting utilities for consistent numeric display across the app
 * Handles locale-specific decimal separators and preserves user input style
 */
object NumberFormatting {
    
    // TODO: Implement locale-based decimal separator detection based on Locale.getDefault()
    private const val DECIMAL_SEPARATOR = "."
    
    /**
     * Format a numeric value preserving user input style
     * @param amount The parsed numeric value
     * @param userInput The original string user typed
     * @param unit Optional unit to append
     * @return Formatted string exactly as user would expect to see
     */
    fun formatRawValue(amount: Double, userInput: String, unit: String = ""): String {
        // For now, simple implementation: preserve user input + unit
        val cleanInput = userInput.trim()
        return if (unit.isNotBlank()) {
            "$cleanInput $unit"
        } else {
            cleanInput
        }
        
        // TODO: Handle more complex cases:
        // - Preserve decimal separator style (, vs .)
        // - Handle scientific notation input
        // - Preserve significant digits from user input
        // - Handle different thousand separators by locale
    }
    
    /**
     * Parse user numeric input handling locale separators
     * @param input User's numeric input string
     * @return Parsed Double or null if invalid
     */
    fun parseUserInput(input: String): Double? {
        return try {
            // Simple implementation for now
            input.trim().replace(",", ".").toDoubleOrNull()
            
            // TODO: Implement proper locale-aware parsing:
            // - Use DecimalFormat with user's locale
            // - Handle thousand separators correctly
            // - Support different decimal separator conventions
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Check if a string represents a valid numeric input
     * @param input User input to validate
     * @return true if input can be parsed as a number
     */
    fun isValidNumericInput(input: String): Boolean {
        return parseUserInput(input) != null
    }
    
    // TODO: Add more formatting utilities as needed:
    // - formatForCalculations(): Standardized format for mathematical operations
    // - formatForDisplay(): User-friendly display format
    // - formatForStorage(): Consistent storage format
}