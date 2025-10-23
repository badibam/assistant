package com.assistant.core.transcription.ui

import android.content.Context
import com.assistant.core.transcription.audio.AudioRecorder
import com.assistant.core.transcription.models.TimeSegment
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Controller for audio recording session
 *
 * Manages AudioRecorder, timer updates, segment markers, and exposes state via StateFlow.
 * Separates business logic from UI (RecordingDialog).
 *
 * Usage:
 * ```kotlin
 * val controller = RecordingController(context, audioFilePath)
 * controller.state.collect { state ->
 *     // Update UI based on state
 * }
 * controller.start()
 * controller.markSegment()
 * controller.pause()
 * controller.resume()
 * controller.validate { segments -> /* handle completion */ }
 * controller.cancel()
 * controller.cleanup()
 * ```
 *
 * @param context Android context
 * @param audioFilePath Absolute path to output audio file
 */
class RecordingController(
    private val context: Context,
    private val audioFilePath: String
) {

    private val audioRecorder = AudioRecorder(context)

    // Mutable state
    private val _state = MutableStateFlow(RecordingState())

    /**
     * Observable state flow
     * UI should collect this to update display
     */
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    // Coroutine scope for timer updates
    private var timerScope: CoroutineScope? = null
    private var timerJob: Job? = null

    // Segment markers (timestamps in milliseconds)
    private val segmentMarkers = mutableListOf<SegmentMarker>()

    /**
     * Start recording
     * Initializes AudioRecorder and starts timer updates
     */
    fun start(onResult: (Boolean, String?) -> Unit) {
        val audioFile = File(audioFilePath)

        audioRecorder.start(audioFile) { success, error ->
            if (success) {
                _state.value = _state.value.copy(
                    status = RecordingStatus.RECORDING,
                    errorMessage = null
                )

                // Start timer updates
                startTimerUpdates()

                LogManager.service("RecordingController: Recording started")
                onResult(true, null)
            } else {
                _state.value = _state.value.copy(
                    status = RecordingStatus.ERROR,
                    errorMessage = error
                )

                LogManager.service("RecordingController: Failed to start recording: $error", "ERROR")
                onResult(false, error)
            }
        }
    }

    /**
     * Pause recording
     */
    fun pause(): Boolean {
        val success = audioRecorder.pause()

        if (success) {
            _state.value = _state.value.copy(status = RecordingStatus.PAUSED)
            LogManager.service("RecordingController: Recording paused")
        }

        return success
    }

    /**
     * Resume recording
     */
    fun resume(): Boolean {
        val success = audioRecorder.resume()

        if (success) {
            _state.value = _state.value.copy(status = RecordingStatus.RECORDING)
            LogManager.service("RecordingController: Recording resumed")
        }

        return success
    }

    /**
     * Mark segment timestamp ("end of sentence")
     * Adds current timestamp to segment markers list
     */
    fun markSegment() {
        val currentDuration = audioRecorder.getCurrentDuration()
        val marker = SegmentMarker(timestampMs = currentDuration)

        segmentMarkers.add(marker)

        _state.value = _state.value.copy(
            segments = segmentMarkers.toList()  // Copy to trigger state update
        )

        LogManager.service("RecordingController: Segment marked at ${currentDuration}ms")
    }

    /**
     * Validate and complete recording
     * Stops AudioRecorder and converts segment markers to TimeSegments
     *
     * @param onComplete Callback with list of TimeSegments
     */
    fun validate(onComplete: (List<TimeSegment>) -> Unit) {
        stopTimerUpdates()

        val audioFile = audioRecorder.stop()

        if (audioFile != null && audioFile.exists()) {
            val totalDuration = audioRecorder.getCurrentDuration()

            // Convert segment markers to TimeSegments
            val segments = buildTimeSegments(totalDuration)

            _state.value = _state.value.copy(
                status = RecordingStatus.COMPLETED,
                durationMs = totalDuration
            )

            LogManager.service("RecordingController: Recording completed with ${segments.size} segments")
            onComplete(segments)
        } else {
            _state.value = _state.value.copy(
                status = RecordingStatus.ERROR,
                errorMessage = "Failed to save audio file"
            )

            LogManager.service("RecordingController: Failed to complete recording", "ERROR")
            onComplete(emptyList())
        }
    }

    /**
     * Cancel recording
     * Deletes audio file and resets state
     */
    fun cancel() {
        stopTimerUpdates()
        audioRecorder.cancel()

        _state.value = RecordingState()  // Reset to initial state
        segmentMarkers.clear()

        LogManager.service("RecordingController: Recording cancelled")
    }

    /**
     * Cleanup resources
     * Must be called when controller is no longer needed
     */
    fun cleanup() {
        stopTimerUpdates()
        cancel()
    }

    /**
     * Stop timer updates only (without cancelling/deleting audio)
     * Used when recording was validated and we just need to cleanup UI resources
     */
    fun stopTimerOnly() {
        stopTimerUpdates()
        LogManager.service("RecordingController: Timer stopped (recording was validated)")
    }

    // ========================================================================================
    // Private Helper Methods
    // ========================================================================================

    /**
     * Start coroutine for timer and audio level updates
     */
    private fun startTimerUpdates() {
        timerScope = CoroutineScope(Dispatchers.Main)
        timerJob = timerScope?.launch {
            while (isActive && audioRecorder.isRecording()) {
                val currentDuration = audioRecorder.getCurrentDuration()
                val audioLevel = calculateAudioLevel()

                _state.value = _state.value.copy(
                    durationMs = currentDuration,
                    audioLevel = audioLevel
                )

                delay(100)  // Update every 100ms
            }
        }
    }

    /**
     * Stop timer updates coroutine
     */
    private fun stopTimerUpdates() {
        timerJob?.cancel()
        timerScope?.cancel()
        timerJob = null
        timerScope = null
    }

    /**
     * Calculate audio level (0.0 to 1.0)
     * Currently returns 0 as AudioRecorder doesn't implement getMaxAmplitude()
     * TODO: Implement real audio level calculation if needed for better UX
     */
    private fun calculateAudioLevel(): Float {
        val maxAmplitude = audioRecorder.getMaxAmplitude()
        // AudioRecorder.getMaxAmplitude() currently returns 0
        // For MVP, we return 0 (no visual feedback)
        // Future: Implement proper amplitude calculation from audio buffer
        return if (maxAmplitude > 0) {
            (maxAmplitude / 32767.0f).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    /**
     * Build TimeSegments from segment markers
     *
     * Logic:
     * - If no markers: single segment [0, totalDuration]
     * - If markers exist: create segments between each marker + final segment
     *
     * Example:
     * - totalDuration = 10000ms
     * - markers = [3000ms, 7000ms]
     * - result = [
     *     TimeSegment(0, 3.0),
     *     TimeSegment(3.0, 7.0),
     *     TimeSegment(7.0, 10.0)
     *   ]
     */
    private fun buildTimeSegments(totalDurationMs: Long): List<TimeSegment> {
        if (segmentMarkers.isEmpty()) {
            // No markers: single segment covering entire recording
            return listOf(
                TimeSegment(
                    start = 0f,
                    end = totalDurationMs / 1000f  // Convert to seconds
                )
            )
        }

        // Build segments from markers
        val segments = mutableListOf<TimeSegment>()
        var previousTimestamp = 0L

        // Sort markers by timestamp (should already be sorted, but ensure it)
        val sortedMarkers = segmentMarkers.sortedBy { it.timestampMs }

        sortedMarkers.forEach { marker ->
            segments.add(
                TimeSegment(
                    start = previousTimestamp / 1000f,  // Convert to seconds
                    end = marker.timestampMs / 1000f
                )
            )
            previousTimestamp = marker.timestampMs
        }

        // Add final segment from last marker to end
        segments.add(
            TimeSegment(
                start = previousTimestamp / 1000f,
                end = totalDurationMs / 1000f
            )
        )

        return segments
    }
}
