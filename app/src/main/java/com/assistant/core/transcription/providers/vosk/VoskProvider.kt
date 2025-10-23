package com.assistant.core.transcription.providers.vosk

import android.content.Context
import androidx.compose.runtime.Composable
import com.assistant.core.strings.Strings
import com.assistant.core.transcription.models.DownloadResult
import com.assistant.core.transcription.models.TimeSegment
import com.assistant.core.transcription.models.TranscriptionModel
import com.assistant.core.transcription.models.TranscriptionResult
import com.assistant.core.transcription.providers.TranscriptionProvider
import com.assistant.core.utils.LogManager
import com.assistant.core.validation.Schema
import com.assistant.core.validation.SchemaCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Download progress data
 */
data class DownloadProgress(
    val modelId: String,
    val progress: Int,  // 0-100
    val attempt: Int,
    val maxAttempts: Int
)

/**
 * Vosk offline speech recognition provider
 *
 * Features:
 * - Offline transcription (no network required after model download)
 * - Multiple language support (40+ languages)
 * - Model management (download, cache, delete)
 * - Segment-based transcription with timestamps
 *
 * Configuration:
 * - default_model: Model ID to use for transcription
 * - downloaded_models: List of model IDs already downloaded
 */
class VoskProvider(private val context: Context) : TranscriptionProvider {

    private val s = Strings.`for`(context = context)

    companion object {
        private const val PROVIDER_ID = "vosk"
        private const val SCHEMA_ID = "transcription_provider_vosk_config"

        // Sample rate must match AudioRecorder (16kHz)
        private const val SAMPLE_RATE = 16000f
    }

    // Cached model instance (heavy to load)
    private var cachedModel: Model? = null
    private var cachedModelId: String? = null

    // Download progress state (observable)
    private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
    val downloadProgress: StateFlow<DownloadProgress?> = _downloadProgress.asStateFlow()

    // ========================================================================================
    // TranscriptionProvider Implementation
    // ========================================================================================

    override fun getProviderId(): String = PROVIDER_ID

    override fun getDisplayName(): String = "Vosk (Offline)"

    override fun getAllSchemaIds(): List<String> = listOf(SCHEMA_ID)

    override fun getSchema(schemaId: String, context: Context): Schema? {
        if (schemaId != SCHEMA_ID) return null

        return Schema(
            id = SCHEMA_ID,
            displayName = "Vosk Provider Configuration",
            description = "Configuration for Vosk offline speech recognition provider",
            category = SchemaCategory.TRANSCRIPTION_PROVIDER,
            content = """
                {
                  "${'$'}schema": "http://json-schema.org/draft-07/schema#",
                  "type": "object",
                  "required": ["default_model"],
                  "properties": {
                    "default_model": {
                      "type": "string",
                      "minLength": 1,
                      "description": "Default model ID for transcription"
                    },
                    "downloaded_models": {
                      "type": "array",
                      "items": {
                        "type": "string"
                      },
                      "default": [],
                      "description": "List of downloaded model IDs"
                    }
                  }
                }
            """.trimIndent()
        )
    }

    override fun getFormFieldName(fieldName: String, context: Context): String {
        // Return user-friendly field names for validation errors
        return when (fieldName) {
            "default_model" -> "Default Model"
            "downloaded_models" -> "Downloaded Models"
            else -> fieldName.capitalize()
        }
    }

    @Composable
    override fun getConfigScreen(
        config: String,
        onSave: (String) -> Unit,
        onCancel: () -> Unit,
        onReset: (() -> Unit)?
    ) {
        com.assistant.core.transcription.providers.vosk.ui.VoskConfigScreen(
            provider = this,
            config = config,
            onSave = onSave,
            onCancel = onCancel,
            onReset = onReset
        )
    }

