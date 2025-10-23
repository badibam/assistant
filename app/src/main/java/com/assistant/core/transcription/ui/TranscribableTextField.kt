package com.assistant.core.transcription.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.strings.Strings
import com.assistant.core.transcription.models.TimeSegment
import com.assistant.core.transcription.models.TranscriptionContext
import com.assistant.core.ui.*
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Text field with integrated transcription support
 *
 * Displays a text field that can be populated via:
 * - Manual typing
 * - Audio recording + transcription
 *
 * Features:
 * - Record audio button (mic icon)
 * - Play audio button (if audio exists)
 * - Transcription status display
 * - Re-recording with confirmation
 * - Automatic transcription triggering (if autoTranscribe && transcriptionContext provided)
 *
 * @param label Field label
 * @param value Current text value (nullable)
 * @param onChange Callback when text changes (manual edit)
 * @param audioFilePath Path to audio file (if exists)
 * @param transcriptionStatus Current transcription status
 * @param modelName Model name to display during recording
 * @param enabled Whether field is editable
 * @param required Whether field is required
 * @param autoTranscribe If true, automatically trigger transcription after recording (default: true)
 * @param transcriptionContext Context info for auto-transcription (required if autoTranscribe=true)
 * @param onTranscriptionStatusChange Callback when transcription status changes
 * @param onRecordComplete Optional callback when recording validated (receives TimeSegments) - for custom logic
 */
