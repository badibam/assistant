package com.assistant.core.demo

import androidx.room.Dao
import androidx.room.Query

/**
 * What a reinstall removes (docs/design/demo.md): every row whose id carries the demo's prefix,
 * and what lives in a demo zone or tool whatever its id — an entry the user typed in a demo
 * tool, a tool added to a demo zone. A session's messages and files go with it (cascade).
 * Called in one transaction, in this order: the children before what holds them.
 */
@Dao
interface DemoDao {

    @Query("""DELETE FROM tool_data WHERE id LIKE 'demo-%' OR tool_instance_id IN
        (SELECT id FROM tool_instances WHERE id LIKE 'demo-%' OR zone_id LIKE 'demo-%')""")
    suspend fun deleteEntries()

    @Query("DELETE FROM tool_instances WHERE id LIKE 'demo-%' OR zone_id LIKE 'demo-%'")
    suspend fun deleteTools()

    @Query("DELETE FROM variables WHERE id LIKE 'demo-%' OR zone_id LIKE 'demo-%'")
    suspend fun deleteVariables()

    @Query("""DELETE FROM ai_sessions WHERE id LIKE 'demo-%' OR automation_id IN
        (SELECT id FROM automations WHERE id LIKE 'demo-%' OR zone_id LIKE 'demo-%')""")
    suspend fun deleteSessions()

    @Query("DELETE FROM automations WHERE id LIKE 'demo-%' OR zone_id LIKE 'demo-%'")
    suspend fun deleteAutomations()

    @Query("DELETE FROM zones WHERE id LIKE 'demo-%'")
    suspend fun deleteZones()

    @Query("SELECT COUNT(*) FROM zones WHERE id LIKE 'demo-%'")
    suspend fun countZones(): Int
}
