package app.treelune.tools.sequence

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The link between a session's operations and the session running (SequenceRunService), inside
 * the app's process: a gesture written, the service hears of it to sound its signal and follow
 * the new run; the screen hears which session the service runs.
 */
object SequenceLive {

    /** A gesture written on the entry [id]: [operation], and what it made of the run. */
    data class Change(val id: String, val operation: String, val step: Step)

    private val _changes = MutableSharedFlow<Change>(extraBufferCapacity = 16)
    val changes: SharedFlow<Change> = _changes.asSharedFlow()

    private val _running = MutableStateFlow<String?>(null)

    /**
     * The entry of the session the service runs, null when none does. A session recorded as
     * running that is not this one was stopped with the app: the screen offers to resume it.
     */
    val running: StateFlow<String?> = _running.asStateFlow()

    internal fun setRunning(id: String?) { _running.value = id }

    /** A session started, or resumed after the app stopped: the service runs it. */
    fun started(context: Context, id: String) {
        // Known at once, so the screen never takes the session starting for one interrupted
        _running.value = id
        val intent = Intent(context, SequenceRunService::class.java).setAction(SequenceRunService.ACTION_RUN).putExtra(SequenceRunService.EXTRA_ENTRY, id)
        context.startForegroundService(intent)
    }

    fun changed(context: Context, id: String, operation: String, step: Step) {
        if (operation == SequenceService.RESUME_INTERRUPTED) started(context, id)
        _changes.tryEmit(Change(id, operation, step))
    }
}
