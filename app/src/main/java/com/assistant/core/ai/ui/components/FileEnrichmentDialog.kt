package com.assistant.core.ai.ui.components

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.assistant.core.ai.enrichments.FileEnrichment
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.strings.Strings
import com.assistant.core.ui.DialogType
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.core.utils.FormatUtils
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** What the picker opens on: text, a CSV whatever type the phone gives it, JSON. */
private val TEXT_TYPES = arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/json")

/**
 * The FILE enrichment's dialog (docs/design/missing-tools.md, « L'import »). A new one opens the
 * phone's file picker; the file picked is read once and kept by the files service with the
 * session, so only its id is held here. Then what will go: its name, type, size, line count and
 * first lines, and « Include it whole », ticked. Cancelling a file just joined deletes it;
 * editing a joined one only changes whether it goes whole.
 *
 * @param sessionId The session the file is kept with
 */
@Composable
fun FileEnrichmentDialog(
    existingConfig: String?,
    sessionId: String,
    onDismiss: () -> Unit,
    onConfirm: (config: String) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { Coordinator(context) }
    val scope = rememberCoroutineScope()

    // The file joined, as the enrichment stores it; null until one is picked and kept
    var file by rememberSaveable { mutableStateOf(existingConfig) }
    // Whether this dialog joined it, so that cancelling takes it back
    var joinedHere by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(existingConfig == null) }
    var error by remember { mutableStateOf<String?>(null) }
    // Read back from the files service, never kept across a rotation
    var details by remember { mutableStateOf<Pair<String, String>?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        picking = false
        if (uri == null) { onDismiss(); return@rememberLauncherForActivityResult }
        scope.launch {
            val read = readText(uri, context)
            if (read.isFailure) { error = read.exceptionOrNull()?.message; return@launch }
            val (name, text) = read.getOrThrow()
            val result = coordinator.processUserAction("files.attach", mapOf(
                "session_id" to sessionId,
                "name" to name,
                "mime_type" to (context.contentResolver.getType(uri) ?: "text/plain"),
                "content" to text
            ))
            if (!result.isSuccess) { error = result.error; return@launch }
            file = FileEnrichment(result.data?.get("id") as String, name, (result.data?.get("line_count") as Number).toInt()).toJson().toString()
            joinedHere = true
        }
    }
    LaunchedEffect(Unit) { if (picking) picker.launch(TEXT_TYPES) }

    val current = file?.let { FileEnrichment.fromJson(it) }
    LaunchedEffect(current?.fileId) {
        val id = current?.fileId ?: return@LaunchedEffect
        val result = coordinator.processUserAction("files.read", mapOf("id" to id, "start_line" to 1, "lines" to PREVIEW_SHOWN))
        if (!result.isSuccess) { error = result.error; return@LaunchedEffect }
        val size = (result.data?.get("size_bytes") as? Number)?.toLong()
        details = listOfNotNull(
            result.data?.get("mime_type") as? String,
            size?.let { FormatUtils.formatFileSize(it, context) },
            s.shared("file_line_count").format(current.lineCount)
        ).joinToString(" · ") to (result.data?.get("text") as? String ?: "")
    }

    fun cancel() {
        val joined = current?.takeIf { joinedHere }
        if (joined != null) scope.launch {
            val result = coordinator.processUserAction("files.delete", mapOf("id" to joined.fileId))
            if (!result.isSuccess) LogManager.aiEnrichment("File ${joined.fileId} not deleted on cancel: ${result.error}", "ERROR")
        }
        onDismiss()
    }

    // Nothing to show while the picker is open
    if (current == null && error == null) return

    UI.Dialog(
        type = DialogType.CONFIRM,
        confirmEnabled = current != null,
        onCancel = { cancel() },
        onConfirm = { current?.let { onConfirm(it.toJson().toString()) } }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(UI.Space.M)
        ) {
            UI.Text(text = s.shared("file_dialog_title"), type = TextType.TITLE, fillMaxWidth = true)
            error?.let { UI.Text(text = it, type = TextType.ERROR) }
            if (current != null) {
                UI.Text(text = current.name, type = TextType.SUBTITLE)
                details?.let { (line, preview) ->
                    UI.Text(text = line, type = TextType.CAPTION)
                    UI.Text(text = s.shared("file_preview_label"), type = TextType.LABEL)
                    UI.Text(text = preview, type = TextType.CAPTION)
                }
                UI.Checkbox(
                    checked = current.whole,
                    onCheckedChange = { whole -> file = current.copy(whole = whole).toJson().toString() },
                    label = s.shared("file_include_whole")
                )
            }
        }
    }
}

/** The lines of a file shown in the dialog, fewer than the AI's preview: a glance. */
private const val PREVIEW_SHOWN = 8

/**
 * The name and the text of the file at [uri]. A file that is not UTF-8 text is refused, in words,
 * rather than read with replacement characters that would import as garbage.
 */
private fun readText(uri: Uri, context: android.content.Context): Result<Pair<String, String>> {
    val s = Strings.`for`(context = context)
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: uri.lastPathSegment ?: "file"
    return try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return Result.failure(IllegalStateException(s.shared("import_error_file").format(name)))
        val text = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
        Result.success(name to text.removePrefix("﻿"))
    } catch (e: CharacterCodingException) {
        Result.failure(IllegalArgumentException(s.shared("file_error_encoding").format(name)))
    } catch (e: java.io.IOException) {
        Result.failure(IllegalStateException(s.shared("import_error_file").format(e.message ?: name)))
    }
}
