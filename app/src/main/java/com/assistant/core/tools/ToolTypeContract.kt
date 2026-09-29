package com.assistant.core.tools

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.room.migration.Migration
import com.assistant.core.database.entities.ToolDataEntity
import com.assistant.core.services.ExecutableService
import com.assistant.core.validation.ValidationResult
import com.assistant.core.validation.SchemaProvider
import org.json.JSONObject

/**
 * Contract for tool type implementations
 * Defines the mandatory static metadata that each tool type must provide
 * Includes data migration capabilities for autonomous data upgrades
 */
interface ToolTypeContract {

    /**
     * The label of [fieldName] as a screen shows it, for a validation error or the pointer
     * to name it.
     */
    fun getFormFieldName(fieldName: String, context: Context): String
    
    /**
     * Human-readable display name for this tool type
     * @param context Android context for string resource access
     */
    fun getDisplayName(context: Context): String

    /**
     * Description of this tool type explaining its purpose and usage
     * @param context Android context for string resource access
     */
    fun getDescription(context: Context): String

    /** The display mode a new tool of this type is shown in, in its zone. */
    fun getDefaultDisplayMode(): String

    /**
     * Whether the user's fields show their names beside their values, until the tool's config
     * says otherwise (show_field_labels): where values speak for themselves, a list item's, no.
     */
    fun getDefaultShowFieldLabels(): Boolean
    
    // Schema Provider Implementation
    // SchemaProvider methods are inherited from SchemaProvider interface
    
    
    
    /**
     * Default icon for this tool type, a Lucide name: given to a tool created without one.
     */
    fun getDefaultIconName(): String
    
    /**
     * Suggested icon names for this tool type (shown first in icon selector)
     * @return List of icon IDs that make sense for this tool type
     */
    fun getSuggestedIcons(): List<String> = emptyList()
    
    /**
     * Create service instance for this tool type
     * Returns null if this tool type doesn't have an associated service
     * @param context Android context for service creation
     */
    fun getService(context: Context): ExecutableService?
    
    /**
     * Create DAO instance for this tool type
     * Returns null if this tool type doesn't have an associated DAO
     * @param context Android context for DAO creation
     */
    fun getDao(context: Context): Any?
    
    /**
     * Get database entities for this tool type
     * Used for Room database setup via discovery
     * @return List of entity classes for this tool type
     */
    fun getDatabaseEntities(): List<Class<*>>
    
    /**
     * Usage screen for this tool type
     * @param toolInstanceId ID of the tool instance
     * @param configJson Configuration JSON of the tool instance
     * @param onNavigateBack Called when user wants to navigate back
     * @param onLongClick Called when user long-clicks for configuration access
     * @param openEntry What to open at once (EntryToOpen): an entry touched on the tile, the
     *   oldest of those waiting for the user (getWaiting), or a new one; null for the screen alone
     */
    @Composable
    fun getUsageScreen(
        toolInstanceId: String,
        configJson: String,
        zoneName: String,
        onNavigateBack: () -> Unit,
        onLongClick: () -> Unit,
        openEntry: EntryToOpen?
    )

    /**
     * The tile of [tool] (ToolTile): its summary and its body, placed by UI.ToolCard as its
     * display mode lays them out. [open] opens the tool on one of its entries, as this tool type
     * understands opening one (getUsageScreen's openEntry): a tile opens an entry it shows when
     * touched, or a new one from a button that writes one.
     *
     * While a tool type still shows its tile through TileContent, the summary is its LINE content
     * and the body its EXTENDED, SQUARE or FULL one.
     */
    @Composable
    fun rememberTile(tool: com.assistant.core.database.entities.ToolInstance, open: (EntryToOpen) -> Unit): ToolTile =
        androidx.compose.runtime.remember(tool) {
            object : ToolTile {
                @Composable
                override fun Summary() = TileContent(tool, com.assistant.core.ui.DisplayMode.LINE)

                @Composable
                override fun Body(rows: Int?) = TileContent(tool, when (rows) {
                    null -> com.assistant.core.ui.DisplayMode.FULL
                    1 -> com.assistant.core.ui.DisplayMode.EXTENDED
                    else -> com.assistant.core.ui.DisplayMode.SQUARE
                })
            }
        }

