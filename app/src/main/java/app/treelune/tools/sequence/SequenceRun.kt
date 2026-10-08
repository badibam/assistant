package app.treelune.tools.sequence

import org.json.JSONArray
import org.json.JSONObject

/** How a step was left: by a gesture of the user, or by its timer running out. */
enum class LeftBy { GESTURE, TIMER }

/** What became of a step left: done, or skipped. */
enum class Outcome(val key: String) { DONE("done"), SKIPPED("skipped") }

/** A step left, with where its clock stood, which undoing that passage gives back. */
data class Left(val outcome: Outcome, val by: LeftBy, val stepStart: Long, val extended: Long)

/**
 * A session running (docs/design/sequence-tool.md), as its entry keeps it: every time is an
 * instant recorded, never a counter, so a session read again after the app was killed stands
 * right.
 *
 * Its times are read on the session's clock: the time since it started, the pauses left out —
 * the clock stops while it is paused. A pause then moves nothing, and undoing a passage gives the
 * step back its own start: it goes on as if the gesture had not happened.
 *
 * The current step is the one after the steps left: [left] holds one passage per step gone
 * through, in order, which is what `back` undoes.
 *
 * @property stepStart When the current step's clock started, on the session's clock; during the
 *   lead-in before the first step, a moment still to come
 * @property extended What `extend` added to the current step's timer
 * @property held The current step came back by `back` after its timer ran out: it stays at zero,
 *   waiting for « Done » or `restart`, instead of passing again at once
 * @property pausedAt When the session was paused, null while it runs
 * @property paused The pauses of the session before the current one
 */
data class SequenceRun(
    val steps: List<PlannedStep>,
    val startedAt: Long,
    val left: List<Left>,
    val stepStart: Long,
    val extended: Long,
    val held: Boolean,
    val pausedAt: Long?,
    val paused: Long
) {
    val position: Int get() = left.size
    val current: PlannedStep get() = steps[position]
    val next: PlannedStep? get() = steps.getOrNull(position + 1)
    val isPaused: Boolean get() = pausedAt != null

    /** The session's clock at [now]: the instant less the pauses, stopped while paused. */
    fun clock(now: Long): Long = (pausedAt ?: now) - paused

    /** The pauses at [now], the current one included. */
    fun pauses(now: Long): Long = paused + (pausedAt?.let { now - it } ?: 0L)

    /** How long the current step has run at [now]; below zero during the lead-in. */
    fun elapsed(now: Long): Long = if (held) timerLength() else clock(now) - stepStart

    /** The current step's timer with its extensions; zero for a step ended by hand. */
    fun timerLength(): Long = (current.duration ?: 0L) + extended

    /** What the current step's timer has left at [now], below zero in overtime; null without a timer. */
    fun remaining(now: Long): Long? = if (current.end.timed) timerLength() - elapsed(now) else null

    /** The instant the current step's timer runs out, null without one, held, or while paused. */
    fun timerEnd(): Long? = if (current.end.timed && !held && pausedAt == null) stepStart + timerLength() + paused else null

    /** The instant the current step starts, null once started or while paused. */
    fun stepBegins(now: Long): Long? = if (pausedAt == null && stepStart > clock(now)) stepStart + paused else null

    fun count(outcome: Outcome): Int = left.count { it.outcome == outcome }

    fun toJson(): JSONObject = JSONObject()
        .put("steps", SequencePlan.toJson(steps))
        .put("started_at", startedAt)
        .put("left", JSONArray().apply {
            left.forEach {
                put(JSONObject().put("outcome", it.outcome.key).put("by", it.by.name.lowercase())
                    .put("step_start", it.stepStart).put("extended", it.extended))
            }
        })
        .put("step_start", stepStart)
        .put("extended", extended)
        .put("held", held)
        .put("paused_at", pausedAt ?: JSONObject.NULL)
        .put("paused", paused)

    companion object {

        /** The wait between « Start » and the first step. */
        const val LEAD_IN = 5_000L

        /** What `extend` adds to a timer. */
        const val EXTENSION = 15_000L

        /** A session starting at [now] on [steps], its first step after the lead-in. */
        fun start(steps: List<PlannedStep>, now: Long): SequenceRun {
            require(steps.isNotEmpty()) { "a session without steps" }
            return SequenceRun(steps, now, emptyList(), now + LEAD_IN, 0L, false, null, 0L)
        }

        fun fromJson(o: JSONObject): SequenceRun {
            val left = o.getJSONArray("left")
            return SequenceRun(
                steps = SequencePlan.fromJson(o.getJSONArray("steps")),
                startedAt = o.getLong("started_at"),
                left = (0 until left.length()).map { i ->
                    left.getJSONObject(i).let {
                        Left(Outcome.entries.first { e -> e.key == it.getString("outcome") }, LeftBy.valueOf(it.getString("by").uppercase()),
                            it.getLong("step_start"), it.getLong("extended"))
                    }
                },
                stepStart = o.getLong("step_start"),
                extended = o.getLong("extended"),
                held = o.getBoolean("held"),
                pausedAt = if (o.isNull("paused_at")) null else o.getLong("paused_at"),
                paused = o.getLong("paused")
            )
        }
    }
}

