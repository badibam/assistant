package com.assistant.core.utils

import android.content.Context
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
     * Uses centralized LocaleUtils for consistent locale management
     */
    private fun getAppLocale(context: Context): Locale {
        return LocaleUtils.getAppLocale(context)
    }
    
    /**
     * Get configured locale's decimal separator
     */
    private fun getDecimalSeparator(context: Context): Char {
        return DecimalFormatSymbols.getInstance(getAppLocale(context)).decimalSeparator
    }
    
    /**
     * Get configured locale's grouping separator (thousands)
     */
    private fun getGroupingSeparator(context: Context): Char {
        return DecimalFormatSymbols.getInstance(getAppLocale(context)).groupingSeparator
    }
    
    /**
     * Format a numeric value preserving user input style
     * @param amount The parsed numeric value
     * @param userInput The original string user typed
     * @param unit Optional unit to append
     * @param context Application context for locale access
     * @return Formatted string exactly as user would expect to see
     */
    fun formatRawValue(amount: Double, userInput: String, unit: String = "", context: Context): String {
        val cleanInput = userInput.trim()
        
        // Preserve user's decimal separator preference if different from locale
        val userDecimalSep = when {
            cleanInput.contains(',') && !cleanInput.contains('.') -> ','
            cleanInput.contains('.') && !cleanInput.contains(',') -> '.'
            else -> getDecimalSeparator(context) // Use locale default
        }
        
        // Format preserving user's style
        val formatted = if (userDecimalSep != getDecimalSeparator(context)) {
            // Convert to user's preferred separator
            cleanInput.replace(getDecimalSeparator(context), userDecimalSep)
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
     * @param context Application context for locale access
     * @return Parsed Double or null if invalid
     */
    fun parseUserInput(input: String, context: Context): Double? {
        return try {
            val cleanInput = input.trim()
            if (cleanInput.isEmpty()) return null
            
            val locale = LocaleUtils.getAppLocale(context)
            val decimalSep = DecimalFormatSymbols.getInstance(locale).decimalSeparator
            val groupingSep = DecimalFormatSymbols.getInstance(locale).groupingSeparator
            
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
     * @param context Application context for locale access
     * @return true if input can be parsed as a number
     */
    fun isValidNumericInput(input: String, context: Context): Boolean {
        return parseUserInput(input, context) != null
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
     * @param context Application context for locale access
     * @return Localized formatted string
     */
    fun formatForDisplay(value: Double, maxDecimalPlaces: Int = 2, context: Context): String {
        val formatter = NumberFormat.getNumberInstance(getAppLocale(context))
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
     * @param context Application context for locale access
     * @return Normalized string for storage or null if invalid
     */
    fun validateAndNormalize(input: String, context: Context): String? {
        val parsed = parseUserInput(input, context)
        return parsed?.let { formatForStorage(it) }
    }
}