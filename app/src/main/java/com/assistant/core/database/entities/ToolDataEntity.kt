package com.assistant.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * Unified entity for storing all tool data
 * Replaces specialized tables (tracking_data, journal_data, etc.)
 */
@Entity(
    tableName = "tool_data",
    foreignKeys = [
        ForeignKey(
            entity = ToolInstance::class,
            parentColumns = ["id"],
            childColumns = ["tool_instance_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["tool_instance_id"]),
        Index(value = ["timestamp"]),
        Index(value = ["tooltype"]),
        Index(value = ["tool_instance_id", "timestamp"])
    ]
)
data class ToolDataEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "tool_instance_id") val toolInstanceId: String,
    @ColumnInfo(name = "tooltype") val tooltype: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long?,
    @ColumnInfo(name = "name") val name: String?,
    @ColumnInfo(name = "data") val data: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)