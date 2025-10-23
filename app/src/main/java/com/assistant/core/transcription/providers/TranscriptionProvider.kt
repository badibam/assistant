package com.assistant.core.transcription.providers

import android.content.Context
import androidx.compose.runtime.Composable
import com.assistant.core.validation.Schema
import com.assistant.core.validation.SchemaProvider
import com.assistant.core.transcription.models.DownloadResult
import com.assistant.core.transcription.models.TimeSegment
import com.assistant.core.transcription.models.TranscriptionModel
import com.assistant.core.transcription.models.TranscriptionResult
import java.io.File

/**
 * Interface for speech-to-text transcription providers
 * Follows the same pattern as AIProvider (extensible, schema-validated config)
 *
 * Each provider implements offline or online transcription capabilities,
 * manages its own models, and provides a configuration UI.
 */
interface TranscriptionProvider : SchemaProvider {

    /**
     * Unique provider identifier (e.g., "vosk", "whisper_local")
     */
    fun getProviderId(): String

    /**
     * Display name for UI (e.g., "Vosk Offline")
     */
    fun getDisplayName(): String

    /**
     * Configuration screen for provider settings
     *
     * @param config Current configuration JSON string
     * @param onSave Callback when user saves configuration (receives new config JSON)
     * @param onCancel Callback when user cancels configuration
     * @param onReset Optional callback to reset configuration to defaults
     */
    @Composable
    fun getConfigScreen(
        config: String,
        onSave: (String) -> Unit,
        onCancel: () -> Unit,
        onReset: (() -> Unit)?
    )

    /**
     * Transcribe audio file segments
     *
     * @param audioFile Audio file to transcribe (WAV 16kHz mono)
     * @param segmentsTimestamps Time segments to transcribe
     * @param config Provider configuration JSON
     * @return Transcription result with text per segment
     */
    suspend fun transcribe(
        audioFile: File,
        segmentsTimestamps: List<TimeSegment>,
        config: String
    ): TranscriptionResult

    /**
     * List available models that can be downloaded
     *
     * @param config Provider configuration JSON
     * @return List of available models with metadata
     */
    suspend fun listAvailableModels(config: String): List<TranscriptionModel>

    /**
     * Download a specific model
     *
     * @param modelId Model identifier to download
     * @param config Provider configuration JSON
     * @return Download result
     */
    suspend fun downloadModel(modelId: String, config: String): DownloadResult

    /**
     * Get list of already downloaded models
     *
     * @return List of model IDs that are downloaded and ready to use
     */
    fun getDownloadedModels(): List<String>

    /**
     * Delete a downloaded model
     *
     * @param modelId Model identifier to delete
     * @return true if deletion successful
     */
    fun deleteModel(modelId: String): Boolean
}
