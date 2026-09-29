package com.assistant.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A variable of the core: a named value any reader reads (docs/DATA.md, « Variables »). It lives
 * in a zone, deleted with it, and reads any zone. Nothing of its value is stored: it is computed
 * each time it is read.
 *
 * @property name Unique in the app: a formula and the AI name it alone
 * @property group The zone's group it sits in, as a tool or an automation; null outside any
 * @property orderIndex Its line in the group's variables
 * @property definitionJson `{"kind": "CONSTANT", "value", "field"}` or
 *   `{"kind": "FORMULA", "formula", "terms", "field"}`, checked by the variables service
 */
@Entity(
    tableName = "variables",
    foreignKeys = [
        ForeignKey(entity = Zone::class, parentColumns = ["id"], childColumns = ["zone_id"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [
        Index(value = ["zone_id"]),
        Index(value = ["name"], unique = true)
    ]
)
data class VariableEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "zone_id") val zoneId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "group") val group: String?,
    @ColumnInfo(name = "order_index") val orderIndex: Int,
    @ColumnInfo(name = "definition_json") val definitionJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