/** What a gesture or the clock makes of a run: the run going on, or the session over. */
sealed class Step {
    abstract val run: SequenceRun

    data class Running(override val run: SequenceRun) : Step()

    /**
     * @property endedAt The instant it ended
     * @property completed Every step gone through, rather than stopped on the way
     */
    data class Finished(override val run: SequenceRun, val endedAt: Long, val completed: Boolean) : Step() {
        /** Its length, the pauses left out. */
        val duration: Long get() = run.clock(endedAt) - run.startedAt
        val pauses: Long get() = run.pauses(endedAt)
        val notDone: Int get() = run.steps.size - run.left.size
    }
}

/**
 * What each gesture does to a run (docs/design/sequence-tool.md, « Les opérations »). Pure: the
 * service reads the run, applies one of these, and writes the result.
 */
object SequenceGestures {

    fun done(run: SequenceRun, now: Long): Step = leave(run, Outcome.DONE, LeftBy.GESTURE, now)

    fun skip(run: SequenceRun, now: Long): Step = leave(run, Outcome.SKIPPED, LeftBy.GESTURE, now)

    /**
     * The timer of a timed step ran out at its end, which is when the next step starts, however
     * late this is called: a pass missed while the phone slept does not shift what follows.
     * Null when the current step's timer has not run out at [now], or does not pass by itself.
     */
    fun timerRanOut(run: SequenceRun, now: Long): Step? {
        if (run.current.end != StepEnd.TIMED) return null
        val end = run.timerEnd() ?: return null
        if (end > now) return null
        return leave(run, Outcome.DONE, LeftBy.TIMER, end)
    }

    /** Every timer that ran out by [now], one step after the other, as after a long sleep. */
    fun catchUp(run: SequenceRun, now: Long): Step {
        var step: Step = Step.Running(run)
        while (step is Step.Running) step = timerRanOut(step.run, now) ?: return step
        return step
    }

    /**
     * The last passage undone, as if it had not happened: the previous step back with its own
     * start, the time since counting for it. A step its timer had ended comes back held at zero.
     * Null when no step was left yet.
     */
    fun back(run: SequenceRun): SequenceRun? {
        val last = run.left.lastOrNull() ?: return null
        return run.copy(left = run.left.dropLast(1), stepStart = last.stepStart, extended = last.extended, held = last.by == LeftBy.TIMER)
    }

    /** The current step from its beginning. */
    fun restart(run: SequenceRun, now: Long): SequenceRun = run.copy(stepStart = run.clock(now), extended = 0L, held = false)

    fun pause(run: SequenceRun, now: Long): SequenceRun = if (run.isPaused) run else run.copy(pausedAt = now)

    fun resume(run: SequenceRun, now: Long): SequenceRun {
        val since = run.pausedAt ?: return run
        return run.copy(pausedAt = null, paused = run.paused + (now - since))
    }

    /**
     * [SequenceRun.EXTENSION] more on the current step's timer; a held step starts again with that
     * alone left. Null for a step without a timer.
     */
    fun extend(run: SequenceRun, now: Long): SequenceRun? {
        if (!run.current.end.timed) return null
        val extended = run.extended + SequenceRun.EXTENSION
        return if (run.held) run.copy(stepStart = run.clock(now) - run.timerLength(), extended = extended, held = false)
        else run.copy(extended = extended)
    }

    fun stop(run: SequenceRun, now: Long): Step.Finished = Step.Finished(run, now, completed = false)

    /**
     * Back after the app was stopped mid-session: the current step starts again, and the time
     * since [lastWritten], the session's last recorded moment, counts as a pause.
     */
    fun resumeInterrupted(run: SequenceRun, lastWritten: Long, now: Long): SequenceRun {
        if (run.isPaused) return run
        val gapped = run.copy(paused = run.paused + (now - lastWritten).coerceAtLeast(0L))
        return gapped.copy(stepStart = gapped.clock(now), extended = 0L, held = false)
    }

    private fun leave(run: SequenceRun, outcome: Outcome, by: LeftBy, at: Long): Step {
        val left = run.left + Left(outcome, by, run.stepStart, run.extended)
        val next = run.copy(left = left, stepStart = run.clock(at), extended = 0L, held = false)
        return if (left.size == run.steps.size) Step.Finished(next, at, completed = true) else Step.Running(next)
    }
}

/** The beeps of a run, at the instants they sound (docs/design/sequence-tool.md, « Les signaux »). */
object SequenceSignals {

    /** The countdown: 3, 2 and 1 second before a timed step's end, or before the first step. */
    val COUNTDOWN = listOf(3_000L, 2_000L, 1_000L)

    /**
     * The instants of the current step's beeps, from the start of the lead-in or of the step:
     * only the seconds it has, none while paused or held.
     */
    fun beeps(run: SequenceRun, now: Long): List<Long> {
        val leadIn = run.stepBegins(now)?.takeIf { run.position == 0 }
            ?.let { begins -> COUNTDOWN.map { begins - it }.filter { it >= run.startedAt } } ?: emptyList()
        val countdown = run.timerEnd()?.let { end -> COUNTDOWN.map { end - it }.filter { it > run.stepStart + run.paused } } ?: emptyList()
        return leadIn + countdown
    }
}