    override suspend fun transcribe(
        audioFile: File,
        segmentsTimestamps: List<TimeSegment>,
        config: String
    ): TranscriptionResult = withContext(Dispatchers.Default) {
        LogManager.service("VoskProvider: Starting transcription of ${audioFile.name}")

        try {
            // Parse config
            val configJson = JSONObject(config)
            val modelId = configJson.optString("default_model").takeIf { it.isNotEmpty() }
                ?: return@withContext TranscriptionResult(
                    success = false,
                    segmentsTexts = emptyList(),
                    fullText = "",
                    errorMessage = s.shared("error_transcription_no_model")
                )

            // Load model (cached if same as previous)
            val model = loadModel(modelId) ?: return@withContext TranscriptionResult(
                success = false,
                segmentsTexts = emptyList(),
                fullText = "",
                errorMessage = "Failed to load model: $modelId"
            )

            // Transcribe each segment
            val segmentTexts = mutableListOf<String>()

            for (segment in segmentsTimestamps) {
                val recognizer = Recognizer(model, SAMPLE_RATE)

                try {
                    // Extract segment from audio file
                    val segmentAudio = extractAudioSegment(audioFile, segment)

                    // Feed audio data to recognizer
                    recognizer.acceptWaveForm(segmentAudio, segmentAudio.size)

                    // Get final result
                    val resultJson = JSONObject(recognizer.finalResult)
                    val text = resultJson.optString("text", "")

                    segmentTexts.add(text)

                    LogManager.service("VoskProvider: Segment [${segment.start}-${segment.end}s] -> \"$text\"")

                } finally {
                    recognizer.close()
                }
            }

            // Combine all segments
            val fullText = segmentTexts.joinToString(" ").trim()

            LogManager.service("VoskProvider: Transcription complete, total length: ${fullText.length} chars")

            TranscriptionResult(
                success = true,
                segmentsTexts = segmentTexts,
                fullText = fullText
            )

        } catch (e: Exception) {
            LogManager.service("VoskProvider: Transcription failed: ${e.message}", "ERROR", e)
            TranscriptionResult(
                success = false,
                segmentsTexts = emptyList(),
                fullText = "",
                errorMessage = e.message ?: "Unknown transcription error"
            )
        }
    }

    override suspend fun listAvailableModels(config: String): List<TranscriptionModel> {
        // Return all Vosk models from hardcoded list
        return VoskModels.ALL_MODELS
    }

    override suspend fun downloadModel(modelId: String, config: String): DownloadResult = withContext(Dispatchers.IO) {
        LogManager.service("VoskProvider: Downloading model: $modelId")

        try {
            val downloadUrl = VoskModels.getDownloadUrl(modelId)
            val modelsDir = File(context.filesDir, "speech_models")
            modelsDir.mkdirs()

            val modelDir = File(modelsDir, modelId)
            if (modelDir.exists()) {
                LogManager.service("VoskProvider: Model already exists: $modelId", "WARN")
                _downloadProgress.value = null  // Clear progress
                return@withContext DownloadResult(success = true)
            }

            // Download ZIP file
            val zipFile = File(modelsDir, "$modelId.zip")

            try {
                downloadFile(downloadUrl, zipFile, modelId)

                // Extract ZIP (emit 100% progress during extraction)
                _downloadProgress.value = DownloadProgress(modelId, 100, 1, 1)
                extractZip(zipFile, modelsDir)

                // Delete ZIP file
                zipFile.delete()

                // Update config with downloaded model
                updateDownloadedModels(config, modelId, add = true)

                LogManager.service("VoskProvider: Model downloaded successfully: $modelId")

                // Clear progress
                _downloadProgress.value = null

                DownloadResult(success = true)

            } catch (e: Exception) {
                // Cleanup on failure
                zipFile.delete()
                modelDir.deleteRecursively()
                _downloadProgress.value = null  // Clear progress
                throw e
            }

        } catch (e: Exception) {
            LogManager.service("VoskProvider: Failed to download model: ${e.message}", "ERROR", e)
            _downloadProgress.value = null  // Clear progress
            DownloadResult(
                success = false,
                errorMessage = e.message ?: "Download failed"
            )
        }
    }

    override fun getDownloadedModels(): List<String> {
        val modelsDir = File(context.filesDir, "speech_models")
        if (!modelsDir.exists()) return emptyList()

        return modelsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?: emptyList()
    }

    override fun deleteModel(modelId: String): Boolean {
        try {
            val modelsDir = File(context.filesDir, "speech_models")
            val modelDir = File(modelsDir, modelId)

            if (!modelDir.exists()) {
                LogManager.service("VoskProvider: Model not found for deletion: $modelId", "WARN")
                return false
            }

            // Clear cache if this model is cached
            if (cachedModelId == modelId) {
                cachedModel = null
                cachedModelId = null
            }

            val deleted = modelDir.deleteRecursively()

            if (deleted) {
                LogManager.service("VoskProvider: Model deleted: $modelId")
            } else {
                LogManager.service("VoskProvider: Failed to delete model: $modelId", "ERROR")
            }

            return deleted

        } catch (e: Exception) {
            LogManager.service("VoskProvider: Error deleting model: ${e.message}", "ERROR", e)
            return false
        }
    }

    // ========================================================================================
    // Private Helper Methods
    // ========================================================================================

