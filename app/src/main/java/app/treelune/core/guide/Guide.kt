package app.treelune.core.guide

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import app.treelune.core.commands.CommandStatus
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.PassedOperations
import app.treelune.core.database.entities.AppSettingCategories
import app.treelune.core.navigation.Navigator
import app.treelune.core.navigation.Place
import app.treelune.core.navigation.PlaceStack
import app.treelune.core.utils.JsonUtils
import app.treelune.core.utils.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The tutorial in progress and the Guide's progress (docs/design/user-journey.md). A GO step is
 * done when its place is on screen, a DO step when the operation it waits for passes through the
 * dispatcher (PassedOperations), asked by whom it says, a READ step by Next. Each change is
 * written to the settings (category guide) as it happens.
 *
 * Started once, by the main screen ([start]); until the progress is read, [progress] is null and
 * nothing of the Guide shows.
 */
object Guide {

    var progress by mutableStateOf<GuideProgress?>(null)
        private set

    /** A tutorial just finished, whose dialog asks what comes next. */
    var ended by mutableStateOf<GuideChapter?>(null)

    /** A tutorial just begun from its start, whose dialog says what it is and points at the band. */
    var introducing by mutableStateOf<GuideChapter?>(null)

    /** A tutorial asked for while the demo it begins in is not installed: its dialog asks. */
    var demoMissingFor by mutableStateOf<GuideChapter?>(null)

