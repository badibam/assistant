package com.assistant.core.fields

/**
 * Types of custom fields available for tool instances.
 *
 * Architecture is extensible from V1, with only TEXT_UNLIMITED implemented initially.
 * All components (validator, generator, renderer) use when(type) to facilitate adding new types.
 *
 * V1: Only TEXT_UNLIMITED is available
 * Future extensions: TEXT, TEXT_LONG, NUMERIC, SCALE, CHOICE, BOOLEAN, RANGE, DATE, TIME, FILE, IMAGE, AUDIO
 */
enum class FieldType {
    /**
     * Text field with no length limit.
     * Config: null (no configuration required)
     * Example: Long notes, transcriptions, detailed descriptions
     */
    TEXT_UNLIMITED

    // Future types (not yet implemented):
    // TEXT,           // Short text with max length (config: {max_length?})
    // TEXT_LONG,      // Medium text with max length (config: {max_length?})
    // NUMERIC,        // Number with optional unit, min, max, decimals (config: {unit?, min?, max?, decimals?})
    // SCALE,          // Scale between min and max with labels (config: {min, max, min_label, max_label})
    // CHOICE,         // Single or multiple choice (config: {options: string[], multiple?: boolean})
    // BOOLEAN,        // Yes/No toggle (config: null)
    // RANGE,          // Range between two numbers (config: {min?, max?, unit?})
    // DATE,           // Date picker (config: null)
    // TIME,           // Time picker (config: null)
    // FILE,           // File attachment (config: {allowed_extensions?: string[]})
    // IMAGE,          // Image attachment (config: null)
    // AUDIO           // Audio recording (config: null)
}
