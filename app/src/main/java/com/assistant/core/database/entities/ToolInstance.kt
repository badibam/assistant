package com.assistant.core.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "tool_instances",
    foreignKeys = [
        ForeignKey(
            entity = Zone::class,
            parentColumns = ["id"],
            childColumns = ["zone_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ToolInstance(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val zone_id: String,
    val tool_type: String, // "tracking", "objective", etc.
    val config_json: String, // Configuration spécifique à l'outil
    val config_metadata_json: String, // Métadonnées décrivant la config
    val enabled: Boolean = true,
    val order_index: Int = 0,
    val created_at: Long = System.currentTimeMillis(),
    val updated_at: Long = System.currentTimeMillis()
)