    /**
     * What a tool of this type shows in its tile on a zone, beside the header UI.ToolCard draws:
     * the right half of a LINE tile, the space under the header of a CONDENSED, EXTENDED, SQUARE
     * or FULL one. The ICON and MINIMAL tiles have no room for it and never ask.
     *
     * A tile that shows something the tool's entries hold loads them itself and reloads on
     * their change (DataChangeNotifier). By default, a LINE tile names the tool type and the
     * larger ones show nothing more than their header.
     */
    @Composable
    fun TileContent(tool: com.assistant.core.database.entities.ToolInstance, displayMode: com.assistant.core.ui.DisplayMode) {
        if (displayMode == com.assistant.core.ui.DisplayMode.LINE) {
            com.assistant.core.ui.UI.Text(
                text = getDisplayName(androidx.compose.ui.platform.LocalContext.current),
                type = com.assistant.core.ui.TextType.BODY,
                fillMaxWidth = true,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }

    /**
     * The fields of this tool type's entries, for a tool instance whose config is [config]:
     * how it uses name and timestamp, its fixed fields in data, and the fields of their state.
     * The user's fields come from the config's extra_fields and are not declared here.
     *
     * The data schema of the entries is generated from it (BaseSchemas.getEntrySchema), so a
     * tool type never writes that schema by hand.
     */
    fun getEntryFields(config: JSONObject, context: Context): com.assistant.core.fields.EntryFields

    /**
     * The settings of this tool type's config, beside the ones every tool has
     * (ToolConfigSettings): the config's schema and checking are generated from them.
     */
    fun getConfigSettings(context: Context): List<com.assistant.core.fields.settings.SettingNode>

    /**
     * The operations this tool type's service runs on its entries beside the generic writes
     * (ToolOperation). The AI receives them with a tool's entries schema and calls them with
     * TOOL_OPERATION; a type that declares none offers only the generic writes.
     */
    fun getOperations(context: Context): List<ToolOperation> = emptyList()

    /**
     * [config] once [added] have joined the options of the CHOICE field [field] this tool type
     * declares in data, for a field whose vocabulary is open. Only a tool type that declares an
     * open CHOICE in data keeps its options in its config and answers; the service asks no other.
     */
    fun configWithOptionsAdded(config: JSONObject, field: String, added: List<String>): JSONObject =
        throw IllegalStateException("${this::class.simpleName} declares no open choice in data")

    /**
     * Keep a rule that spans the entries of one tool instance, such as a manual order.
     *
     * ToolDataService calls it on every create, update and delete of an entry of this tool type,
     * whoever the caller — screen or AI — and stores its answer in the same transaction as the write.
     *
     * @param entries Every entry of the tool instance as it will stand once the write is done
     * @param writtenId The entry just created or updated; null after a delete
     * @return The entries whose content must change, the written one included if it must;
     *         empty when the rule already holds
     */
    fun settleEntries(entries: List<ToolDataEntity>, writtenId: String?): List<ToolDataEntity> = emptyList()

    /**
     * [config] completed with what only this tool type writes, on every create and update of an
     * instance, before it is checked: a goal gives each new criterion its key, fixed from then on.
     *
     * @param previous The config as stored, null on a create
     */
    fun completeConfig(config: JSONObject, previous: JSONObject?): JSONObject = config

    /**
     * What waits for the user among an instance's entries (the waiting, docs/BRICKS.md): the
     * conditions an entry passes when it does (Conditions.onField), a questionnaire to fill, an
     * attempt to validate. The core counts them on the instance's tile and its zone's; none,
     * nothing ever waits.
     */
    fun getWaiting(config: JSONObject): List<JSONObject> = emptyList()

    /**
     * Why [entry] may not be changed or deleted by an ordinary write, or null when it may: a
     * goal's validated attempt changes only by its own operation, reopening it. ToolDataService
     * asks it before every update and delete, whoever the caller.
     */
    fun refuseChange(entry: ToolDataEntity, context: Context): String? = null

    /**
     * Get scheduler instance for this tool type.
     *
     * Discovery pattern: CoreScheduler discovers schedulers via ToolTypeManager,
     * tools with scheduling needs return a ToolScheduler instance.
     *
     * @return ToolScheduler instance if this tool requires periodic scheduling, null otherwise
     */
    fun getScheduler(): ToolScheduler? = null
}