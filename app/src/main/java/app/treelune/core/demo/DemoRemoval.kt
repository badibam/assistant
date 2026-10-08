package app.treelune.core.demo

/**
 * What a reinstall of the demo removes, run in one transaction in this order, the children before
 * what holds them: every row whose id carries the demo's prefix, and what lives in a demo zone or
 * tool whatever its id — an entry the user typed in a demo tool, a tool added to a demo zone. A
 * session's messages and files go with it (cascade). Plain statements, which a test runs on
 * SQLite as they are.
 */
object DemoRemoval {
    val STATEMENTS = listOf(
        """DELETE FROM tool_data WHERE id LIKE 'demo-%' OR tool_instance_id IN
            (SELECT id FROM tool_instances WHERE id LIKE 'demo-%' OR zone_id LIKE 'demo-%')""",
        "DELETE FROM tool_instances WHERE id LIKE 'demo-%' OR zone_id LIKE 'demo-%'",
        "DELETE FROM variables WHERE id LIKE 'demo-%' OR zone_id LIKE 'demo-%'",
        """DELETE FROM ai_sessions WHERE id LIKE 'demo-%' OR automation_id IN
            (SELECT id FROM automations WHERE id LIKE 'demo-%' OR zone_id LIKE 'demo-%')""",
        "DELETE FROM automations WHERE id LIKE 'demo-%' OR zone_id LIKE 'demo-%'",
        "DELETE FROM zones WHERE id LIKE 'demo-%'"
    )
}
