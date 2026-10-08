package app.treelune.core.ui.selectors

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.entities.AppSettingCategories
import app.treelune.core.grid.ZonePositions
import app.treelune.core.strings.Strings
import app.treelune.core.themes.IconColor
import app.treelune.core.themes.OptionIcon
import app.treelune.core.ui.UI

/**
 * The zones as the home screen shows them: section by section, the zone groups in their order
 * and then the ungrouped zones, each section's zones row by row and left to right.
 */
object ZoneOrder {

    /** A zone as the choice reads it, with the icon it is shown by. */
    data class Zone(val id: String, val name: String, val group: String?, val row: Int, val column: Int, val icon: OptionIcon? = null)

    /**
     * [zones] in the home screen's order, each with the title of its section: the group's name, or
     * [ungrouped] for the zones without one. No titles at all when no group holds a zone: a single
     * section says nothing.
     */
    fun sorted(zones: List<Zone>, zoneGroups: List<String>, ungrouped: String): List<Pair<Zone, String?>> {
        val bySection = zones.groupBy { ZonePositions.section(it.group, zoneGroups) }
        val titled = bySection.keys.any { it != null }
        return (zoneGroups.filter { it in bySection } + listOf<String?>(null)).flatMap { section ->
            bySection[section].orEmpty().sortedWith(compareBy({ it.row }, { it.column }))
                .map { it to (if (!titled) null else section ?: ungrouped) }
        }
    }
}

/**
 * The choice of a zone, its zones sorted into the sections of the home screen.
 *
 * @param selected The id of the zone chosen, null for none
 */
@Composable
fun ZoneChoice(label: String, selected: String?, onSelect: (String) -> Unit, required: Boolean = true) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { Coordinator(context) }
    var zones by remember { mutableStateOf<List<Pair<ZoneOrder.Zone, String?>>>(emptyList()) }
    LaunchedEffect(Unit) {
        val listed = coordinator.processUserAction("zones.list", mapOf("include_position" to true))
        val settings = coordinator.processUserAction("app_config.get", mapOf("category" to AppSettingCategories.MAIN_SCREEN))
        if (!listed.isSuccess || !settings.isSuccess) {
            UI.Toast(context, listed.error ?: settings.error ?: s.shared("error_load_failed"))
            return@LaunchedEffect
        }
        val groups = ((settings.data?.get("settings") as? Map<*, *>)?.get("zone_groups") as? List<*>).orEmpty().map { it as String }
        val read = (listed.data?.get("zones") as? List<*>).orEmpty().map { zone ->
            val map = zone as Map<*, *>
            ZoneOrder.Zone(
                id = map["id"] as String,
                name = map["name"] as String,
                group = map["group"] as? String,
                row = (map["grid_y"] as Number).toInt(),
                column = (map["grid_x"] as Number).toInt(),
                icon = (map["icon_name"] as? String)?.takeIf { it.isNotBlank() }?.let { OptionIcon(it, IconColor.of(map[IconColor.KEY] as? String)) }
            )
        }
        zones = ZoneOrder.sorted(read, groups, s.shared("label_ungrouped"))
    }
    UI.SectionedSelection(
        label = label,
        options = zones.map { it.first.name },
        sections = zones.map { it.second },
        selected = zones.indexOfFirst { it.first.id == selected }.takeIf { it >= 0 },
        onSelect = { onSelect(zones[it].first.id) },
        required = required,
        icons = zones.map { it.first.icon }
    )
}
