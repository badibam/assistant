package app.treelune.core.ui.screens

import app.treelune.core.utils.JsonUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.style.TextAlign
import app.treelune.core.ui.UI
import app.treelune.core.ui.*
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.commands.CommandStatus
import app.treelune.core.coordinator.mapData
import app.treelune.core.coordinator.executeWithLoading
import app.treelune.core.strings.Strings
import app.treelune.core.database.entities.AppSettingCategories
import app.treelune.core.database.entities.Zone
import app.treelune.core.navigation.LocalBreadcrumb
import app.treelune.core.navigation.Navigator
import app.treelune.core.navigation.Place
import app.treelune.core.navigation.PlaceStack
import app.treelune.core.tools.EntryToOpen
import app.treelune.core.tools.ToolTypeManager
import app.treelune.core.ui.screens.settings.*
import app.treelune.core.ai.ui.screens.AIProvidersScreen
import app.treelune.core.ai.ui.chat.AIFloatingChat
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.core.utils.DataChangeEvent
import app.treelune.core.utils.LogManager

/**
 * The app's screens: the place at the top of the stack (Navigator), the chat laid over the one
 * under it. What opens a place from outside the screen on display arrives here: a notification's
 * tool ([openToolId]), a tool asked for from any screen (ToolRequests), a chat asked for with its
 * content (ChatRequests).
 *
 * The stack is saved with the activity's state, as addresses, and read back after the process's
 * death. Each place keeps what its screen saved (rememberSaveable) while it stays in the stack,
 * and drops it once it leaves.
 *
 * @param openToolId A tool to open, on its oldest entry that waits, the way its tile opens it: the
 *   one a touched notification is about; [onToolOpened] once it is opened
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(openToolId: String? = null, onToolOpened: () -> Unit = {}) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { Coordinator(context) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // The stack across the process's death: read back once, when nothing else is open
    val saved = rememberSaveable { mutableStateOf<ArrayList<String>?>(null) }
    remember { saved.value?.let { if (Navigator.stack.size == 1) Navigator.restore(it) }; true }
    LaunchedEffect(Unit) { snapshotFlow { Navigator.addresses() }.collect { saved.value = it } }

    val zones = rememberZones { errorMessage = it }
    // The demo is there while one of its zones is
    val demoInstalled = zones.list.any { it.id.startsWith(app.treelune.core.guide.DEMO_PREFIX) }
    LaunchedEffect(Unit) { app.treelune.core.guide.Guide.start(context) }

    // A chat asked for from any screen, with its content, laid over that screen
    LaunchedEffect(Unit) {
        app.treelune.core.ai.orchestration.ChatRequests.requests.collect { prefill ->
            app.treelune.core.ai.orchestration.AIOrchestrator.startNewChatSession(prefill)
            if (Navigator.top != Place.Chat) Navigator.push(Place.Chat)
        }
    }
    // The tool a notification asked for, on its oldest entry that waits
    LaunchedEffect(openToolId) {
        val toolId = openToolId ?: return@LaunchedEffect
        val zoneId = zoneOfTool(coordinator, toolId)
        if (zoneId == null) {
            // Deleted since the notification: the app opens as it is
            LogManager.ui("MainScreen: tool $toolId of a notification not found", "WARN")
            onToolOpened()
            return@LaunchedEffect
        }
        val waiting = coordinator.processUserAction("tools.waiting", mapOf("zone_id" to zoneId))
        val entryId = (waiting.data?.get("oldest") as? Map<*, *>)?.get(toolId) as? String
        Navigator.open(Place.Tool(toolId, zoneId), entryId?.let { EntryToOpen.Existing(it) })
        onToolOpened()
    }
    // A tool asked for from any screen, on one of its entries
    LaunchedEffect(Unit) {
        app.treelune.core.tools.ToolRequests.requests.collect { (toolId, entryId) ->
            val zoneId = zoneOfTool(coordinator, toolId)
            if (zoneId == null) {
                LogManager.ui("MainScreen: tool $toolId asked for not found", "WARN")
                errorMessage = s.shared("service_error_tool_instance_not_found")
                return@collect
            }
            Navigator.open(Place.Tool(toolId, zoneId), entryId?.let { EntryToOpen.Existing(it) })
        }
    }

    // The back key: the home screen asks before leaving the app, any other place goes back.
    // Composed before the places, whose own handlers (a form with changes) answer first.
    var showExitConfirm by remember { mutableStateOf(false) }
    BackHandler(enabled = Navigator.top == Place.Home) { showExitConfirm = true }
    BackHandler(enabled = Navigator.top != Place.Home) { Navigator.pop() }

    // The place drawn: the top, or the one under the chat
    val stack = Navigator.stack
    val baseIndex = PlaceStack.baseIndex(stack)
    val base = stack[baseIndex]
    val breadcrumb = if (baseIndex == 0) null else PlaceStack.breadcrumb(stack, baseIndex) { Navigator.names[it.address] }

    // What each place saved, kept while it is in the stack
    val holder = rememberSaveableStateHolder()
    val kept = remember { mutableSetOf<String>() }
    LaunchedEffect(stack.toList()) {
        val now = stack.map { it.address }.toSet()
        (kept - now).forEach { holder.removeState(it) }
        kept.clear(); kept.addAll(now)
    }

    // The first-launch screen, once, before any place; then the place, the tutorial's band under it
    val guide = app.treelune.core.guide.Guide.progress
    if (guide != null && !guide.welcomeSeen && zones.loaded) {
        app.treelune.core.guide.ui.WelcomeScreen(demoInstalled)
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            key(base.address) {
                holder.SaveableStateProvider(base.address) {
                    CompositionLocalProvider(LocalBreadcrumb provides breadcrumb) {
                        PlaceScreen(base, zones, demoInstalled)
                    }
                }
            }
        }
        app.treelune.core.guide.ui.GuideBand()
    }
    app.treelune.core.guide.Guide.ended?.let { app.treelune.core.guide.ui.GuideEndDialog(it, demoInstalled) }
    app.treelune.core.guide.Guide.demoMissingFor?.let { app.treelune.core.guide.ui.GuideDemoMissingDialog(it) }

    AIFloatingChat(isVisible = Navigator.top == Place.Chat, onDismiss = { Navigator.pop() })

    if (showExitConfirm) {
        UI.ConfirmDialog(
            title = s.shared("action_quit"),
            message = s.shared("exit_confirm_message"),
            confirmText = s.shared("action_quit"),
            cancelText = s.shared("action_cancel"),
            onConfirm = { (context as? android.app.Activity)?.finish() },
            onDismiss = { showExitConfirm = false }
        )
    }

    errorMessage?.let { message ->
        LaunchedEffect(message) {
            UI.Toast(context, message, Duration.LONG)
            errorMessage = null
        }
    }
}

/** The zone a tool belongs to, null when there is no such tool. */
private suspend fun zoneOfTool(coordinator: Coordinator, toolId: String): String? {
    val tool = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to toolId))
    return (tool.data?.get("tool_instance") as? Map<*, *>)?.get("zone_id") as? String
}

