package com.assistant.core.transcription.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.strings.Strings
import com.assistant.core.transcription.models.TimeSegment
import com.assistant.core.ui.*
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
 *
 * @param label Field label
 * @param value Current text value (nullable)
 * @param onChange Callback when text changes (manual edit)
 * @param onRecordComplete Callback when recording validated (receives TimeSegments)
 * @param audioFilePath Path to audio file (if exists)
 * @param transcriptionStatus Current transcription status
 * @param modelName Model name to display during recording
 * @param enabled Whether field is editable
 * @param required Whether field is required
 */
@Composable
fun TranscribableTextField(
    label: String,
    value: String?,
    onChange: (String) -> Unit,
    onRecordComplete: (List<TimeSegment>) -> Unit,
    audioFilePath: String,
    transcriptionStatus: TranscriptionStatus? = null,
    modelName: String,
    enabled: Boolean = true,
    required: Boolean = false
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    // Recording dialog state
    var showRecordingDialog by remember { mutableStateOf(false) }
    var showRerecordConfirmation by remember { mutableStateOf(false) }

    // Check if audio file exists
    val audioExists = remember(audioFilePath) {
        File(audioFilePath).exists()
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
                            // Direct recording
                            showRecordingDialog = true
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
                onRecordComplete(segments)
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
                showRecordingDialog = true
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
