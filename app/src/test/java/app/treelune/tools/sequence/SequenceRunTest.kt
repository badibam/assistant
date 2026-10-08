package app.treelune.tools.sequence

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the session tool (docs/design/sequence-tool.md): the run unrolled from its config, blocks
 * repeated and a step skipped at its block's last round; what each gesture does to the clock,
 * `back` undoing a passage as if it had not happened, a pause leaving the times untouched; timers
 * caught up after a sleep without shifting what follows; the countdown's beeps.
 */
class SequenceRunTest {

    private val intervals = JSONObject("""
        {"steps": [
          {"kind": "step", "name": "Warm-up", "end": "manual"},
          {"kind": "block", "name": "Intervals", "repeat": 3, "steps": [
            {"kind": "step", "name": "Run", "end": "timed", "duration": 30000},
            {"kind": "step", "name": "Rest", "end": "timed", "duration": 90000, "skip_last_round": true}
          ]},
          {"kind": "step", "name": "Cool-down", "end": "timed_then_manual", "duration": 300000}
        ]}
    """)

    private fun timed(name: String, duration: Long) = PlannedStep(name, null, StepEnd.TIMED, duration, emptyList())
    private fun manual(name: String) = PlannedStep(name, null, StepEnd.MANUAL, null, emptyList())

    /** A run whose first step started at 0, the lead-in over. */
    private fun running(vararg steps: PlannedStep) = SequenceRun.start(steps.toList(), -SequenceRun.LEAD_IN)

    private fun Step.running() = (this as Step.Running).run

    @Test
    fun `a block repeats its steps, the one marked skipped at its last round`() {
        val names = SequencePlan.unroll(intervals).map { it.name }
        assertEquals(listOf("Warm-up", "Run", "Rest", "Run", "Rest", "Run", "Cool-down"), names)
        val third = SequencePlan.unroll(intervals)[5]
        assertEquals(listOf(Round("Intervals", 3, 3)), third.rounds)
    }

    @Test
    fun `a block in a block keeps both rounds, its steps stored without their kind`() {
        val config = JSONObject("""
            {"steps": [{"kind": "block", "name": "Circuit", "repeat": 2, "steps": [
              {"kind": "block", "name": "Set", "repeat": 2, "steps": [{"name": "Squat", "end": "manual"}]}
            ]}]}
        """)
        val steps = SequencePlan.unroll(config)
        assertEquals(4, steps.size)
        assertEquals(listOf(Round("Circuit", 2, 2), Round("Set", 1, 2)), steps[2].rounds)
    }

    @Test
    fun `the run read back from its entry is the run written`() {
        val run = SequenceGestures.pause(SequenceGestures.done(SequenceRun.start(SequencePlan.unroll(intervals), 1000), 9000).running(), 12000)
        assertEquals(run, SequenceRun.fromJson(JSONObject(run.toJson().toString())))
    }

    @Test
    fun `the first step starts after the lead-in, its countdown beeping before`() {
        val run = SequenceRun.start(listOf(timed("Run", 30000)), 0)
        assertEquals(-SequenceRun.LEAD_IN, run.elapsed(0))
        assertEquals(listOf(2000L, 3000L, 4000L, 32000L, 33000L, 34000L), SequenceSignals.beeps(run, 0))
    }

    @Test
    fun `a step shorter than the countdown beeps only the seconds it has`() {
        val run = running(timed("Jump", 2000))
        assertEquals(listOf(1000L), SequenceSignals.beeps(run, 0))
    }

    @Test
    fun `a timer ended passes to the next step at its end, however late`() {
        val run = running(timed("Run", 30000), timed("Rest", 90000), manual("Stretch"))
        val after = SequenceGestures.catchUp(run, 200_000).running()
        assertEquals(2, after.position)
        // Rest started at 30 s and ended at 120 s: Stretch has run 80 s at 200 s
        assertEquals(80_000L, after.elapsed(200_000))
        assertEquals(LeftBy.TIMER, after.left.last().by)
    }

    @Test
    fun `a pause stops the clock, and the timer ends that much later`() {
        val run = running(timed("Run", 30000), manual("Next"))
        val paused = SequenceGestures.pause(run, 10_000)
        assertEquals(10_000L, paused.elapsed(50_000))
        assertNull(SequenceGestures.timerRanOut(paused, 50_000))
        val resumed = SequenceGestures.resume(paused, 50_000)
        assertEquals(70_000L, resumed.timerEnd())
        assertEquals(40_000L, resumed.paused)
    }

    @Test
    fun `back undoes a done touched too early, the step going on as if it had not been`() {
        val run = running(manual("Squat"), timed("Rest", 90000))
        val left = SequenceGestures.done(run, 20_000).running()
        val back = SequenceGestures.back(left)!!
        assertEquals(0, back.position)
        assertEquals(25_000L, back.elapsed(25_000))
    }

    @Test
    fun `back leaves out a pause taken since the passage`() {
        val run = running(manual("Squat"), manual("Lunge"))
        var next = SequenceGestures.done(run, 20_000).running()
        next = SequenceGestures.resume(SequenceGestures.pause(next, 21_000), 31_000)
        // 42 s since it started, 10 of them paused
        assertEquals(32_000L, SequenceGestures.back(next)!!.elapsed(42_000))
    }

    @Test
    fun `back to a step its timer ended holds it at zero until done or restarted`() {
        val run = running(timed("Run", 30000), manual("Next"))
        val passed = SequenceGestures.timerRanOut(run, 35_000)!!.running()
        val back = SequenceGestures.back(passed)!!
        assertTrue(back.held)
        assertEquals(0L, back.remaining(60_000))
        assertNull(SequenceGestures.timerRanOut(back, 60_000))
        assertEquals(30_000L, SequenceGestures.restart(back, 60_000).remaining(60_000))
        assertEquals(SequenceRun.EXTENSION, SequenceGestures.extend(back, 60_000)!!.remaining(60_000))
    }

    @Test
    fun `back on the first step has nothing to undo`() {
        assertNull(SequenceGestures.back(running(manual("Only"))))
    }

    @Test
    fun `a timer then by hand rings and waits in overtime`() {
        val run = running(PlannedStep("Bake", null, StepEnd.TIMED_THEN_MANUAL, 10_000, emptyList()), manual("Serve"))
        assertNull(SequenceGestures.timerRanOut(run, 20_000))
        assertEquals(-10_000L, run.remaining(20_000))
    }

    @Test
    fun `the last step done ends the session, a stop counts what was not reached`() {
        val run = running(manual("A"), manual("B"), manual("C"))
        val one = SequenceGestures.skip(run, 10_000).running()
        val stopped = SequenceGestures.stop(one, 15_000)
        assertFalse(stopped.completed)
        assertEquals(2, stopped.notDone)
        assertEquals(1, stopped.run.count(Outcome.SKIPPED))
        val two = SequenceGestures.done(SequenceGestures.done(one, 20_000).running(), 30_000)
        assertTrue((two as Step.Finished).completed)
        assertEquals(30_000L + SequenceRun.LEAD_IN, two.duration)
    }

    @Test
    fun `a session interrupted starts its step again, the gap counted as a pause`() {
        val run = running(manual("A"), manual("B"))
        val back = SequenceGestures.resumeInterrupted(run, lastWritten = 10_000, now = 100_000)
        assertEquals(0L, back.elapsed(100_000))
        assertEquals(90_000L, back.paused)
    }
}
