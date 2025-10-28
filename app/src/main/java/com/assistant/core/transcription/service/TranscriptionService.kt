package com.assistant.core.transcription.service

import android.content.Context
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.database.AppDatabase
import com.assistant.core.utils.DataChangeNotifier
import com.assistant.core.services.ExecutableService
import com.assistant.core.services.OperationResult
import com.assistant.core.strings.Strings
import com.assistant.core.transcription.models.PendingTranscription
import com.assistant.core.transcription.models.TimeSegment
import com.assistant.core.transcription.models.TranscriptionMetadata
import com.assistant.core.transcription.providers.TranscriptionProviderRegistry
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Transcription service (ExecutableService)
 *
 * Responsibilities:
 * - Manage speech-to-text transcription operations
 * - Background processing of audio files
 * - Auto-retry on app restart for pending transcriptions
 *
 * Available operations:
 * - transcription.start (multi-step: load → transcribe → save)
 * - transcription.cancel
 * - transcription.list_pending
 * - transcription.get_status
 */
class TranscriptionService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(context = context)

    companion object {
        private const val TAG = "TranscriptionService"

        // Temporary data storage for multi-step operations (shared across instances)
        private val tempData = ConcurrentHashMap<String, Any>()

        // Active transcriptions tracking (operationId → entryId+fieldName)
        private val activeTranscriptions = ConcurrentHashMap<String, Pair<String, String>>()
    }

    // ========================================================================================
    // ExecutableService Implementation
    // ========================================================================================

    override suspend fun execute(
        operation: String,
        params: JSONObject,
        token: CancellationToken
    ): OperationResult {
        LogManager.service("TranscriptionService.execute() called: $operation")

        return withContext(Dispatchers.IO) {
            try {
                when (operation) {
                    "start" -> executeTranscription(params, token)
                    "cancel" -> cancelTranscription(params, token)
                    "list_pending" -> listPendingTranscriptions(params, token)
                    "get_status" -> getTranscriptionStatus(params, token)
                    else -> {
                        LogManager.service("Unknown operation: $operation", "ERROR")
                        OperationResult.error("Unknown operation: $operation")
                    }
                }
            } catch (e: Exception) {
                LogManager.service("TranscriptionService error: ${e.message}", "ERROR", e)
                OperationResult.error(s.shared("error_generic").format(e.message ?: ""))
            }
        }
    }

    // ========================================================================================
    // Multi-Step Transcription Operation
    // ========================================================================================

    private suspend fun executeTranscription(
        params: JSONObject,
        token: CancellationToken
    ): OperationResult {
        val operationId = params.optString("operationId")
        val phase = params.optInt("phase", 1)

        return when (phase) {
            1 -> phase1_PrepareTranscription(params, operationId, token)
            2 -> phase2_TranscribeAudio(params, operationId, token)
            3 -> phase3_SaveResults(params, operationId, token)
            else -> OperationResult.error(s.shared("service_error_invalid_phase").format(phase))
        }
    }

    /**
     * Phase 1: Load entry data, validate audio file, prepare transcription context
     */
    private suspend fun phase1_PrepareTranscription(
        params: JSONObject,
        operationId: String,
        token: CancellationToken
    ): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val entryId = params.optString("entryId").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_required").format("entryId"))
        val toolInstanceId = params.optString("toolInstanceId").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_required").format("toolInstanceId"))
        val tooltype = params.optString("tooltype").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_required").format("tooltype"))
        val fieldName = params.optString("fieldName").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_required").format("fieldName"))
        val audioFile = params.optString("audioFile").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_required").format("audioFile"))
        val segmentsTimestampsJson = params.optJSONArray("segmentsTimestamps")
            ?: return OperationResult.error(s.shared("error_param_required").format("segmentsTimestamps"))

        LogManager.service("Phase 1: Preparing transcription for entry=$entryId, field=$fieldName")

        try {
            // Parse time segments
            val segments = mutableListOf<TimeSegment>()
            for (i in 0 until segmentsTimestampsJson.length()) {
                val segment = segmentsTimestampsJson.getJSONObject(i)
                segments.add(
                    TimeSegment(
                        start = segment.optDouble("start", 0.0).toFloat(),
                        end = segment.optDouble("end", 0.0).toFloat()
                    )
                )
            }

            if (segments.isEmpty()) {
                return OperationResult.error(s.shared("error_transcription_no_segments"))
            }

            // Validate audio file exists
            val audioDir = File(context.filesDir, "${tooltype}_audio")
            val audioFilePath = File(audioDir, audioFile)

            if (!audioFilePath.exists()) {
                LogManager.service("Audio file not found: ${audioFilePath.absolutePath}", "ERROR")
                return OperationResult.error(s.shared("error_transcription_audio_not_found").format(audioFile))
            }

            LogManager.service("Audio file validated: ${audioFilePath.absolutePath} (${audioFilePath.length()} bytes)")

            // Get active transcription provider and config
            val registry = TranscriptionProviderRegistry(context)
            val provider = registry.getActiveProvider()
                ?: return OperationResult.error(s.shared("error_transcription_no_provider"))

            val database = AppDatabase.getDatabase(context)
            val providerConfig = database.transcriptionDao().getActiveProviderConfig()
                ?: return OperationResult.error(s.shared("error_transcription_no_config"))

            // Extract model ID from config (provider-specific, for now assume "default_model" field)
            val configJson = JSONObject(providerConfig.configJson)
            val modelId = configJson.optString("default_model").takeIf { it.isNotEmpty() }
                ?: return OperationResult.error(s.shared("error_transcription_no_model"))

            LogManager.service("Provider configured: ${provider.getProviderId()}, model: $modelId")

            // Store context for phase 2
            val transcriptionContext = mapOf(
                "entryId" to entryId,
                "toolInstanceId" to toolInstanceId,
                "tooltype" to tooltype,
                "fieldName" to fieldName,
                "audioFile" to audioFilePath.absolutePath,
                "segments" to segments,
                "providerId" to provider.getProviderId(),
                "providerConfig" to providerConfig.configJson,
                "modelId" to modelId
            )

            tempData[operationId] = transcriptionContext
            activeTranscriptions[operationId] = Pair(entryId, fieldName)

            LogManager.service("Phase 1 complete: Context stored for operationId=$operationId")

            return OperationResult.success(requiresBackground = true)

        } catch (e: Exception) {
            LogManager.service("Phase 1 failed: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("error_generic").format(e.message ?: ""))
        }
    }

    /**
     * Phase 2: Load transcription model and transcribe audio segments (HEAVY CPU)
     */
    private suspend fun phase2_TranscribeAudio(
        params: JSONObject,
        operationId: String,
        token: CancellationToken
    ): OperationResult {
        if (token.isCancelled) {
            cleanup(operationId)
            return OperationResult.cancelled()
        }

        LogManager.service("Phase 2: Starting transcription (background thread)")

        try {
            @Suppress("UNCHECKED_CAST")
            val context = tempData[operationId] as? Map<String, Any>
                ?: return OperationResult.error(s.shared("service_error_no_data_found").format(operationId))

            val audioFile = File(context["audioFile"] as String)
            val segments = context["segments"] as List<TimeSegment>
            val providerId = context["providerId"] as String
            val providerConfig = context["providerConfig"] as String

            // Get provider
            val registry = TranscriptionProviderRegistry(this.context)
            val provider = registry.getProvider(providerId)
                ?: return OperationResult.error(s.shared("error_transcription_provider_not_found").format(providerId))

            LogManager.service("Phase 2: Transcribing ${segments.size} segments with provider: $providerId")

            // Execute transcription (this is the heavy operation)
            val result = provider.transcribe(audioFile, segments, providerConfig)

            if (token.isCancelled) {
                cleanup(operationId)
                return OperationResult.cancelled()
            }

            if (!result.success) {
                LogManager.service("Phase 2: Transcription failed: ${result.errorMessage}", "ERROR")
                // Store error for phase 3
                tempData[operationId] = mapOf(
                    "success" to false,
                    "error" to (result.errorMessage ?: s.shared("error_transcription_unknown")),
                    "originalContext" to context
                )
                return OperationResult.success(requiresContinuation = true)
            }

            LogManager.service("Phase 2: Transcription successful, fullText length=${result.fullText.length}")

            // Check if transcription produced any content
            if (result.fullText.trim().isEmpty()) {
                LogManager.service("Phase 2: Transcription produced empty text (0 chars), marking as failed", "WARN")
                // Store error for phase 3
                tempData[operationId] = mapOf(
                    "success" to false,
                    "error" to s.shared("transcription_no_content"),
                    "originalContext" to context
                )
                return OperationResult.success(requiresContinuation = true)
            }

            // Store results for phase 3
            tempData[operationId] = mapOf(
                "success" to true,
                "fullText" to result.fullText,
                "segmentsTexts" to result.segmentsTexts,
                "originalContext" to context
            )

            return OperationResult.success(requiresContinuation = true)

        } catch (e: Exception) {
            LogManager.service("Phase 2 failed: ${e.message}", "ERROR", e)
            cleanup(operationId)
            return OperationResult.error(s.shared("error_generic").format(e.message ?: ""))
        }
    }

    /**
     * Phase 3: Update entry data with transcription results and cleanup
     */
    private suspend fun phase3_SaveResults(
        params: JSONObject,
        operationId: String,
        token: CancellationToken
    ): OperationResult {
        LogManager.service("Phase 3: Saving transcription results")

        try {
            @Suppress("UNCHECKED_CAST")
            val results = tempData[operationId] as? Map<String, Any>
                ?: return OperationResult.error(s.shared("service_error_no_results_found").format(operationId))

            val success = results["success"] as Boolean
            val originalContext = results["originalContext"] as Map<String, Any>

            val entryId = originalContext["entryId"] as String
            val toolInstanceId = originalContext["toolInstanceId"] as String
            val tooltype = originalContext["tooltype"] as String
            val fieldName = originalContext["fieldName"] as String
            val audioFile = File(originalContext["audioFile"] as String).name
            val segments = originalContext["segments"] as List<TimeSegment>
            val modelId = originalContext["modelId"] as String

            // Load current entry data
            val database = AppDatabase.getDatabase(context)
            val entry = database.toolDataDao().getById(entryId)
                ?: return OperationResult.error(s.shared("error_entry_not_found").format(entryId))

            // Parse current data
            val currentData = try {
                JSONObject(entry.data)
            } catch (e: Exception) {
                JSONObject()
            }

            if (success) {
                // Transcription successful
                val fullText = results["fullText"] as String
                // Note: segmentsTexts from results not extracted - not stored to avoid duplication

                LogManager.service("Phase 3: Saving successful transcription (${fullText.length} chars)")

                // Update field value
                currentData.put(fieldName, fullText)

                // Update metadata
                // Note: segments_texts NOT stored to avoid data duplication (fullText already in field)
                val metadata = currentData.optJSONObject("transcription_metadata") ?: JSONObject()
                val fieldMetadata = JSONObject().apply {
                    put("audio_file", audioFile)
                    put("segments_timestamps", JSONArray().apply {
                        segments.forEach { segment ->
                            put(JSONObject().apply {
                                put("start", segment.start)
                                put("end", segment.end)
                            })
                        }
                    })
                    put("status", "completed")
                    put("model", modelId)
                    put("date", System.currentTimeMillis())
                    // segments_texts intentionally NOT stored - would double data size
                }
                metadata.put(fieldName, fieldMetadata)
                currentData.put("transcription_metadata", metadata)

            } else {
                // Transcription failed
                val error = results["error"] as String

                LogManager.service("Phase 3: Saving failed transcription (error: $error)", "WARN")

                // Update metadata with error
                val metadata = currentData.optJSONObject("transcription_metadata") ?: JSONObject()
                val fieldMetadata = JSONObject().apply {
                    put("audio_file", audioFile)
                    put("segments_timestamps", JSONArray().apply {
                        segments.forEach { segment ->
                            put(JSONObject().apply {
                                put("start", segment.start)
                                put("end", segment.end)
                            })
                        }
                    })
                    put("status", "failed")
                    put("model", modelId)
                    put("date", System.currentTimeMillis())
                    put("error", error)
                }
                metadata.put(fieldName, fieldMetadata)
                currentData.put("transcription_metadata", metadata)
            }

            // Update entry in database
            val updatedEntry = entry.copy(data = currentData.toString())
            database.toolDataDao().update(updatedEntry)

            // Get tool instance to find zone ID
            val toolInstance = database.toolInstanceDao().getToolInstanceById(toolInstanceId)
            val zoneId = toolInstance?.zone_id ?: ""

            // Notify data change
            DataChangeNotifier.notifyToolDataChanged(toolInstanceId, zoneId)

            // Cleanup
            cleanup(operationId)

            LogManager.service("Phase 3 complete: Transcription results saved for entry=$entryId")

            return if (success) {
                OperationResult.success(mapOf(
                    "entryId" to entryId,
                    "fieldName" to fieldName,
                    "status" to "completed",
                    "textLength" to (results["fullText"] as String).length
                ))
            } else {
                OperationResult.success(mapOf(
                    "entryId" to entryId,
                    "fieldName" to fieldName,
                    "status" to "failed",
                    "error" to (results["error"] as? String ?: "Unknown error")
                ))
            }

        } catch (e: Exception) {
            LogManager.service("Phase 3 failed: ${e.message}", "ERROR", e)
            cleanup(operationId)
            return OperationResult.error(s.shared("error_generic").format(e.message ?: ""))
        }
    }

    // ========================================================================================
    // Single-Step Operations
    // ========================================================================================

    private suspend fun cancelTranscription(
        params: JSONObject,
        token: CancellationToken
    ): OperationResult {
        val entryId = params.optString("entryId").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_required").format("entryId"))
        val fieldName = params.optString("fieldName").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_required").format("fieldName"))

        LogManager.service("Cancelling transcription for entry=$entryId, field=$fieldName")

        // Find active operation for this entry+field
        val operationId = activeTranscriptions.entries
            .find { it.value == Pair(entryId, fieldName) }
            ?.key

        if (operationId != null) {
            cleanup(operationId)
            LogManager.service("Cancelled active transcription operation: $operationId")
        }

        // TODO: Cancel token for this operation if possible
        // For now, we just cleanup the tracking data

        return OperationResult.success(mapOf(
            "entryId" to entryId,
            "fieldName" to fieldName,
            "cancelled" to true
        ))
    }

    private suspend fun listPendingTranscriptions(
        params: JSONObject,
        token: CancellationToken
    ): OperationResult {
        LogManager.service("Listing pending transcriptions")

        try {
            val database = AppDatabase.getDatabase(context)
            val allEntries = database.toolDataDao().getAllEntries()

            val pendingList = mutableListOf<PendingTranscription>()

            for (entry in allEntries) {
                if (token.isCancelled) return OperationResult.cancelled()

                try {
                    val data = JSONObject(entry.data)
                    val metadata = data.optJSONObject("transcription_metadata") ?: continue

                    // Check each field in metadata
                    val keys = metadata.keys()
                    while (keys.hasNext()) {
                        val fieldName = keys.next()
                        val fieldMetadata = metadata.optJSONObject(fieldName) ?: continue
                        val status = fieldMetadata.optString("status")

                        if (status == "pending") {
                            // Parse segments
                            val segmentsJson = fieldMetadata.optJSONArray("segments_timestamps") ?: continue
                            val segments = mutableListOf<TimeSegment>()
                            for (i in 0 until segmentsJson.length()) {
                                val seg = segmentsJson.getJSONObject(i)
                                segments.add(
                                    TimeSegment(
                                        start = seg.optDouble("start", 0.0).toFloat(),
                                        end = seg.optDouble("end", 0.0).toFloat()
                                    )
                                )
                            }

                            pendingList.add(
                                PendingTranscription(
                                    entryId = entry.id,
                                    toolInstanceId = entry.toolInstanceId,
                                    tooltype = entry.tooltype,
                                    fieldName = fieldName,
                                    audioFile = fieldMetadata.optString("audio_file"),
                                    segmentsTimestamps = segments,
                                    model = fieldMetadata.optString("model")
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    LogManager.service("Error parsing entry ${entry.id}: ${e.message}", "WARN")
                    continue
                }
            }

            LogManager.service("Found ${pendingList.size} pending transcriptions")

            return OperationResult.success(mapOf(
                "pending" to pendingList.map { pending ->
                    mapOf(
                        "entryId" to pending.entryId,
                        "toolInstanceId" to pending.toolInstanceId,
                        "tooltype" to pending.tooltype,
                        "fieldName" to pending.fieldName,
                        "audioFile" to pending.audioFile,
                        "model" to pending.model,
                        "segmentsTimestamps" to pending.segmentsTimestamps.map { segment ->
                            mapOf(
                                "start" to segment.start.toDouble(),
                                "end" to segment.end.toDouble()
                            )
                        }
                    )
                },
                "count" to pendingList.size
            ))

        } catch (e: Exception) {
            LogManager.service("Failed to list pending transcriptions: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("error_generic").format(e.message ?: ""))
        }
    }

    private suspend fun getTranscriptionStatus(
        params: JSONObject,
        token: CancellationToken
    ): OperationResult {
        val entryId = params.optString("entryId").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_required").format("entryId"))
        val fieldName = params.optString("fieldName").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_required").format("fieldName"))

        LogManager.service("Getting transcription status for entry=$entryId, field=$fieldName")

        try {
            val database = AppDatabase.getDatabase(context)
            val entry = database.toolDataDao().getById(entryId)
                ?: return OperationResult.error(s.shared("error_entry_not_found").format(entryId))

            val data = JSONObject(entry.data)
            val metadata = data.optJSONObject("transcription_metadata")
            val fieldMetadata = metadata?.optJSONObject(fieldName)

            if (fieldMetadata == null) {
                return OperationResult.success(mapOf(
                    "hasTranscription" to false
                ))
            }

            val status = fieldMetadata.optString("status")
            val error = fieldMetadata.optString("error", null)

            return OperationResult.success(mapOf(
                "hasTranscription" to true,
                "status" to status,
                "error" to error,
                "audioFile" to fieldMetadata.optString("audio_file"),
                "model" to fieldMetadata.optString("model"),
                "date" to fieldMetadata.optLong("date")
            ))

        } catch (e: Exception) {
            LogManager.service("Failed to get transcription status: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("error_generic").format(e.message ?: ""))
        }
    }

    // ========================================================================================
    // Utilities
    // ========================================================================================

    private fun cleanup(operationId: String) {
        tempData.remove(operationId)
        activeTranscriptions.remove(operationId)
        LogManager.service("Cleaned up transcription operation: $operationId")
    }

    /**
     * Verbalize transcription operation
     */
    override fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)
        return s.shared("action_verbalize_unknown")
    }
}
