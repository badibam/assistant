package app.treelune.core.database.entities

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
    val tooltype: String, // "tracking", "objective", etc.
    val config_json: String, // Configuration spécifique à l'outil
    val enabled: Boolean = true,
    /** Its column and row in the grid of its group section, placed by ToolPositions. */
    val grid_x: Int,
    val grid_y: Int,
    val created_at: Long = System.currentTimeMillis(),
    val updated_at: Long = System.currentTimeMillis()
)