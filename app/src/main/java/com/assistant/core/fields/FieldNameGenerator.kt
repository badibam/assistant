package com.assistant.core.fields

import java.text.Normalizer

/**
 * Generates stable technical names for custom fields from display names.
 *
 * The generated name is used as the JSON key in custom_fields data and must be:
 * - Stable (immutable after creation)
 * - Valid identifier (snake_case, ASCII)
 * - Unique within the tool instance
 *
 * Generation rules:
 * 1. Convert to lowercase
 * 2. Transliterate accents to ASCII (é→e, ç→c, etc.)
 * 3. Replace spaces with underscores
 * 4. Remove non-alphanumeric characters (except underscores)
 * 5. Trim underscores from start/end
 * 6. If result is empty or only numbers, use fallback "field"
 * 7. Handle collisions with numeric suffix (_2, _3, etc.)
 */
object FieldNameGenerator {

    /**
     * Generates a unique field name from a display name.
     *
     * @param displayName The user-facing name to convert
     * @param existingFields List of fields already defined (to detect collisions)
     * @return A unique, normalized field name
     */
    fun generateName(displayName: String, existingFields: List<FieldDefinition>): String {
        val existingNames = existingFields.map { it.name }.toSet()

        // Normalize the display name
        val normalized = normalize(displayName)

        // If normalized name is available, return it
        if (normalized !in existingNames) {
            return normalized
        }

        // Handle collision with numeric suffix
        return generateUniqueName(normalized, existingNames)
    }

    /**
     * Normalizes a display name to a valid field name.
     *
     * Examples:
     * - "Calories totales" → "calories_totales"
     * - "Temp. (°C)" → "temp_c"
     * - "温度" → "field"
     * - "  Multiple   Spaces  " → "multiple_spaces"
     * - "123" → "field_123"
     */
    private fun normalize(displayName: String): String {
        // Step 1-2: Convert to lowercase and transliterate accents
        val transliterated = transliterate(displayName.lowercase())

        // Step 3: Replace spaces (and multiple spaces) with single underscore
        val spacesReplaced = transliterated.replace(Regex("\\s+"), "_")

        // Step 4: Remove non-alphanumeric characters except underscores
        val cleaned = spacesReplaced.replace(Regex("[^a-z0-9_]"), "")

        // Step 5: Trim underscores from start and end
        val trimmed = cleaned.trim('_')

        // Step 6: Handle empty result or numbers-only
        if (trimmed.isEmpty() || trimmed.matches(Regex("\\d+"))) {
            // If original had some digits, append them to fallback
            val digits = displayName.filter { it.isDigit() }
            return if (digits.isNotEmpty()) {
                "field_$digits"
            } else {
                "field"
            }
        }

        // Step 7: If starts with number, prefix with "field_"
        return if (trimmed[0].isDigit()) {
            "field_$trimmed"
        } else {
            trimmed
        }
    }

    /**
     * Transliterates accented characters to their ASCII equivalents.
     *
     * Uses NFD normalization to decompose characters, then removes diacritical marks.
     *
     * Examples:
     * - "café" → "cafe"
     * - "naïve" → "naive"
     * - "señor" → "senor"
     * - "ça" → "ca"
     *
     * Non-transliterable characters (e.g., CJK, Arabic) are removed.
     */
    private fun transliterate(text: String): String {
        // Normalize to NFD (decomposed form) to separate base characters from diacritical marks
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)

        // Remove diacritical marks (combining characters)
        // Keep only ASCII letters, numbers, spaces, and basic punctuation
        return normalized.replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
    }

    /**
     * Generates a unique name by appending numeric suffix.
     *
     * If "calories" exists, tries "calories_2", "calories_3", etc.
     *
     * @param baseName The normalized base name
     * @param existingNames Set of already used names
     * @return A unique name with numeric suffix
     */
    private fun generateUniqueName(baseName: String, existingNames: Set<String>): String {
        var suffix = 2
        var candidate = "${baseName}_$suffix"

        while (candidate in existingNames) {
            suffix++
            candidate = "${baseName}_$suffix"
        }

        return candidate
    }
}