@Composable
fun TranscribableTextField(
    label: String,
    value: String?,
    onChange: (String) -> Unit,
    audioFilePath: String,
    transcriptionStatus: TranscriptionStatus? = null,
    modelName: String,
    enabled: Boolean = true,
    required: Boolean = false,
    autoTranscribe: Boolean = true,
    transcriptionContext: TranscriptionContext? = null,
    onTranscriptionStatusChange: ((TranscriptionStatus?) -> Unit)? = null,
    onRecordComplete: ((List<TimeSegment>) -> Unit)? = null
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { Coordinator(context) }
    val coroutineScope = rememberCoroutineScope()

    // Recording dialog state
    var showRecordingDialog by remember { mutableStateOf(false) }
    var showRerecordConfirmation by remember { mutableStateOf(false) }

    // Audio permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, open recording dialog
            showRecordingDialog = true
        } else {
            // Permission denied, show error message
            UI.Toast(context, s.shared("transcription_permission_denied"), Duration.LONG)
        }
    }

    // Check if audio file exists
    val audioExists = remember(audioFilePath) {
        File(audioFilePath).exists()
    }

    // Helper function to check and request permission
    fun requestRecordingPermission() {
        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            // Permission already granted, open dialog directly
            showRecordingDialog = true
        } else {
            // Request permission
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Helper function to trigger transcription automatically
    fun triggerTranscription(segments: List<TimeSegment>) {
        if (!autoTranscribe || transcriptionContext == null) {
            LogManager.ui("Auto-transcription disabled or context missing")
            return
        }

        LogManager.ui("Auto-triggering transcription: ${segments.size} segments")
        onTranscriptionStatusChange?.invoke(TranscriptionStatus.PENDING)

        coroutineScope.launch {
            try {
                // Convert segments to JSONArray
                val segmentsJson = JSONArray()
                segments.forEach { segment ->
                    val segmentObj = JSONObject()
                    segmentObj.put("start", segment.start.toDouble())
                    segmentObj.put("end", segment.end.toDouble())
                    segmentsJson.put(segmentObj)
                }

                // Extract filename from path
                val audioFileName = File(audioFilePath).name

                val params = mapOf(
                    "entryId" to transcriptionContext.entryId,
                    "toolInstanceId" to transcriptionContext.toolInstanceId,
                    "tooltype" to transcriptionContext.tooltype,
                    "fieldName" to transcriptionContext.fieldName,
                    "audioFile" to audioFileName,
                    "segmentsTimestamps" to segmentsJson
                )

                LogManager.ui("Starting transcription with params: $params")
                val result = coordinator.processUserAction("transcription.start", params)

                if (result?.isSuccess == true) {
                    LogManager.ui("Transcription started successfully")
                    // Status will be updated when transcription completes
                } else {
                    LogManager.ui("Transcription failed: ${result?.error}", "ERROR")
                    onTranscriptionStatusChange?.invoke(TranscriptionStatus.FAILED)
                    UI.Toast(context, s.shared("error_transcription_start"), Duration.LONG)
                }
            } catch (e: Exception) {
                LogManager.ui("Error starting transcription: ${e.message}", "ERROR")
                onTranscriptionStatusChange?.invoke(TranscriptionStatus.FAILED)
                UI.Toast(context, s.shared("error_transcription_start"), Duration.LONG)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Label row with status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Label
            UI.Text(
                text = if (required) "$label *" else label,
                type = TextType.SUBTITLE
            )

            // Transcription status
            transcriptionStatus?.let { status ->
                UI.Text(
                    text = when (status) {
                        TranscriptionStatus.PENDING -> s.shared("transcription_processing")
                        TranscriptionStatus.COMPLETED -> s.shared("transcription_completed")
                        TranscriptionStatus.FAILED -> s.shared("transcription_failed")
                    },
                    type = TextType.CAPTION
                )
            }
        }

        // Text field
        UI.FormField(
            label = "", // Label already shown above
            value = value ?: "",
            onChange = onChange,
            fieldType = FieldType.TEXT_MEDIUM,
            required = required,
            state = when {
                transcriptionStatus == TranscriptionStatus.FAILED -> ComponentState.ERROR
                !enabled -> ComponentState.DISABLED
                else -> ComponentState.NORMAL
            },
            readonly = transcriptionStatus == TranscriptionStatus.PENDING
        )

        // Action buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Record/Re-record button
            if (transcriptionStatus != TranscriptionStatus.PENDING) {
                UI.Button(
                    type = ButtonType.PRIMARY,
                    size = Size.S,
                    state = if (enabled) ComponentState.NORMAL else ComponentState.DISABLED,
                    onClick = {
                        if (value != null && value.isNotEmpty()) {
                            // Show re-record confirmation
                            showRerecordConfirmation = true
                        } else {
                            // Request permission and start recording
                            requestRecordingPermission()
                        }
                    }
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        UI.Text(
                            text = "🎤", // Mic icon
                            type = TextType.BODY
                        )
                        UI.Text(
                            text = if (value != null && value.isNotEmpty()) {
                                s.shared("transcription_record") // "Ré-enregistrer" logic handled by confirmation
                            } else {
                                s.shared("transcription_record")
                            },
                            type = TextType.BODY
                        )
                    }
                }
            }

            // Play audio button (if audio exists)
            if (audioExists && transcriptionStatus != TranscriptionStatus.PENDING) {
                UI.Button(
                    type = ButtonType.DEFAULT,
                    size = Size.S,
                    state = ComponentState.NORMAL,
                    onClick = {
                        // TODO: Implement audio playback
                        // For now, just show a toast
                        UI.Toast(context, "Audio playback not yet implemented", Duration.SHORT)
                    }
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        UI.Text(
                            text = "▶", // Play icon
                            type = TextType.BODY
                        )
                        UI.Text(
                            text = s.shared("transcription_play_audio"),
                            type = TextType.BODY
                        )
                    }
                }
            }

            // Retry button (if failed)
            if (transcriptionStatus == TranscriptionStatus.FAILED) {
                UI.Button(
                    type = ButtonType.SECONDARY,
                    size = Size.S,
                    state = ComponentState.NORMAL,
                    onClick = {
                        // Re-trigger transcription with existing audio
                        // This will be handled by the parent component
                        UI.Toast(context, "Retry functionality to be implemented by parent", Duration.SHORT)
                    }
                ) {
                    UI.Text(
                        text = s.shared("transcription_retry"),
                        type = TextType.BODY
                    )
                }
            }

            // Loading indicator (if pending)
            if (transcriptionStatus == TranscriptionStatus.PENDING) {
                UI.LoadingIndicator(size = Size.S)
            }
        }
    }

    // Recording dialog
    if (showRecordingDialog) {
        RecordingDialog(
            audioFilePath = audioFilePath,
            modelName = modelName,
            onComplete = { segments ->
                showRecordingDialog = false

                // Trigger auto-transcription if enabled
                triggerTranscription(segments)

                // Also call custom callback if provided
                onRecordComplete?.invoke(segments)
            },
            onDismiss = {
                showRecordingDialog = false
            }
        )
    }

    // Re-record confirmation
    if (showRerecordConfirmation) {
        UI.ConfirmDialog(
            title = s.shared("transcription_confirm_rerecord_title"),
            message = s.shared("transcription_confirm_rerecord_message"),
            onConfirm = {
                showRerecordConfirmation = false
                requestRecordingPermission()
            },
            onDismiss = {
                showRerecordConfirmation = false
            }
        )
    }
}

/**
 * Transcription status for UI display
 */
enum class TranscriptionStatus {
    PENDING,    // Transcription in progress
    COMPLETED,  // Transcription finished successfully
    FAILED      // Transcription failed (error)
}
