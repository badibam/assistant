package com.assistant.core.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Data Change Notifier - singleton for notifying UI of data changes
 *
 * Emits events when data is modified (by AI or by user) so that UI screens
 * can automatically reload their data without manual refresh.
 *
 * Uses SharedFlow instead of StateFlow because:
 * - We want to emit events (actions), not maintain state
 * - Multiple screens can observe the same flow independently
 * - Events are not replayed to new subscribers (no "initial state" needed)
 *
 * Usage in UI screens:
 * ```kotlin
 * LaunchedEffect(Unit) {
 *     DataChangeNotifier.changes.collect { event ->
 *         when (event) {
 *             is DataChangeEvent.ZonesChanged -> reloadZones()
 *             is DataChangeEvent.ToolsChanged -> if (event.zoneId == currentZoneId) reloadTools()
 *             is DataChangeEvent.ToolDataChanged -> if (event.toolInstanceId == currentToolId) reloadData()
 *         }
 *     }
 * }
 * ```
 */
object DataChangeNotifier {

    // SharedFlow for data change events
    // replay = 0: no replay (events are fire-and-forget)
    // extraBufferCapacity = 10: buffer up to 10 events if no active collectors
    private val _changes = MutableSharedFlow<DataChangeEvent>(
        replay = 0,
        extraBufferCapacity = 10
    )
    val changes: SharedFlow<DataChangeEvent> = _changes.asSharedFlow()

    /**
     * Notify that zones have changed (created, updated, deleted)
     * Triggers MainScreen reload
     */
    fun notifyZonesChanged() {
        LogManager.coordination("DataChangeNotifier: zones changed", "DEBUG")
        _changes.tryEmit(DataChangeEvent.ZonesChanged)
    }

    /**
     * Notify that tools in a zone have changed (created, updated, deleted)
     * Triggers ZoneScreen reload for the specific zone
     *
     * @param zoneId ID of the zone whose tools changed
     */
    fun notifyToolsChanged(zoneId: String) {
        LogManager.coordination("DataChangeNotifier: tools changed in zone $zoneId", "DEBUG")
        _changes.tryEmit(DataChangeEvent.ToolsChanged(zoneId))
    }

    /**
     * Notify that data in a tool instance has changed (created, updated, deleted)
     * Triggers UsageScreen reload for the specific tool instance
     *
     * @param toolInstanceId ID of the tool instance whose data changed
     * @param zoneId ID of the zone containing the tool (for context)
     */
    fun notifyToolDataChanged(toolInstanceId: String, zoneId: String) {
        LogManager.coordination("DataChangeNotifier: data changed in tool $toolInstanceId (zone $zoneId)", "DEBUG")
        _changes.tryEmit(DataChangeEvent.ToolDataChanged(toolInstanceId, zoneId))
    }

    /**
     * Notify app configuration changed
     * Triggers settings screens reload
     */
    fun notifyAppConfigChanged() {
        LogManager.coordination("DataChangeNotifier: app config changed", "DEBUG")
        _changes.tryEmit(DataChangeEvent.AppConfigChanged)
    }

    /**
     * Notify that AI sessions have changed (created, updated, completed)
     * Triggers AutomationScreen reload for the specific automation or all automations
     *
     * @param automationId ID of the automation whose sessions changed, or null for all automations
     */
    fun notifyAISessionsChanged(automationId: String? = null) {
        LogManager.coordination("DataChangeNotifier: AI sessions changed for automation ${automationId ?: "all"}", "DEBUG")
        _changes.tryEmit(DataChangeEvent.AISessionsChanged(automationId))
    }
}

/**
 * Data change events
 * Sealed class for type-safe event handling
 */
sealed class DataChangeEvent {
    /**
     * Zones have changed (any CRUD operation on zones)
     */
    object ZonesChanged : DataChangeEvent()

    /**
     * Tools in a specific zone have changed
     * @param zoneId ID of the zone whose tools changed
     */
    data class ToolsChanged(val zoneId: String) : DataChangeEvent()

    /**
     * Data in a specific tool instance has changed
     * @param toolInstanceId ID of the tool instance whose data changed
     * @param zoneId ID of the zone containing the tool (for context)
     */
    data class ToolDataChanged(val toolInstanceId: String, val zoneId: String) : DataChangeEvent()

    /**
     * App configuration has changed
     */
    object AppConfigChanged : DataChangeEvent()

    /**
     * AI sessions have changed (created, updated, completed)
     * @param automationId ID of the automation whose sessions changed, or null for all automations
     */
    data class AISessionsChanged(val automationId: String?) : DataChangeEvent()
}
