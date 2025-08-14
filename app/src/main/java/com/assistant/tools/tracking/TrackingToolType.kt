package com.assistant.tools.tracking

import com.assistant.tools.base.ToolType
import com.assistant.tools.tracking.entities.TrackingData
import kotlin.reflect.KClass

/**
 * Tracking tool type registration
 * Declares the entities used by the tracking tool
 */
object TrackingToolType : ToolType {
    override fun getEntities(): List<KClass<*>> {
        return listOf(TrackingData::class)
    }
    
    override fun getToolTypeName(): String = "tracking"
    
    override fun getSchemaVersion(): Int = 1
}