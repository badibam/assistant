package com.assistant.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "zones")
data class Zone(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String? = null,
    val color: String? = null,
    val active: Boolean = true,
    val order_index: Int = 0,
    val created_at: Long = System.currentTimeMillis(),
    val updated_at: Long = System.currentTimeMillis(),

    /**
     * Tool groups defined at zone level (JSON array of group names)
     * Example: ["Santé", "Productivité", "Loisirs"]
     * Tool instances and automations can be assigned to these groups
     */
    val tool_groups: String? = null
)