package com.assistant.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * Unified entity for storing tool executions
 * Separates template/config (in tool_data) from execution history
 *
 * Used by tooltypes that schedule or trigger executions:
 * - Messages: scheduled message deliveries
 * - Goals: periodic goal evaluations
 * - Alerts: threshold checks and notifications
 * - Questionnaires: scheduled form submissions
 */
@Entity(
    tableName = "tool_executions",
    foreignKeys = [
        ForeignKey(
            entity = ToolInstance::class,
            parentColumns = ["id"],
            childColumns = ["tool_instance_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ToolDataEntity::class,
            parentColumns = ["id"],
            childColumns = ["template_data_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["tool_instance_id"]),
        Index(value = ["template_data_id"]),
        Index(value = ["execution_time"]),
        Index(value = ["status"]),
        Index(value = ["tool_instance_id", "execution_time"])
    ]
)
data class ToolExecutionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "tool_instance_id") val toolInstanceId: String,
    @ColumnInfo(name = "tooltype") val tooltype: String,
    @ColumnInfo(name = "template_data_id") val templateDataId: String,

    // Timing
    @ColumnInfo(name = "scheduled_time") val scheduledTime: Long?,
    @ColumnInfo(name = "execution_time") val executionTime: Long,

    // Status
    @ColumnInfo(name = "status") val status: String,  // "pending"|"completed"|"failed"|"cancelled"

    // Data (JSON)
    @ColumnInfo(name = "snapshot_data") val snapshotData: String,
    @ColumnInfo(name = "execution_result") val executionResult: String,

    // Metadata
    @ColumnInfo(name = "triggered_by") val triggeredBy: String,  // "SCHEDULE"|"MANUAL"|"THRESHOLD"|"EVENT"
    @ColumnInfo(name = "metadata") val metadata: String,

    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
