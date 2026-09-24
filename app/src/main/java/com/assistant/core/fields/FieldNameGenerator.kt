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
 * 2. Transliterate to ASCII: ligatures spelled out (œ→oe, ß→ss), accents dropped (é→e, ç→c)
 * 3. Turn every run of other characters (spaces, punctuation, other scripts) into one underscore
 * 4. Trim underscores from start/end
 * 5. If result is empty or only numbers, use fallback "field"
 * 6. Handle collisions with numeric suffix (_2, _3, etc.)
 *
 * A name is fixed once given: changing these rules only changes the names of fields created after.
 */
object FieldNameGenerator {

    /**
     * Generates a unique field name from a display name.
     *
     * Takes the names rather than the definitions holding them, so that a pass assigning
     * several new fields in a row can count what it has just handed out.
     *
     * @param displayName The user-facing name to convert
     * @param takenNames Names already in use
     * @return A unique, normalized field name
     */
    fun generateName(displayName: String, takenNames: List<String>): String {
        val existingNames = takenNames.toSet()

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
     * - "Nombre d'œufs" → "nombre_d_oeufs"
     * - "Heure-de-coucher" → "heure_de_coucher"
     * - "温度" → "field"
     * - "  Multiple   Spaces  " → "multiple_spaces"
     * - "123" → "field_123"
     */
    private fun normalize(displayName: String): String {
        // Step 1-2: Convert to lowercase and transliterate
        val transliterated = transliterate(displayName.lowercase())

        // Step 3: Every run of anything else becomes one underscore, so punctuation separates
        // words the way a space does ("pré-sommeil" → "pre_sommeil", not "presommeil")
        val separated = transliterated.replace(Regex("[^a-z0-9]+"), "_")

        // Step 4: Trim underscores from start and end
        val trimmed = separated.trim('_')

        // Step 5: Handle empty result or numbers-only
        if (trimmed.isEmpty() || trimmed.matches(Regex("\\d+"))) {
            // If original had some digits, append them to fallback
            val digits = displayName.filter { it.isDigit() }
            return if (digits.isNotEmpty()) {
                "field_$digits"
            } else {
                "field"
            }
        }

        // A leading digit gets the "field_" prefix
        return if (trimmed[0].isDigit()) {
            "field_$trimmed"
        } else {
            trimmed
        }
    }

    /**
     * Letters NFD does not decompose into a base letter and a mark, spelled out in ASCII.
     * Without them "cœur" would lose its "œ" and come out as "c_ur".
     */
    private val spelledOut = mapOf(
        'œ' to "oe", 'æ' to "ae", 'ß' to "ss", 'ø' to "o", 'ł' to "l", 'đ' to "d", 'þ' to "th"
    )

    /**
     * Transliterates a lowercase text to ASCII where a Latin spelling exists.
     *
     * Examples:
     * - "café" → "cafe"
     * - "señor" → "senor"
     * - "cœur" → "coeur"
     * - "straße" → "strasse"
     *
     * Other characters (CJK, Arabic...) are left for normalize to turn into separators.
     */
    private fun transliterate(text: String): String {
        val spelled = text.map { spelledOut[it] ?: it.toString() }.joinToString("")

        // Normalize to NFD (decomposed form) to separate base characters from diacritical marks
        val normalized = Normalizer.normalize(spelled, Normalizer.Form.NFD)

        // Remove diacritical marks (combining characters)
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
