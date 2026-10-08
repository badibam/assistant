package app.treelune.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "zones")
data class Zone(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String? = null,
    /** A Lucide icon name, stored under its current name. Null until one is chosen. */
    val icon_name: String? = null,
    /** Its icon's colour, a name of IconColor.NAMES; null for a neutral icon. */
    val icon_color: String? = null,
    val active: Boolean = true,
    /** How its tile shows on the home screen: ICON, MINIMAL, LINE or CONDENSED (ZonePositions.MODES). */
    val display_mode: String = "LINE",
    /** Its column and row in the grid of its zone group on the home screen, placed by ZonePositions. */
    val grid_x: Int,
    val grid_y: Int,
    val created_at: Long = System.currentTimeMillis(),
    val updated_at: Long = System.currentTimeMillis(),

    /**
     * Tool groups defined at zone level (JSON array of group names)
     * Example: ["Santé", "Productivité", "Loisirs"]
     * Tool instances and automations can be assigned to these groups
     */
    val tool_groups: String? = null,

    /**
     * Zone group assignment (links to zone_groups in app_config)
     * Allows organizing zones into groups on MainScreen
     * Example: "Santé", "Productivité", null (ungrouped)
     */
    val group: String? = null
)