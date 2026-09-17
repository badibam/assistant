package com.assistant.core.ui.selectors.data

/**
 * Defines the context for pointer enrichments in ZoneScopeSelector
 *
 * Determines what type of data is being referenced and what automatic queries
 * should be generated (if any)
 */
enum class PointerContext {
    /**
     * Generic reference - no specific context
     *
     * Behavior:
     * - No automatic DataCommand generation
     * - Optional temporal reference for AI context
     * - AI must explicitly request what it needs
     */
    GENERIC,

    /**
     * Tool configuration context
     *
     * Available resources:
     * - config: Tool instance configuration
     * - config_schema: JSON schema for configuration validation
     *
     * Behavior:
     * - No temporal filtering (configs are not time-based)
     * - Generates TOOL_CONFIG commands if resources selected
     */
    CONFIG,

    /**
     * Tool data context (templates, entries, measurements)
     *
     * Available resources:
     * - data: Tool data entries (default selected)
     * - data_schema: JSON schema for data validation
     *
     * Behavior:
     * - Temporal filtering on tool_data.timestamp
     * - Generates TOOL_DATA commands if resources selected
     */
    DATA
}
