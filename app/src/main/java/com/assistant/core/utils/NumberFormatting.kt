package com.assistant.core.utils

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.*

/**
 * Number formatting utilities for consistent numeric display across the app
 * Uses AppConfig locale override with system locale fallback
 * Handles locale-specific decimal separators and preserves user input style
 */
object NumberFormatting {
    
    /**
     * Get app's configured locale
     * Uses AppConfig locale override if available, falls back to system locale
     */
    private fun getAppLocale(): Locale {
        return try {
            // TODO: Integrate with AppConfigService when available
            // val localeString = AppConfigService.get("locale") as? String
            // return localeString?.let { Locale.forLanguageTag(it) } ?: Locale.getDefault()
            
            // For now, use system locale
            Locale.getDefault()
        } catch (e: Exception) {
            // Fallback to system locale if config unavailable
            Locale.getDefault()
        }
    }
    
    /**
     * Get configured locale's decimal separator
     */
    private fun getDecimalSeparator(): Char {
        return DecimalFormatSymbols.getInstance(getAppLocale()).decimalSeparator
    }
    
    /**
     * Get configured locale's grouping separator (thousands)
     */
    private fun getGroupingSeparator(): Char {
        return DecimalFormatSymbols.getInstance(getAppLocale()).groupingSeparator
    }
    
    /**
     * Format a numeric value preserving user input style
     * @param amount The parsed numeric value
     * @param userInput The original string user typed
     * @param unit Optional unit to append
     * @return Formatted string exactly as user would expect to see
     */
    fun formatRawValue(amount: Double, userInput: String, unit: String = ""): String {
        val cleanInput = userInput.trim()
        
        // Preserve user's decimal separator preference if different from locale
        val userDecimalSep = when {
            cleanInput.contains(',') && !cleanInput.contains('.') -> ','
            cleanInput.contains('.') && !cleanInput.contains(',') -> '.'
            else -> getDecimalSeparator() // Use locale default
        }
        
        // Format preserving user's style
        val formatted = if (userDecimalSep != getDecimalSeparator()) {
            // Convert to user's preferred separator
            cleanInput.replace(getDecimalSeparator(), userDecimalSep)
        } else {
            cleanInput
        }
        
        return if (unit.isNotBlank()) {
            "$formatted $unit"
        } else {
            formatted
        }
    }
    
    /**
     * Parse user numeric input handling locale separators
     * @param input User's numeric input string
     * @return Parsed Double or null if invalid
     */
    fun parseUserInput(input: String): Double? {
        return try {
            val cleanInput = input.trim()
            if (cleanInput.isEmpty()) return null
            
            val decimalSep = getDecimalSeparator()
            val groupingSep = getGroupingSeparator()
            
            // Handle different separator conventions
            val normalized = when {
                // User used comma as decimal (European style: 12,34)
                cleanInput.contains(',') && !cleanInput.contains('.') -> {
                    cleanInput.replace(',', '.')
                }
                // User used both separators (e.g., 1,234.56 or 1.234,56)
                cleanInput.contains(',') && cleanInput.contains('.') -> {
                    val lastComma = cleanInput.lastIndexOf(',')
                    val lastDot = cleanInput.lastIndexOf('.')
                    
                    when {
                        lastDot > lastComma -> {
                            // Format: 1,234.56 (US style)
                            cleanInput.replace(",", "")
                        }
                        lastComma > lastDot -> {
                            // Format: 1.234,56 (European style)
                            cleanInput.replace(".", "").replace(',', '.')
                        }
                        else -> cleanInput.replace(",", "")
                    }
                }
                else -> cleanInput
            }
            
            normalized.toDoubleOrNull()
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
    
    /**
     * Format number for mathematical calculations
     * Always uses dot as decimal separator for consistent calculations
     * @param value Number to format
     * @return Standardized string format for calculations (dot as decimal separator)
     */
    fun formatForCalculations(value: Double): String {
        return value.toString() // Always uses dot, suitable for calculations
    }
    
    /**
     * Format number for user-friendly display
     * Uses app's configured locale formatting conventions
     * @param value Number to format
     * @param maxDecimalPlaces Maximum decimal places to show
     * @return Localized formatted string
     */
    fun formatForDisplay(value: Double, maxDecimalPlaces: Int = 2): String {
        val formatter = NumberFormat.getNumberInstance(getAppLocale())
        formatter.maximumFractionDigits = maxDecimalPlaces
        formatter.isGroupingUsed = true // Show thousands separators
        return formatter.format(value)
    }
    
    /**
     * Format number for consistent storage
     * Always uses dot as decimal separator for database storage
     * @param value Number to format
     * @return Standardized storage format (dot as decimal separator, no grouping)
     */
    fun formatForStorage(value: Double): String {
        return value.toString() // Consistent format for storage
    }
    
    /**
     * Validate and normalize user input for storage
     * Combines parsing and storage formatting
     * @param input User's numeric input
     * @return Normalized string for storage or null if invalid
     */
    fun validateAndNormalize(input: String): String? {
        val parsed = parseUserInput(input)
        return parsed?.let { formatForStorage(it) }
    }
}