/**
 * The screen of [place], which names it for the breadcrumb. A place whose zone or tool is gone
 * (deleted from another screen, by the AI) leaves the stack.
 */
@Composable
private fun PlaceScreen(place: Place, zones: ZonesState, demoInstalled: Boolean) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val back = { Navigator.pop() }
    val zone = place.zoneId?.let { id -> zones.list.find { it.id == id } }
    // A place of a zone that is gone leaves, once the zones are read
    if (place.zoneId != null && zone == null) {
        if (zones.loaded) LaunchedEffect(place) { Navigator.drop(place) }
        return
    }
    @Composable
    fun name(text: String) = SideEffect { Navigator.name(place, text) }

    when (place) {
        Place.Home -> {
            name(s.shared("nav_home"))
            HomeScreen(zones)
        }
        Place.HomeConfig -> {
            name(s.shared("nav_home_config"))
            AppSettingsScreen(category = AppSettingCategories.MAIN_SCREEN, onBack = back)
        }
        Place.Settings -> {
            name(s.shared("settings_title"))
            SettingsScreen(onBack = back, onOpen = { Navigator.push(Place.SettingsPage(it)) })
        }
        is Place.SettingsPage -> {
            name(s.shared("settings_${place.id}"))
            when (place.id) {
                "ai_providers" -> AIProvidersScreen(onBack = back)
                "format" -> AppSettingsScreen(category = AppSettingCategories.FORMAT, onBack = back)
                "ai_limits" -> AppSettingsScreen(category = AppSettingCategories.AI_LIMITS, onBack = back)
                "validation" -> AppSettingsScreen(category = AppSettingCategories.VALIDATION_CONFIG, onBack = back)
                "ui" -> AppSettingsScreen(category = AppSettingCategories.UI, onBack = back)
                "demo" -> DemoSettingsScreen(onBack = back)
                "external_access" -> app.treelune.core.mcp.ui.ExternalAccessSettingsScreen(onBack = back)
                "data" -> DataSettingsScreen(onBack = back)
                "logs" -> LogsScreen(onBack = back)
                "bug_report" -> BugReportScreen(onBack = back)
                else -> throw IllegalArgumentException("No settings page '${place.id}'")
            }
        }
        is Place.CreateZone -> {
            name(s.shared("nav_new_zone"))
            CreateZoneScreen(preSelectedGroup = place.group, onCancel = back, onCreate = back)
        }
        is Place.Zone -> {
            name(zone!!.name)
            app.treelune.core.ui.components.ShownScreen { ZoneScreen(zone = zone) }
        }
        is Place.ZoneConfig -> {
            name(s.shared("nav_configuration"))
            CreateZoneScreen(
                existingZone = zone,
                onCancel = back,
                onUpdate = back,
                onDelete = { Navigator.dropZone(place.id) }
            )
        }
        is Place.Tool -> ToolPlace(place, zone!!.name)
        is Place.ToolConfig -> {
            name(s.shared(if (place.toolId == null) "nav_new_tool" else "nav_configuration"))
            val toolType = requireNotNull(ToolTypeManager.getToolType(place.tooltype)) { "No tool type '${place.tooltype}'" }
            app.treelune.core.tools.ui.ToolConfigScreen(
                toolType = toolType,
                tooltype = place.tooltype,
                zoneId = place.zoneId,
                existingToolId = place.toolId,
                initialGroup = place.group,
                onDone = back,
                onCancel = back
            )
        }
        is Place.Variable -> {
            name(s.shared(if (place.variableId == null) "nav_new_variable" else "nav_variable"))
            app.treelune.core.ui.variables.VariableScreen(zoneId = place.zoneId, variableId = place.variableId, group = place.group, onDone = back)
        }
        is Place.Automation -> {
            name(s.shared("nav_automation"))
            app.treelune.core.ai.ui.automation.AutomationScreen(
                automationId = place.id,
                onNavigateBack = back,
                onNavigateToExecution = { sessionId -> Navigator.push(Place.Execution(sessionId, place.id, place.zoneId)) }
            )
        }
        is Place.Execution -> {
            name(s.shared("nav_execution"))
            app.treelune.core.ai.ui.automation.ExecutionDetailScreen(sessionId = place.sessionId, onNavigateBack = back)
        }
        is Place.Seed -> {
            name(s.shared("nav_seed"))
            app.treelune.core.ai.ui.screens.AIScreen(sessionId = place.sessionId, onClose = back)
        }
        Place.Guide -> {
            name(s.shared("guide_title"))
            app.treelune.core.guide.ui.GuideScreen()
        }
        is Place.Chapter -> app.treelune.core.guide.ui.ChapterScreen(place.id, demoInstalled)
        Place.Chat -> throw IllegalStateException("The chat is laid over a place, never drawn as one")
    }
}