    /**
     * Load Vosk model (cached)
     */
    private suspend fun loadModel(modelId: String): Model? = withContext(Dispatchers.IO) {
        // Return cached model if same
        if (cachedModelId == modelId && cachedModel != null) {
            LogManager.service("VoskProvider: Using cached model: $modelId")
            return@withContext cachedModel
        }

        // Clear previous cache
        cachedModel = null
        cachedModelId = null

        try {
            val modelsDir = File(context.filesDir, "speech_models")
            val modelDir = File(modelsDir, modelId)

            if (!modelDir.exists()) {
                LogManager.service("VoskProvider: Model not found: $modelId", "ERROR")
                return@withContext null
            }

            LogManager.service("VoskProvider: Loading model: $modelId (this may take time...)")

            val model = Model(modelDir.absolutePath)

            // Cache for future use
            cachedModel = model
            cachedModelId = modelId

            LogManager.service("VoskProvider: Model loaded successfully: $modelId")

            model

        } catch (e: Exception) {
            LogManager.service("VoskProvider: Failed to load model: ${e.message}", "ERROR", e)
            null
        }
    }

    /**
     * Extract audio segment from WAV file
     * Returns raw PCM data for the specified time range
     */
    private fun extractAudioSegment(audioFile: File, segment: TimeSegment): ByteArray {
        return FileInputStream(audioFile).use { inputStream ->
            // Skip WAV header (44 bytes)
            inputStream.skip(44)

            // Calculate byte positions
            // 16kHz * 1 channel * 2 bytes (16-bit) = 32000 bytes per second
            val bytesPerSecond = (SAMPLE_RATE * 2).toInt() // 2 bytes per sample (16-bit)
            val startByte = (segment.start * bytesPerSecond).toLong()
            val endByte = (segment.end * bytesPerSecond).toLong()
            val segmentSize = (endByte - startByte).toInt()

            // Skip to segment start
            inputStream.skip(startByte)

            // Read segment data
            val buffer = ByteArray(segmentSize)
            var totalRead = 0

            while (totalRead < segmentSize) {
                val read = inputStream.read(buffer, totalRead, segmentSize - totalRead)
                if (read == -1) break
                totalRead += read
            }

            buffer
        }
    }

    /**
     * Download file from URL with progress logging and retry
     */
    private fun downloadFile(urlString: String, outputFile: File, modelId: String, maxRetries: Int = 3) {
        var lastException: Exception? = null

        // Try download with retries
        repeat(maxRetries) { attempt ->
            try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 120000  // 2 minutes for slow connections
                connection.readTimeout = 120000     // 2 minutes for slow connections
                connection.setRequestProperty("Accept-Encoding", "identity") // Disable compression for reliable download

                try {
                    connection.connect()

                    val fileLength = connection.contentLength
                    LogManager.service("VoskProvider: Downloading ${fileLength / 1_000_000}MB... (attempt ${attempt + 1}/$maxRetries)")

                    connection.inputStream.use { input ->
                        FileOutputStream(outputFile).use { output ->
                            val buffer = ByteArray(16384)  // Larger buffer for better performance
                            var totalRead = 0L
                            var read: Int
                            var lastEmittedProgress = 0

                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                totalRead += read

                                // Emit progress every 1%
                                if (fileLength > 0) {
                                    val progress = (totalRead * 100 / fileLength).toInt()
                                    if (progress > lastEmittedProgress) {
                                        _downloadProgress.value = DownloadProgress(
                                            modelId = modelId,
                                            progress = progress,
                                            attempt = attempt + 1,
                                            maxAttempts = maxRetries
                                        )
                                        lastEmittedProgress = progress

                                        // Log every 5%
                                        if (progress % 5 == 0) {
                                            LogManager.service("VoskProvider: Download progress: $progress%")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    LogManager.service("VoskProvider: Download complete")
                    return // Success, exit function

                } finally {
                    connection.disconnect()
                }

            } catch (e: Exception) {
                lastException = e
                LogManager.service("VoskProvider: Download attempt ${attempt + 1} failed: ${e.message}", "WARN")

                // Delete partial file
                if (outputFile.exists()) {
                    outputFile.delete()
                }

                // Wait before retry (exponential backoff)
                if (attempt < maxRetries - 1) {
                    val delay = (1000L * (attempt + 1))
                    LogManager.service("VoskProvider: Retrying in ${delay}ms...")
                    Thread.sleep(delay)
                }
            }
        }

        // All retries failed
        throw lastException ?: Exception("Download failed after $maxRetries attempts")
    }

    /**
     * Extract ZIP file to directory
     */
    private fun extractZip(zipFile: File, destDir: File) {
        LogManager.service("VoskProvider: Extracting model...")

        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry

            while (entry != null) {
                val file = File(destDir, entry.name)

                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { output ->
                        zis.copyTo(output)
                    }
                }

                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        LogManager.service("VoskProvider: Extraction complete")
    }

    /**
     * Update downloaded_models list in config
     */
    private suspend fun updateDownloadedModels(config: String, modelId: String, add: Boolean) {
        // TODO: Update provider config via coordinator
        // This should call transcription_provider_config.set with updated downloaded_models list
        LogManager.service("VoskProvider: Would update config to ${if (add) "add" else "remove"} model: $modelId")
    }
}
