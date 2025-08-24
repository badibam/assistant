package com.assistant.tools.tracking.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "tracking_data",
    indices = [Index(value = ["tool_instance_id"])]  // Performance index
)
data class TrackingData(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val tool_instance_id: String,
    
    // Immutability: copy values at record time
    val zone_name: String,           // "Health" (copied from zone)
    val tool_instance_name: String,  // "Nutrition Tracking" (copied from config)
    
    val name: String,                // "Whole bread", "Mood", etc.
    val value: String,               // JSON: {"amount": 75, "unit": "g", "type": "numeric", "raw": "75g"}
    
    val recorded_at: Long,           // When the measurement was taken
    val created_at: Long = System.currentTimeMillis(),
    val updated_at: Long = System.currentTimeMillis()
)