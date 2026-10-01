package com.assistant.core.demo

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * A reinstall of the demo removes all of it — its own rows, and what the user put in a demo zone
 * or tool — and touches nothing else: the statements of DemoRemoval, run on SQLite in their order
 * over the columns they read.
 */
class DemoRemovalTest {

    private lateinit var db: Connection

    @Before
    fun setUp() {
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
        listOf(
            "CREATE TABLE zones (id TEXT)",
            "CREATE TABLE tool_instances (id TEXT, zone_id TEXT)",
            "CREATE TABLE tool_data (id TEXT, tool_instance_id TEXT)",
            "CREATE TABLE variables (id TEXT, zone_id TEXT)",
            "CREATE TABLE automations (id TEXT, zone_id TEXT)",
            "CREATE TABLE ai_sessions (id TEXT, automation_id TEXT)"
        ).forEach { db.createStatement().execute(it) }

        // The user's own, outside the demo
        insert("zones", "zone")
        insert("tool_instances", "tool", "zone")
        insert("tool_data", "entry", "tool")
        insert("variables", "variable", "zone")
        insert("automations", "automation", "zone")
        insert("ai_sessions", "session", "automation")
        insert("ai_sessions", "chat", null)

        // The demo's own
        insert("zones", "demo-zone")
        insert("tool_instances", "demo-tool", "demo-zone")
        insert("tool_data", "demo-entry", "demo-tool")
        insert("variables", "demo-variable", "demo-zone")
        insert("automations", "demo-automation", "demo-zone")
        insert("ai_sessions", "demo-session", "demo-automation")

        // What the user put in the demo
        insert("tool_data", "typed", "demo-tool")
        insert("tool_instances", "added", "demo-zone")
        insert("tool_data", "typed-in-added", "added")
        insert("variables", "added-variable", "demo-zone")
        insert("automations", "added-automation", "demo-zone")
        insert("ai_sessions", "added-session", "added-automation")
    }

    @After
    fun tearDown() = db.close()

    private fun insert(table: String, id: String, parent: String? = null) {
        val sql = if (table == "zones") "INSERT INTO zones VALUES (?)" else "INSERT INTO $table VALUES (?, ?)"
        db.prepareStatement(sql).apply {
            setString(1, id)
            if (table != "zones") setString(2, parent)
            execute()
        }
    }

    private fun ids(table: String): Set<String> =
        db.createStatement().executeQuery("SELECT id FROM $table").use { rows ->
            buildSet { while (rows.next()) add(rows.getString(1)) }
        }

    @Test
    fun `a removal takes the demo and what was put in it, and leaves everything else`() {
        DemoRemoval.STATEMENTS.forEach { db.createStatement().execute(it) }
        assertEquals(setOf("zone"), ids("zones"))
        assertEquals(setOf("tool"), ids("tool_instances"))
        assertEquals(setOf("entry"), ids("tool_data"))
        assertEquals(setOf("variable"), ids("variables"))
        assertEquals(setOf("automation"), ids("automations"))
        assertEquals(setOf("session", "chat"), ids("ai_sessions"))
    }
}
