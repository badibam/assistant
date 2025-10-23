package com.assistant.core.transcription.models

/**
 * Time segment for audio transcription
 * Represents a portion of audio file with start and end timestamps
 *
 * @param start Start time in seconds
 * @param end End time in seconds
 */
data class TimeSegment(
    val start: Float,
    val end: Float
)

/**
 * Result of a transcription operation
 *
 * @param success Whether transcription completed successfully
 * @param segmentsTexts Transcribed text for each segment
 * @param fullText Complete transcribed text (all segments concatenated)
 * @param errorMessage Error message if transcription failed
 */
data class TranscriptionResult(
    val success: Boolean,
    val segmentsTexts: List<String>,
    val fullText: String,
    val errorMessage: String? = null
)

/**
 * Available transcription model metadata
 *
 * @param id Unique model identifier
 * @param name Display name
 * @param language Language code (e.g., "fr", "en")
 * @param size Model file size in bytes
 * @param quality Quality level (e.g., "small", "medium", "large")
 */
data class TranscriptionModel(
    val id: String,
    val name: String,
    val language: String,
    val size: Long,
    val quality: String
)

/**
 * Result of a model download operation
 *
 * @param success Whether download completed successfully
 * @param errorMessage Error message if download failed
 */
data class DownloadResult(
    val success: Boolean,
    val errorMessage: String? = null
)

/**
 * Pending transcription waiting to be processed or retried
 *
 * @param entryId Tool data entry ID
 * @param toolInstanceId Tool instance ID
 * @param tooltype Tool type name
 * @param fieldName Field name in entry data
 * @param audioFile Audio file name
 * @param segmentsTimestamps List of time segments to transcribe
 * @param model Transcription model identifier
 */
data class PendingTranscription(
    val entryId: String,
    val toolInstanceId: String,
    val tooltype: String,
    val fieldName: String,
    val audioFile: String,
    val segmentsTimestamps: List<TimeSegment>,
    val model: String
)

/**
 * Transcription status for a field
 */
enum class TranscriptionStatus {
    PENDING,
    COMPLETED,
    FAILED
}

/**
 * Context information for triggering a transcription
 * Used by TranscribableTextField to auto-start transcription after recording
 *
 * @param entryId Tool data entry ID
 * @param toolInstanceId Tool instance ID
 * @param tooltype Tool type name (e.g., "journal", "tracking")
 * @param fieldName Field name in entry data (e.g., "content", "description")
 */
data class TranscriptionContext(
    val entryId: String,
    val toolInstanceId: String,
    val tooltype: String,
    val fieldName: String
)

/**
 * Transcription metadata for a single field
 * Stored in tool data entry's transcription_metadata map
 *
 * @param audioFile Audio file name
 * @param segmentsTimestamps List of time segments
 * @param status Current transcription status
 * @param model Model identifier used for transcription
 * @param date Timestamp when transcription started
 * @param error Error message if status is FAILED
 * @param segmentsTexts Transcribed text per segment (present when COMPLETED)
 */
data class TranscriptionMetadata(
    val audioFile: String,
    val segmentsTimestamps: List<TimeSegment>,
    val status: String, // "pending", "completed", "failed"
    val model: String,
    val date: Long,
    val error: String? = null,
    val segmentsTexts: List<String>? = null
)
