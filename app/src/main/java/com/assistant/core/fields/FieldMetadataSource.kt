package com.assistant.core.fields

/**
 * Represents the source of custom field metadata (definitions).
 *
 * Used by rendering components to determine where to load field definitions:
 * - ConfigBased: Load from current tool instance config (standard usage)
 * - SnapshotBased: Use archived metadata (for execution snapshots)
 *
 * This enables self-contained executions that preserve field definitions
 * at the time of execution, even if the config changes later.
 */
sealed class FieldMetadataSource {
    /**
     * Load field definitions from the current tool instance configuration.
     * This is the standard mode for creating/editing tool data entries.
     *
     * @param toolInstanceId The ID of the tool instance to load config from
     */
    data class ConfigBased(val toolInstanceId: String) : FieldMetadataSource()

    /**
     * Use the provided archived field definitions directly.
     * This mode is used for displaying historical execution snapshots
     * where the field definitions are embedded in the snapshot itself.
     *
     * @param metadata The archived field definitions from the snapshot
     */
    data class SnapshotBased(val metadata: List<FieldDefinition>) : FieldMetadataSource()
}