/**
 * A tool's screen, the tool read by its id and read again when its zone's tools change (its
 * config saved). A tool that is not found any more (deleted from its config screen) leaves.
 */
@Composable
private fun ToolPlace(place: Place.Tool, zoneName: String) {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    var tool by remember { mutableStateOf<Map<*, *>?>(null) }
    var version by remember { mutableStateOf(0) }
    LaunchedEffect(place.id, version) {
        val result = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to place.id))
        if (result.status == CommandStatus.SUCCESS) {
            tool = result.data?.get("tool_instance") as Map<*, *>
        } else {
            LogManager.ui("ToolPlace: tool ${place.id} not read, leaving its place: ${result.error}", "WARN")
            Navigator.drop(place)
        }
    }
    LaunchedEffect(place.id) {
        DataChangeNotifier.changes.collect { event ->
            if (event is DataChangeEvent.ToolsChanged && event.zoneId == place.zoneId) version++
        }
    }
    val current = tool ?: return
    val tooltype = current["tooltype"] as String
    SideEffect { Navigator.name(place, current["name"] as String) }
    app.treelune.core.ui.components.ShownScreen {
        ToolTypeManager.getToolType(tooltype)?.getUsageScreen(
            toolInstanceId = place.id,
            configJson = JsonUtils.toJSONObject(current["config"] as Map<String, Any?>).toString(),
            zoneName = zoneName,
            onNavigateBack = { Navigator.pop() },
            onLongClick = { Navigator.push(Place.ToolConfig(place.zoneId, tooltype, place.id, null)) },
            openEntry = Navigator.openings[place.address]
        )
    }
}

