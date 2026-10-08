package app.treelune.core.services

import app.treelune.core.database.entities.ToolDataEntity
import app.treelune.core.utils.JsonUtils
import org.json.JSONObject

/**
 * A stored entry as the service hands it out, the same for a list and for a single entry.
 *
 * data, extra and state leave as objects: the string form belongs to the database, not to the
 * callers. Timestamps stay in milliseconds, as stored; the ISO the model reads is produced
 * where the model is spoken to, in CommandExecutor.
 *
 * The single read used to build its own map and hand data out as the stored string, so the
 * journal, reading data["content"], opened every existing entry empty.
 */
internal object ToolDataEntries {

    fun toMap(entity: ToolDataEntity): Map<String, Any?> = mapOf(
        "id" to entity.id,
        "tool_instance_id" to entity.toolInstanceId,
        "tooltype" to entity.tooltype,
        "timestamp" to entity.timestamp,
        "name" to entity.name,
        "data" to JsonUtils.toMap(JSONObject(entity.data)),
        "extra" to entity.extra?.let { JsonUtils.toMap(JSONObject(it)) },
        "state" to entity.state?.let { JsonUtils.toMap(JSONObject(it)) },
        "created_at" to entity.createdAt,
        "updated_at" to entity.updatedAt
    )
}
