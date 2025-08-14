package com.assistant.tools.base

/**
 * Represents a configured instance of a tool
 * Bridges between database entity and runtime tool object
 */
data class ToolInstanceConfig(
    val id: String,
    val zoneId: String,
    val toolType: String,
    val configJson: String,
    val configMetadataJson: String,
    val orderIndex: Int
) {
    companion object {
        /**
         * Create from database entity
         */
        fun fromEntity(entity: com.assistant.core.database.entities.ToolInstance): ToolInstanceConfig {
            return ToolInstanceConfig(
                id = entity.id,
                zoneId = entity.zone_id,
                toolType = entity.tool_type,
                configJson = entity.config_json,
                configMetadataJson = entity.config_metadata_json,
                orderIndex = entity.order_index
            )
        }
    }
    
    /**
     * Create database entity from this config
     */
    fun toEntity(): com.assistant.core.database.entities.ToolInstance {
        return com.assistant.core.database.entities.ToolInstance(
            id = id,
            zone_id = zoneId,
            tool_type = toolType,
            config_json = configJson,
            config_metadata_json = configMetadataJson,
            order_index = orderIndex
        )
    }
}