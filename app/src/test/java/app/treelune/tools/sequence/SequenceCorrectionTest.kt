package app.treelune.tools.sequence

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers a session corrected after the fact: its counts keep adding up to its steps, and its
 * state follows them — done once no step is left unreached, stopped while one is.
 */
class SequenceCorrectionTest {

    private val stopped = StepCounts(done = 11, skipped = 0, notDone = 6)

    @Test
    fun `steps finished without the app turn a stopped session into a done one`() {
        val result = SequenceCorrection.apply(stopped, done = 17, skipped = null, notDone = 0)
        val counts = (result as SequenceCorrection.Result.Corrected).counts
        assertEquals(StepCounts(17, 0, 0), counts)
        assertEquals(SequenceToolType.Status.DONE, counts.status)
    }

    @Test
    fun `a skip touched by mistake becomes a step done, the state staying done`() {
        val counts = (SequenceCorrection.apply(StepCounts(16, 1, 0), done = 17, skipped = 0, notDone = null) as SequenceCorrection.Result.Corrected).counts
        assertEquals(SequenceToolType.Status.DONE, counts.status)
    }

    @Test
    fun `counts that no longer add up to the session's steps are refused`() {
        assertEquals(SequenceCorrection.Result.WrongTotal(17, 18), SequenceCorrection.apply(stopped, done = 12, skipped = null, notDone = null))
    }

    @Test
    fun `a count below zero is refused`() {
        assertEquals(SequenceCorrection.Result.Negative, SequenceCorrection.apply(stopped, done = 18, skipped = -1, notDone = 0))
    }

    @Test
    fun `a step not reached again makes the session stopped`() {
        val counts = (SequenceCorrection.apply(StepCounts(17, 0, 0), done = 15, skipped = null, notDone = 2) as SequenceCorrection.Result.Corrected).counts
        assertEquals(SequenceToolType.Status.STOPPED, counts.status)
    }
}
