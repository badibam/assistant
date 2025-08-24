package com.assistant.core.state

import android.content.Context
import com.assistant.core.database.AppDatabase
import com.assistant.core.database.entities.Zone
import com.assistant.core.database.entities.ToolInstance
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Global application state - Single source of truth for all UI data
 * Provides reactive StateFlow for all common entities
 */
class AppState(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    /**
     * All zones in the application
     */
    val zones: StateFlow<List<Zone>> = database.zoneDao()
        .getAllZones()
        .stateIn(
            scope = scope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    /**
     * All tool instances grouped by zone ID
     */
    val toolInstancesByZone: StateFlow<Map<String, List<ToolInstance>>> = database.toolInstanceDao()
        .getAllToolInstances()
        .map { instances ->
            instances.groupBy { it.zone_id }
        }
        .stateIn(
            scope = scope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )
    
    /**
     * Get tool instances for a specific zone
     */
    fun getToolInstancesForZone(zoneId: String): StateFlow<List<ToolInstance>> {
        return toolInstancesByZone
            .map { it[zoneId] ?: emptyList() }
            .stateIn(
                scope = scope,
                started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    }
    
    /**
     * Get a specific zone by ID
     */
    fun getZone(zoneId: String): StateFlow<Zone?> {
        return zones
            .map { zoneList -> zoneList.find { it.id == zoneId } }
            .stateIn(
                scope = scope,
                started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
                initialValue = null
            )
    }
    
    /**
     * Get a specific tool instance by ID
     */
    fun getToolInstance(instanceId: String): StateFlow<ToolInstance?> {
        return toolInstancesByZone
            .map { zoneMap ->
                zoneMap.values.flatten().find { it.id == instanceId }
            }
            .stateIn(
                scope = scope,
                started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
                initialValue = null
            )
    }
}