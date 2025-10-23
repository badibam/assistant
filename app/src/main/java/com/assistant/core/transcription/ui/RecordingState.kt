package com.assistant.core.transcription.ui

/**
 * State representation for audio recording
 *
 * Immutable data class representing the current state of an audio recording session.
 * Used by RecordingController to expose state to RecordingDialog UI.
 */
data class RecordingState(
    val status: RecordingStatus = RecordingStatus.IDLE,
    val durationMs: Long = 0L,
    val audioLevel: Float = 0f,  // 0.0 to 1.0
    val segments: List<SegmentMarker> = emptyList(),
    val errorMessage: String? = null
)

/**
 * Recording status enumeration
 */
enum class RecordingStatus {
    IDLE,           // Not started yet
    RECORDING,      // Currently recording
    PAUSED,         // Recording paused
    COMPLETED,      // Recording finished successfully
    ERROR           // Recording failed
}

/**
 * Segment marker created by "end of sentence" clicks
 *
 * @param timestampMs Timestamp in milliseconds from start of recording
 */
data class SegmentMarker(
    val timestampMs: Long
)