/** The zones and the home screen's groups, read once and again whenever either changes. */
class ZonesState {
    var list by mutableStateOf<List<Zone>>(emptyList())
    var groups by mutableStateOf<List<String>>(emptyList())
    /** Both read at least once: a zone missing from [list] is then gone. */
    var loaded by mutableStateOf(false)
}

@Composable
private fun rememberZones(onError: (String) -> Unit): ZonesState {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val state = remember { ZonesState() }
    suspend fun readZones() {
        coordinator.executeWithLoading(
            operation = "zones.list",
            params = mapOf("include_position" to true),
            onLoading = {},
            onError = onError
        )?.let { result ->
            state.list = result.mapData("zones") { zoneFrom(it) }
            // Every zone named for the breadcrumb, where a tool stacked alone names its zone
            state.list.forEach { Navigator.name(Place.Zone(it.id), it.name) }
        }
    }
    suspend fun readGroups() {
        coordinator.executeWithLoading(
            operation = "app_config.get",
            params = mapOf("category" to AppSettingCategories.MAIN_SCREEN),
            onLoading = {},
            onError = onError
        )?.let { result ->
            state.groups = ((result.data?.get("settings") as Map<*, *>)["zone_groups"] as List<*>).map { it as String }
        }
    }
    LaunchedEffect(Unit) {
        readZones()
        readGroups()
        state.loaded = true
    }
    LaunchedEffect(Unit) {
        DataChangeNotifier.changes.collect { event ->
            when (event) {
                is DataChangeEvent.ZonesChanged -> readZones()
                is DataChangeEvent.AppConfigChanged -> readGroups()
                else -> {}
            }
        }
    }
    return state
}

/**
 * The home screen: the zones by group, each opened on top of it; its header opens the settings
 * and its own configuration, a zone's long touch the zone's configuration.
 */
@Composable
private fun HomeScreen(zones: ZonesState) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val zoneGroups = zones.groups

    // The edit mode of the zone groups' grids, a section named by its group, "" for the ungrouped one
    val gridEditor = app.treelune.core.ui.components.rememberGridEditor(
        placeOperation = "zones.place",
        placeParams = emptyMap(),
        sectionTiles = { key -> app.treelune.core.grid.ZonePositions.tiles(zones.list, zoneGroups, key.ifEmpty { null }) },
        onError = { errorMessage = it }
    )
    app.treelune.core.ui.components.CloseEditOnLeave(gridEditor)

    Box(modifier = Modifier.fillMaxSize()) {
      // The bar of a zone being moved goes under the scrolling content
      Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = UI.Space.L),
            verticalArrangement = Arrangement.spacedBy(UI.Space.L)
        ) {
            // The Guide's book, at the top of the home screen, marked while a tutorial waits
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = UI.Space.L), horizontalArrangement = Arrangement.End) {
                Box {
                    UI.ActionButton(action = ButtonAction.GUIDE, display = ButtonDisplay.ICON, size = Size.S) { Navigator.push(Place.Guide) }
                    if (app.treelune.core.guide.Guide.pending) Box(modifier = Modifier.align(Alignment.TopEnd)) { UI.WaitingMark() }
                }
            }
            UI.PageHeader(
                title = s.shared("app_name"),
                subtitle = null,
                icon = null,
                leftButton = ButtonAction.SETTINGS,
                rightButton = ButtonAction.CONFIGURE,
                onLeftClick = { Navigator.push(Place.Settings) },
                onRightClick = { Navigator.push(Place.HomeConfig) }
            )

            if (!zones.loaded) {
                UI.Text(
                    text = s.shared("message_loading"),
                    type = TextType.BODY,
                    fillMaxWidth = true,
                    textAlign = TextAlign.Center
                )
            } else {
                // For each defined group, display its section (always shown, even if empty)
                zoneGroups.forEach { groupName ->
                    ZoneGroupSection(
                        groupName = groupName,
                        zones = zones.list,
                        configuredGroups = zoneGroups,
                        editor = gridEditor,
                        onZoneClick = { zone -> Navigator.push(Place.Zone(zone.id)) },
                        onZoneLongClick = { zone -> Navigator.push(Place.ZoneConfig(zone.id)) },
                        onAddZone = { Navigator.push(Place.CreateZone(groupName)) },
                        context = context
                    )
                }

                // "Ungrouped" section (always shown, even if empty)
                // Includes zones with null group OR zones with group not in zoneGroups list
                ZoneGroupSection(
                    groupName = null, // null = ungrouped
                    zones = zones.list,
                    hasConfiguredGroups = zoneGroups.isNotEmpty(),
                    configuredGroups = zoneGroups, // Pass list to filter orphaned zones
                    editor = gridEditor,
                    onZoneClick = { zone -> Navigator.push(Place.Zone(zone.id)) },
                    onZoneLongClick = { zone -> Navigator.push(Place.ZoneConfig(zone.id)) },
                    onAddZone = { Navigator.push(Place.CreateZone(null)) },
                    context = context
                )
            }
        }
        if (gridEditor.selectedId != null) app.treelune.core.ui.components.GridEditBar(gridEditor)
      }

      // The chat's button, floating: the home screen has no breadcrumb to carry it. Away while a zone moves
      if (gridEditor.selectedId == null) Box(
          modifier = Modifier
              .align(Alignment.BottomEnd)
              .padding(UI.Space.L)
      ) {
          UI.FloatingButton(action = ButtonAction.AI_CHAT, onClick = { Navigator.push(Place.Chat) })
      }
    }

    errorMessage?.let { message ->
        LaunchedEffect(message) {
            UI.Toast(context, message, Duration.LONG)
            errorMessage = null
        }
    }
}

