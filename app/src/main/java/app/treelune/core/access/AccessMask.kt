package app.treelune.core.access

import app.treelune.core.selection.Reference
import app.treelune.core.selection.ReferenceKind
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * How far an AI acting without the user may go in a zone or a tool (docs/design/validation.md,
 * « Le masque d'accès »), each level holding the one before it.
 */
enum class AccessLevel(val key: String) {
    /** The zone or the tool, its entries, a zone's variables, read */
    READ("read"),
    /** And written: a tool's entries; a zone's tools and variables, created, changed, deleted */
    USE("use"),
    /** And its own settings: a zone's name, icon, tool groups, deletion; a tool's config, deletion */
    FULL("full");

    companion object {
        fun of(key: String): AccessLevel = entries.firstOrNull { it.key == key } ?: throw IllegalArgumentException("No access level '$key'")
    }
}

/**
 * A zone or a tool (the Chose brick, Reference, a zone or a tool instance with its id), and how
 * far one may go in it.
 */
data class AccessGrant(val target: Reference, val level: AccessLevel) {
    init {
        require(target.kind in KINDS && target.id != null) { "An access is given on a zone or a tool: ${target.toJson()}" }
    }

    companion object {
        val KINDS = setOf(ReferenceKind.ZONE, ReferenceKind.TOOL_INSTANCE)
    }
}

/**
 * The access of an AI acting without the user: an automation's, set in its config. Empty, it may
 * go anywhere (the default); otherwise it reaches what is listed, at the level given, and nothing
 * else -- creating a zone or changing the home screen's groups included.
 *
 * A tool takes the higher of its own grant and what its zone gives it: a zone read gives its
 * tools to read, a zone used or full gives them full, since who may delete and create the tools
 * of a zone may change them.
 */
data class AccessMask(val grants: List<AccessGrant> = emptyList(), private val nothing: Boolean = false) {

    /** Whether nothing is listed: everything is reached. */
    val open: Boolean get() = grants.isEmpty() && !nothing

    /** How far one may go in zone [zoneId], null when not at all. */
    fun zone(zoneId: String): AccessLevel? =
        if (open) AccessLevel.FULL else if (nothing) null else grants.firstOrNull { it.target.kind == ReferenceKind.ZONE && it.target.id == zoneId }?.level

    /** How far one may go in tool [toolId] of zone [zoneId], null when not at all. */
    fun tool(toolId: String, zoneId: String): AccessLevel? {
        if (open) return AccessLevel.FULL
        val own = grants.firstOrNull { it.target.kind == ReferenceKind.TOOL_INSTANCE && it.target.id == toolId }?.level
        val fromZone = when (zone(zoneId)) {
            null -> null
            AccessLevel.READ -> AccessLevel.READ
            AccessLevel.USE, AccessLevel.FULL -> AccessLevel.FULL
        }
        return listOfNotNull(own, fromZone).maxOrNull()
    }

    /** The stored form: a list of `{"target": {"kind", "id"}, "level"}`. */
    fun toJson(): JSONArray = JSONArray().apply {
        grants.forEach { put(JSONObject().put("target", it.target.toJson()).put("level", it.level.key)) }
    }

    companion object {
        /** A mask reaching nothing: what is held to the mask of an automation that cannot be read. */
        val NOTHING = AccessMask(nothing = true)

        /** The mask stored as [json]; one that does not read throws. */
        fun fromJson(json: String): AccessMask {
            val array = JSONArray(json)
            return AccessMask((0 until array.length()).map { i ->
                val grant = array.getJSONObject(i)
                AccessGrant(Reference.fromJson(grant.getJSONObject("target")), AccessLevel.of(grant.getString("level")))
            })
        }
    }
}

/**
 * The mask the operation running is held to, carried by the coroutine running it, as its origin is
 * (Origin): set around the commands of an automation's AI, read by the coordinator.
 *
 * @property holder What the mask belongs to, named in a refusal (the automation's name)
 */
class AccessScope(val mask: AccessMask, val holder: String) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<AccessScope>
}
