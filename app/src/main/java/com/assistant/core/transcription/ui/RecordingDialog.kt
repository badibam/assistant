package com.assistant.core.transcription.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.assistant.core.strings.Strings
import com.assistant.core.transcription.models.TimeSegment
import com.assistant.core.ui.*
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

/**
 * Modal dialog for audio recording
 *
 * Pure UI component that uses RecordingController for business logic.
 * Displays:
 * - Timer (current recording duration)
 * - Audio level indicator (visual bars) - TODO when AudioRecorder implements getMaxAmplitude()
 * - Model name (discrete display)
 * - Controls (pause/resume, end of sentence, validate, cancel)
 *
 * Workflow:
 * - Dialog opens and starts recording automatically
 * - User can pause/resume, mark segments, validate or cancel
 * - On validate: calls onComplete with TimeSegments
 * - On cancel: deletes audio file and calls onDismiss
 *
 * @param audioFilePath Absolute path to output audio file
 * @param modelName Name of transcription model to display
 * @param onComplete Callback when recording validated (receives TimeSegments)
 * @param onDismiss Callback when dialog dismissed without validation
 */
@Composable
fun RecordingDialog(
    audioFilePath: String,
    modelName: String,
    onComplete: (List<TimeSegment>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    // Create controller (remember to survive recompositions)
    val controller = remember { RecordingController(context, audioFilePath) }

    // Collect state
    val state by controller.state.collectAsState()

    // Confirmation dialogs
    var showCancelConfirmation by remember { mutableStateOf(false) }
    var showValidateConfirmation by remember { mutableStateOf(false) }

    // Track if recording was validated (to avoid cleanup on validation)
    var wasValidated by remember { mutableStateOf(false) }

    // Lock screen orientation during recording to prevent rotation
    // (rotation would destroy the dialog and lose the active recording)
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val originalOrientation = activity?.requestedOrientation

        // Lock to current orientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED

        onDispose {
            // Restore original orientation when dialog closes
            originalOrientation?.let {
                activity.requestedOrientation = it
            }
        }
    }

    // Start recording on mount
    LaunchedEffect(Unit) {
        controller.start { success, error ->
            if (!success) {
                // Show error and dismiss
                UI.Toast(context, error ?: "Unknown error", Duration.LONG)
                onDismiss()
            }
        }
    }

    // Cleanup on unmount (only if not validated)
    DisposableEffect(Unit) {
        onDispose {
            if (!wasValidated) {
                // Recording was cancelled, cleanup and delete audio
                controller.cleanup()
            } else {
                // Recording was validated, just stop timer without deleting audio
                controller.stopTimerOnly()
            }
        }
    }

    // Handle error state
    LaunchedEffect(state.status) {
        if (state.status == RecordingStatus.ERROR) {
            state.errorMessage?.let { error ->
                UI.Toast(context, error, Duration.LONG)
            }
        }
    }

    // Helper function to format duration
    fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    Dialog(onDismissRequest = { showCancelConfirmation = true }) {
        UI.Card(
            type = CardType.DEFAULT,
            size = Size.L
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                UI.Text(
                    text = s.shared("transcription_dialog_title"),
                    type = TextType.TITLE
                )

                // Status indicator
                UI.Text(
                    text = when (state.status) {
                        RecordingStatus.RECORDING -> s.shared("transcription_recording")
                        RecordingStatus.PAUSED -> s.shared("transcription_paused")
                        RecordingStatus.IDLE -> s.shared("transcription_ready")
                        RecordingStatus.COMPLETED -> s.shared("transcription_completed")
                        RecordingStatus.ERROR -> s.shared("transcription_failed")
                    },
                    type = TextType.SUBTITLE
                )

                // Timer
                UI.Text(
                    text = s.shared("transcription_duration").format(formatDuration(state.durationMs)),
                    type = TextType.BODY
                )

                // Model name (discrete)
                UI.Text(
                    text = s.shared("transcription_model").format(modelName),
                    type = TextType.CAPTION
                )

                // Segments count
                UI.Text(
                    text = s.shared("transcription_segments").format(state.segments.size),
                    type = TextType.CAPTION
                )

                // TODO: Audio level indicator
                // Will be implemented when AudioRecorder.getMaxAmplitude() is functional
                // For now, state.audioLevel is always 0

                Spacer(modifier = Modifier.height(8.dp))

                // Controls - during recording
                if (state.status == RecordingStatus.RECORDING) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Pause button
                        UI.Button(
                            type = ButtonType.DEFAULT,
                            size = Size.M,
                            state = ComponentState.NORMAL,
                            onClick = { controller.pause() }
                        ) {
                            UI.Text(
                                text = s.shared("action_pause"),
                                type = TextType.BODY
                            )
                        }

                        // End of sentence button
                        UI.Button(
                            type = ButtonType.PRIMARY,
                            size = Size.M,
                            state = ComponentState.NORMAL,
                            onClick = { controller.markSegment() }
                        ) {
                            UI.Text(
                                text = s.shared("transcription_end_of_sentence"),
                                type = TextType.BODY
                            )
                        }
                    }
                }

                // Controls - during pause
                if (state.status == RecordingStatus.PAUSED) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Resume button
                        UI.Button(
                            type = ButtonType.PRIMARY,
                            size = Size.M,
                            state = ComponentState.NORMAL,
                            onClick = { controller.resume() }
                        ) {
                            UI.Text(
                                text = s.shared("action_resume"),
                                type = TextType.BODY
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom actions (always visible during recording/paused)
                if (state.status == RecordingStatus.RECORDING || state.status == RecordingStatus.PAUSED) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Cancel button
                        UI.Button(
                            type = ButtonType.DEFAULT,
                            size = Size.M,
                            state = ComponentState.NORMAL,
                            onClick = { showCancelConfirmation = true }
                        ) {
                            UI.Text(
                                text = s.shared("action_cancel"),
                                type = TextType.BODY
                            )
                        }

                        // Validate button
                        UI.Button(
                            type = ButtonType.PRIMARY,
                            size = Size.M,
                            state = if (state.durationMs > 0) ComponentState.NORMAL else ComponentState.DISABLED,
                            onClick = { showValidateConfirmation = true }
                        ) {
                            UI.Text(
                                text = s.shared("action_validate"),
                                type = TextType.BODY
                            )
                        }
                    }
                }
            }
        }
    }

    // Cancel confirmation dialog
    if (showCancelConfirmation) {
        UI.ConfirmDialog(
            title = s.shared("transcription_confirm_cancel_title"),
            message = s.shared("transcription_confirm_cancel_message"),
            onConfirm = {
                controller.cancel()
                showCancelConfirmation = false
                onDismiss()
            },
            onDismiss = {
                showCancelConfirmation = false
            }
        )
    }

    // Validate confirmation dialog
    if (showValidateConfirmation) {
        UI.ConfirmDialog(
            title = s.shared("transcription_confirm_validate_title"),
            message = s.shared("transcription_confirm_validate_message"),
            onConfirm = {
                showValidateConfirmation = false
                wasValidated = true  // Mark as validated before closing
                controller.validate { segments ->
                    onComplete(segments)
                }
            },
            onDismiss = {
                showValidateConfirmation = false
            }
        )
    }
}