/**
 * Zone group section component
 * Displays zones belonging to a specific group (or ungrouped zones if groupName is null)
 * with a header and an add button
 */
@Composable
private fun ZoneGroupSection(
    groupName: String?,
    zones: List<Zone>,
    hasConfiguredGroups: Boolean = true,
    configuredGroups: List<String>,
    editor: app.treelune.core.ui.components.GridEditor,
    onZoneClick: (Zone) -> Unit,
    onZoneLongClick: (Zone) -> Unit,
    onAddZone: () -> Unit,
    context: android.content.Context
) {
    val s = remember { Strings.`for`(context = context) }

    // The zones shown here: of this group, or for the ungrouped section of none the home screen has
    val groupZones = zones.filter { app.treelune.core.grid.ZonePositions.section(it.group, configuredGroups) == groupName }
    val key = groupName ?: ""

    // Determine section label
    val sectionLabel = when {
        groupName != null -> groupName
        hasConfiguredGroups -> s.shared("label_ungrouped")
        else -> s.shared("label_zones")
    }

    // Section header with its buttons: its title over a divider (SECTION_HEADER)
    UI.Card(type = CardType.SECTION_HEADER) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(UI.Space.M),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            UI.Text(
                text = sectionLabel,
                type = TextType.HEADING
            )

            app.treelune.core.ui.components.GridSectionButtons(key, groupZones.isNotEmpty(), editor, onAddZone)
        }
    }

    // Display zones or empty message
    if (groupZones.isEmpty()) {
        UI.Card(type = CardType.DEFAULT) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(UI.Space.L),
                contentAlignment = Alignment.Center
            ) {
                UI.Text(
                    text = if (groupName != null) {
                        s.shared("message_no_zones_in_group")
                    } else {
                        s.shared("message_no_ungrouped_zones")
                    },
                    type = TextType.CAPTION,
                    fillMaxWidth = true,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        // The zones on their grid, in edit mode when it is this section's, faded while another's is
        app.treelune.core.ui.components.Faded(editor.anyEditing && !editor.isEditing(key)) {
            app.treelune.core.ui.components.GridLayout(groupZones.map { app.treelune.core.grid.ZonePositions.tile(it) }, groupZones.map { false }, editor.gridEdit(key)) { i ->
                val zone = groupZones[i]
                UI.ZoneCard(
                    zone = zone,
                    onClick = { onZoneClick(zone) },
                    onLongClick = { onZoneLongClick(zone) }
                )
            }
        }
    }
}

/**
 * A zone from one entry of zones.list. Every field the list returns is read here, and only
 * here: three copies of this used to drop whatever they did not name, the icon among them.
 */
private fun zoneFrom(map: Map<String, Any?>): Zone = Zone(
    id = map["id"] as String,
    name = map["name"] as String,
    description = map["description"] as? String,
    icon_name = map["icon_name"] as? String,
    icon_color = map["icon_color"] as? String,
    display_mode = map["display_mode"] as String,
    grid_x = (map["grid_x"] as Number).toInt(),
    grid_y = (map["grid_y"] as Number).toInt(),
    created_at = (map["created_at"] as Number).toLong(),
    updated_at = (map["updated_at"] as Number).toLong(),
    tool_groups = (map["tool_groups"] as? List<*>)?.let { JsonUtils.toJSONArray(it).toString() },
    group = map["group"] as? String
)
