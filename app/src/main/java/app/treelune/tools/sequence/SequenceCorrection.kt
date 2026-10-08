package app.treelune.tools.sequence

/** What a session over says of its steps: done, skipped, not reached. */
data class StepCounts(val done: Int, val skipped: Int, val notDone: Int) {
    val total: Int get() = done + skipped + notDone

    /** Done when every step was reached, stopped otherwise. */
    val status: String get() = if (notDone == 0) SequenceToolType.Status.DONE else SequenceToolType.Status.STOPPED
}

/**
 * A session over, corrected after the fact (docs/design/sequence-tool.md, « Les opérations »):
 * its counts stay those of the same session, their sum the number of its steps, and its state
 * follows them. Pure: the service reads the entry, applies this, writes the result.
 */
object SequenceCorrection {

    sealed class Result {
        data class Corrected(val counts: StepCounts) : Result()
        /** The counts given do not add up to the session's steps. */
        data class WrongTotal(val total: Int, val given: Int) : Result()
        /** A count below zero. */
        data object Negative : Result()
    }

    /** [current] with the counts given (null: kept). */
    fun apply(current: StepCounts, done: Int?, skipped: Int?, notDone: Int?): Result {
        val corrected = StepCounts(done ?: current.done, skipped ?: current.skipped, notDone ?: current.notDone)
        if (corrected.done < 0 || corrected.skipped < 0 || corrected.notDone < 0) return Result.Negative
        if (corrected.total != current.total) return Result.WrongTotal(current.total, corrected.total)
        return Result.Corrected(corrected)
    }
}
