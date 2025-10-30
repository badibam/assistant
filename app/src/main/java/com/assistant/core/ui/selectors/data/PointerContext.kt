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
    DATA,

    /**
     * Tool executions context (scheduled executions, results)
     *
     * Available resources:
     * - executions: Execution history (default selected)
     * - executions_schema: JSON schema for execution validation
     *
     * Behavior:
     * - Temporal filtering on tool_executions.executionTime
     * - Generates TOOL_EXECUTIONS commands if resources selected
     * - Only available if ToolType.supportsExecutions() == true
     */
    EXECUTIONS
}
