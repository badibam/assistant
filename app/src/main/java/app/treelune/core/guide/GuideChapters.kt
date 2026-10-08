package app.treelune.core.guide

import android.content.Context
import app.treelune.core.coordinator.Source
import app.treelune.core.strings.Strings
import app.treelune.core.tools.ToolTypeManager
import org.json.JSONObject

/** The two parts of the Guide's page: the journey, in its order, then the reference. */
enum class GuidePart { JOURNEY, REFERENCE }

/**
 * What a step asks: to reach a place (GO), to do something the dispatcher sees pass (DO), or to
 * read, then Next, once at its place when it has one (READ).
 */
enum class StepKind { GO, DO, READ }

/**
 * The operation a DO step waits for: its action, who must ask it (the user, or the AI when the
 * step is to have the AI do it), and params it must carry, each value an exact one or the name
 * of what an earlier step kept (`{zone}`).
 */
data class Await(val operation: String, val origin: Source, val params: Map<String, String>)

/**
 * A step of a chapter, its texts named after its chapter and its number from 1
 * (`guide_<chapter>_<n>_goal`, `_text`).
 *
 * @property target The address of the place it happens at, `{name}` standing for what an earlier
 *   step kept; null for a step read anywhere
 * @property keep What the step keeps once done, by name: the key of the operation's result (or,
 *   lacking it, of its params) whose value it keeps — the zone created, for the steps after
 */
data class GuideStep(
    val number: Int,
    val kind: StepKind,
    val target: String?,
    val await: Await?,
    val keep: Map<String, String>
) {
    /** Whether the step is about the demo: its place or what it waits for names a demo id. */
    val usesDemo: Boolean
        get() = target.orEmpty().contains(DEMO_PREFIX) || await?.params?.values?.any { it.startsWith(DEMO_PREFIX) } == true
}

/**
 * A chapter of the Guide. A chapter with steps is a tutorial, done on the app itself; one
 * without is read. A chapter with no steps and no text yet is a stub.
 *
 * @property toolType Set for a tool type's reference chapter, whose name and line are the tool's
 */
data class GuideChapter(
    val id: String,
    val part: GuidePart,
    val icon: String,
    val minutes: Int,
    val steps: List<GuideStep>,
    val toolType: String? = null
) {
    val isTutorial: Boolean get() = steps.isNotEmpty()

    /** A chapter needs the demo when one of its steps is about it: read from the steps, never declared. */
    val usesDemo: Boolean get() = steps.any { it.usesDemo }

    fun title(context: Context): String = toolType?.let { ToolTypeManager.getToolTypeName(it, context) }
        ?: Strings.`for`(context = context).shared("guide_${id}_title")

    fun intro(context: Context): String = toolType?.let { Strings.`for`(tool = it, context = context).tool("tagline") }
        ?: Strings.`for`(context = context).shared("guide_${id}_intro")

    /** The end of a tutorial; a read chapter has none. */
    fun outro(context: Context): String? = if (isTutorial) Strings.`for`(context = context).shared("guide_${id}_outro") else null

    fun goal(context: Context, step: GuideStep) = Strings.`for`(context = context).shared("guide_${id}_${step.number}_goal")
    fun text(context: Context, step: GuideStep) = Strings.`for`(context = context).shared("guide_${id}_${step.number}_text")
}

/** The ids of the demo's rows begin with this (DemoRemoval). */
const val DEMO_PREFIX = "demo-"

/**
 * The Guide's chapters, in their order: those of `assets/guide/chapters.json`, and after
 * « organize » one per tool type, from the tool type's own name and line, so that a tool type
 * comes with its chapter.
 */
object GuideChapters {

    private const val ASSET = "guide/chapters.json"

    /** The chapter after which the tool types' chapters come. */
    private const val TOOLS_AFTER = "organize"

    @Volatile private var loaded: List<GuideChapter>? = null

    fun all(context: Context): List<GuideChapter> = loaded ?: synchronized(this) {
        loaded ?: run {
            val declared = parse(context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() })
            val tools = ToolTypeManager.getAllToolTypes().keys.map { tooltype ->
                GuideChapter("tool_$tooltype", GuidePart.REFERENCE, "wrench", 3, emptyList(), toolType = tooltype)
            }
            val at = declared.indexOfFirst { it.id == TOOLS_AFTER }
            require(at >= 0) { "The Guide has no chapter '$TOOLS_AFTER' for the tools' chapters to follow" }
            (declared.take(at + 1) + tools + declared.drop(at + 1)).also { loaded = it }
        }
    }

    fun byId(context: Context, id: String): GuideChapter =
        all(context).firstOrNull { it.id == id } ?: throw IllegalArgumentException("No Guide chapter '$id'")

    /** The journey, in its order. */
    fun journey(context: Context) = all(context).filter { it.part == GuidePart.JOURNEY }

    /** The chapters a file declares; fails on anything it does not read. */
    fun parse(json: String): List<GuideChapter> {
        val chapters = JSONObject(json).getJSONArray("chapters")
        return (0 until chapters.length()).map { i ->
            val c = chapters.getJSONObject(i)
            val steps = c.getJSONArray("steps")
            GuideChapter(
                id = c.getString("id"),
                part = GuidePart.valueOf(c.getString("part").uppercase()),
                icon = c.getString("icon"),
                minutes = c.getInt("minutes"),
                steps = (0 until steps.length()).map { n ->
                    val st = steps.getJSONObject(n)
                    GuideStep(
                        number = n + 1,
                        kind = StepKind.valueOf(st.getString("kind").uppercase()),
                        target = if (st.has("target")) st.getString("target") else null,
                        await = st.optJSONObject("await")?.let { a ->
                            val params = a.optJSONObject("params")
                            Await(
                                operation = a.getString("operation"),
                                origin = Source.valueOf(a.getString("origin")),
                                params = params?.keys()?.asSequence()?.associateWith { params.getString(it) } ?: emptyMap()
                            )
                        },
                        keep = st.optJSONObject("keep")?.let { k -> k.keys().asSequence().associateWith { k.getString(it) } } ?: emptyMap()
                    ).also {
                        require((it.kind == StepKind.DO) == (it.await != null)) { "Step ${it.number} of '${c.getString("id")}': a DO step waits for an operation, and only it" }
                        require(it.kind != StepKind.GO || it.target != null) { "Step ${it.number} of '${c.getString("id")}': a GO step has a place" }
                    }
                }
            )
        }
    }
}
