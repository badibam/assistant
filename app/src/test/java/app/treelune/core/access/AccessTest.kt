package app.treelune.core.access

import app.treelune.core.selection.Reference
import app.treelune.core.selection.ReferenceKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * An automation's access mask (docs/design/validation.md): what each level gives, what a zone
 * gives its tools, what each operation needs, and the refusal of what goes beyond.
 */
class AccessTest {

    private fun zone(id: String, level: AccessLevel) = AccessGrant(Reference(ReferenceKind.ZONE, id), level)
    private fun tool(id: String, level: AccessLevel) = AccessGrant(Reference(ReferenceKind.TOOL_INSTANCE, id), level)

    /** Health holds weight and meals; the variable kcal lives in it; garden is elsewhere. */
    private val lookups = object : AccessLookups {
        override suspend fun toolZone(toolId: String) = mapOf("weight" to "health", "meals" to "health", "seeds" to "garden")[toolId]
        override suspend fun variableZone(idOrName: String, byName: Boolean) = if (idOrName in setOf("kcal", "v-kcal")) "health" else "garden"
        override suspend fun zoneName(zoneId: String) = zoneId
        override suspend fun toolName(toolId: String) = toolId
        override fun isToolType(resource: String) = resource == "goal"
    }

    /** The texts a refusal is made of, each with the arguments its format takes. */
    private fun text(key: String) = when (key) {
        "access_refused" -> "%1\$s %2\$s %3\$s"
        "access_refused_unknown" -> "%1\$s %2\$s"
        "access_target_zone", "access_target_tool" -> "%1\$s"
        else -> key
    }

    private fun refusal(mask: AccessMask, resource: String, operation: String, params: Map<String, Any?>) = runBlocking {
        AccessRules.refusal(mask, "Weekly", AccessRules.reach(resource, operation, params, lookups::isToolType), lookups, ::text)
    }

    @Test
    fun anEmptyMaskReachesEverything() {
        val open = AccessMask()
        assertEquals(AccessLevel.FULL, open.zone("anything"))
        assertNull(refusal(open, "zones", "create", mapOf("name" to "New")))
        assertNull(refusal(open, "app_config", "set", mapOf("category" to "main_screen")))
    }

    @Test
    fun aZoneReadGivesItsToolsToReadAndAZoneUsedGivesThemFull() {
        assertEquals(AccessLevel.READ, AccessMask(listOf(zone("health", AccessLevel.READ))).tool("weight", "health"))
        assertEquals(AccessLevel.FULL, AccessMask(listOf(zone("health", AccessLevel.USE))).tool("weight", "health"))
        // A tool's own grant counts when it is higher
        assertEquals(AccessLevel.USE, AccessMask(listOf(zone("health", AccessLevel.READ), tool("weight", AccessLevel.USE))).tool("weight", "health"))
        assertNull(AccessMask(listOf(zone("health", AccessLevel.FULL))).tool("seeds", "garden"))
    }

    @Test
    fun aToolReadMayBeReadButNotWritten() {
        val mask = AccessMask(listOf(tool("weight", AccessLevel.READ)))
        assertNull(refusal(mask, "tool_data", "get", mapOf("id" to "weight")))
        assertNotNull(refusal(mask, "tool_data", "batch_create", mapOf("tool_instance_id" to "weight")))
        assertNotNull(refusal(mask, "tool_data", "get", mapOf("id" to "meals")))
        // A tool type's own operation writes the tool's entries
        assertNotNull(refusal(mask, "goal", "validate", mapOf("tool_instance_id" to "weight")))
    }

    @Test
    fun aZoneUsedTakesNewToolsAndChangesItsToolsButNotItsOwnSettings() {
        val mask = AccessMask(listOf(zone("health", AccessLevel.USE)))
        assertNull(refusal(mask, "tools", "create", mapOf("zone_id" to "health")))
        assertNull(refusal(mask, "tools", "update", mapOf("tool_instance_id" to "weight")))
        assertNull(refusal(mask, "variables", "update", mapOf("variable_id" to "v-kcal")))
        assertNull(refusal(mask, "readings", "read", mapOf("variable" to "kcal")))
        assertNotNull(refusal(mask, "zones", "update", mapOf("zone_id" to "health")))
        // A tool moved into a zone out of reach
        assertNotNull(refusal(mask, "tools", "update", mapOf("tool_instance_id" to "weight", "zone_id" to "garden")))
    }

    @Test
    fun aFilledMaskKeepsTheAppAndWhatItCannotPlaceOutOfReach() {
        val mask = AccessMask(listOf(zone("health", AccessLevel.FULL)))
        assertNotNull(refusal(mask, "zones", "create", mapOf("name" to "New")))
        assertNotNull(refusal(mask, "app_config", "set", mapOf("category" to "main_screen")))
        assertNotNull(refusal(mask, "automations", "create", emptyMap()))
        // The lists, the schemas and the date stay free
        assertNull(refusal(mask, "zones", "list", emptyMap()))
        assertNull(refusal(mask, "schemas", "get", mapOf("id" to "x")))
        assertNull(refusal(mask, "app_config", "get_current_datetime", emptyMap()))
    }

    @Test
    fun aMaskThatReachesNothingRefusesEverythingButWhatIsFree() {
        assertNotNull(refusal(AccessMask.NOTHING, "tool_data", "get", mapOf("id" to "weight")))
        assertNull(refusal(AccessMask.NOTHING, "zones", "list", emptyMap()))
    }

    @Test
    fun theStoredFormReadsBack() {
        val mask = AccessMask(listOf(zone("health", AccessLevel.USE), tool("seeds", AccessLevel.READ)))
        assertEquals(mask, AccessMask.fromJson(mask.toJson().toString()))
        val stored = AccessMask(listOf(zone("health", AccessLevel.USE))).toJson().getJSONObject(0)
        assertEquals("use", stored.getString("level"))
        assertEquals("ZONE", stored.getJSONObject("target").getString("kind"))
        assertEquals("health", stored.getJSONObject("target").getString("id"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun anAccessIsGivenOnAZoneOrAToolAlone() {
        AccessGrant(Reference(ReferenceKind.VARIABLE, "kcal"), AccessLevel.READ)
    }
}