    /** The Guide's page opens once where the journey ends: after it, from a tutorial's end. */
    var openAfterJourney by mutableStateOf(false)

    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        appContext = context.applicationContext
        scope.launch {
            val result = Coordinator(appContext).processUserAction("app_config.get", mapOf("category" to AppSettingCategories.GUIDE))
            if (result.status != CommandStatus.SUCCESS) {
                // The Guide stays away rather than show a progress it could not read
                LogManager.ui("Guide: progress not read: ${result.error}", "ERROR")
                return@launch
            }
            @Suppress("UNCHECKED_CAST")
            progress = GuideProgress.fromJson(JsonUtils.toJSONObject(result.data!!["settings"] as Map<String, Any?>))
            checkPlace()
        }
        scope.launch { PassedOperations.flow.collect { passed(it) } }
        scope.launch { snapshotFlow { Navigator.stack.toList() }.collect { checkPlace() } }
    }

    /** The tutorial the band shows and its step, null when none is in progress. */
    val current: Pair<GuideChapter, GuideStep>?
        get() {
            val p = progress ?: return null
            val chapter = p.current?.let { GuideChapters.byId(appContext, it) } ?: return null
            val step = chapter.steps.getOrNull(p.of(chapter.id)?.step ?: 0) ?: return null
            return chapter to step
        }

    /** What a chapter's steps kept so far. */
    fun kept(chapter: GuideChapter): Map<String, String> = progress?.of(chapter.id)?.kept ?: emptyMap()

    /** A step's place, what earlier steps kept put in; null for a step without one, or one whose kept name is missing. */
    fun target(chapter: GuideChapter, step: GuideStep): Place? {
        val address = step.target ?: return null
        val filled = fill(address, kept(chapter)) ?: return null
        return Place.of(filled)
    }

    /** Whether the place on screen is [place]: the chat when it is open, the place under it otherwise. */
    fun isOn(place: Place): Boolean {
        val stack = Navigator.stack
        return if (place == Place.Chat) Navigator.top == Place.Chat else stack[PlaceStack.baseIndex(stack)] == place && Navigator.top != Place.Chat
    }

    /** The next tutorial of the journey not done, null once the journey is. */
    fun nextOfJourney(): GuideChapter? {
        val p = progress ?: return null
        return GuideChapters.journey(appContext).firstOrNull { p.of(it.id)?.done != true }
    }

    /**
     * Whether the Guide's book carries its mark: a tutorial hidden in progress, or the journey's
     * next tutorial waiting with none in progress.
     */
    val pending: Boolean
        get() {
            val p = progress ?: return false
            if (!p.welcomeSeen) return false
            return if (p.current != null) p.bandHidden else nextOfJourney() != null
        }

    fun welcomeDone() = update { it.copy(welcomeSeen = true) }

    /** The band hidden: the tutorial waits in the Guide. */
    fun hide() = update { it.copy(bandHidden = true) }

    /**
     * [chapter] begun where it stands, or from its start when done. A tutorial that begins in the
     * demo while it is not installed asks first ([demoMissingFor]).
     */
    fun begin(chapter: GuideChapter, demoInstalled: Boolean) {
        val p = progress ?: return
        val at = p.of(chapter.id)?.takeIf { !it.done } ?: ChapterProgress(chapter.id, 0, false, emptyMap())
        if (!demoInstalled && chapter.steps.getOrNull(at.step)?.usesDemo == true) {
            demoMissingFor = chapter
            return
        }
        update { it.with(at).copy(current = chapter.id, bandHidden = false) }
        if (at.step == 0) introducing = chapter
        checkPlace()
    }

    /** [chapter] again from its first step, what it kept forgotten. */
    fun restart(chapter: GuideChapter, demoInstalled: Boolean) {
        update { it.with(ChapterProgress(chapter.id, 0, false, emptyMap())) }
        begin(chapter, demoInstalled)
    }

    /** [chapter] begun after its steps in the demo, the demo not being installed. */
    fun skipDemo(chapter: GuideChapter) {
        val p = progress ?: return
        val from = p.of(chapter.id)?.takeIf { !it.done }?.step ?: 0
        val after = chapter.steps.indexOfFirst { it.number - 1 >= from && !it.usesDemo }
        demoMissingFor = null
        if (after < 0) return finish(chapter)
        update { it.with(ChapterProgress(chapter.id, after, false, p.of(chapter.id)?.kept ?: emptyMap())).copy(current = chapter.id, bandHidden = false) }
        if (from == 0) introducing = chapter
        checkPlace()
    }

    /** Next, on a READ step: allowed anywhere for one without a place, at its place otherwise. */
    fun next() {
        val (chapter, step) = current ?: return
        if (step.kind != StepKind.READ) return
        target(chapter, step)?.let { if (!isOn(it)) return }
        advance(chapter, emptyMap())
    }

    /** A GO step whose place is on screen is done. */
    private fun checkPlace() {
        val (chapter, step) = current ?: return
        if (step.kind != StepKind.GO) return
        val place = target(chapter, step) ?: return
        if (isOn(place)) advance(chapter, emptyMap())
    }

    /** A DO step whose operation just passed, asked by whom it says, with the params it says, is done. */
    private fun passed(op: PassedOperations.Passed) {
        val (chapter, step) = current ?: return
        val await = step.await ?: return
        if (op.action != await.operation || op.source != await.origin) return
        val kept = kept(chapter)
        val matches = await.params.all { (key, expected) ->
            val value = fill(expected, kept) ?: return@all false
            op.params[key]?.toString() == value
        }
        if (!matches) return
        // What the step keeps: from the result, or from the params lacking it
        val keeping = step.keep.mapValues { (_, key) -> (op.result[key] ?: op.params[key])?.toString() }
            .filterValues { it != null }.mapValues { it.value!! }
        advance(chapter, keeping)
    }

    private fun advance(chapter: GuideChapter, keeping: Map<String, String>) {
        val p = progress ?: return
        val at = p.of(chapter.id) ?: return
        val next = at.step + 1
        if (next >= chapter.steps.size) {
            update { it.with(at.copy(step = next, done = true, kept = at.kept + keeping)) }
            return finish(chapter)
        }
        update { it.with(at.copy(step = next, kept = at.kept + keeping)) }
        // The next step may already be done: its place on screen
        checkPlace()
    }

    private fun finish(chapter: GuideChapter) {
        update { p -> p.with((p.of(chapter.id) ?: ChapterProgress(chapter.id, chapter.steps.size, true, emptyMap())).copy(done = true)).copy(current = null, bandHidden = false) }
        ended = chapter
    }

    /** The progress changed by [change], then written to the settings. */
    private fun update(change: (GuideProgress) -> GuideProgress) {
        val p = progress ?: return
        val changed = change(p)
        if (changed == p) return
        progress = changed
        scope.launch {
            val result = Coordinator(appContext).processUserAction("app_config.set", mapOf(
                "category" to AppSettingCategories.GUIDE,
                "settings" to JsonUtils.toMap(changed.toJson())
            ))
            if (result.status != CommandStatus.SUCCESS) LogManager.ui("Guide: progress not written: ${result.error}", "ERROR")
        }
    }

    private val KEPT = Regex("\\{([a-z_]+)\\}")

    /** [text] with each `{name}` replaced by what was kept under it; null when one is missing. */
    fun fill(text: String, kept: Map<String, String>): String? {
        var missing = false
        val filled = KEPT.replace(text) { m -> kept[m.groupValues[1]] ?: run { missing = true; "" } }
        return if (missing) null else filled
    }
}
