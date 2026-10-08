package app.treelune.core.services

import android.content.Context
import app.treelune.core.ai.database.AttachedFileEntity
import app.treelune.core.ai.database.AttachedImageEntity
import app.treelune.core.ai.enrichments.AttachedImages
import app.treelune.core.ai.database.getById
import app.treelune.core.coordinator.CancellationToken
import app.treelune.core.database.AppDatabase
import app.treelune.core.strings.Strings
import org.json.JSONObject
import java.util.UUID

/**
 * The files joined to messages (docs/design/missing-tools.md, « L'import »): text read once
 * when it is joined, kept with its session (AttachedFileEntity).
 *
 * - attach: `session_id`, `name`, `mime_type`, `content`: the file kept; its `id` and `line_count`.
 *   A content that is not text (a NUL character in it) is refused.
 * - read: `id`, and `start_line` (from 1) and `lines` to read a part: that part's text, with the
 *   file's name, type, size and line count. Without them, the whole file.
 * - delete: `id`: a file taken off the composer before its message went.
 *
 * And the images joined to messages (docs/design/message-images.md), a reduced JPEG file each
 * (AttachedImages), described by a row (AttachedImageEntity):
 * - attach_image: `session_id`, `uri` (the photo taken or picked): the image prepared and kept,
 *   its file written before its row; its `id`, `width`, `height` and `size_bytes`.
 * - delete_image: `id`: an image taken off the composer; its row deleted before its file.
 */
class FileService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(context = context)
    private val dao = AppDatabase.getDatabase(context).attachedFileDao()
    private val imageDao = AppDatabase.getDatabase(context).attachedImageDao()

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        return when (operation) {
            "attach" -> attach(params)
            "read" -> read(params)
            "delete" -> {
                val id = params.optString("id").takeIf { it.isNotEmpty() } ?: return OperationResult.error(s.shared("service_error_missing_id"))
                dao.delete(id)
                OperationResult.success()
            }
            "attach_image" -> attachImage(params)
            "delete_image" -> {
                val id = params.optString("id").takeIf { it.isNotEmpty() } ?: return OperationResult.error(s.shared("service_error_missing_id"))
                imageDao.delete(id)
                AttachedImages.delete(context, id)
                OperationResult.success()
            }
            else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
        }
    }

    private suspend fun attachImage(params: JSONObject): OperationResult {
        val sessionId = params.optString("session_id").takeIf { it.isNotEmpty() } ?: return OperationResult.error(s.shared("service_error_missing_required_params").format("session_id"))
        val uri = params.optString("uri").takeIf { it.isNotEmpty() } ?: return OperationResult.error(s.shared("service_error_missing_required_params").format("uri"))
        val prepared = try {
            AttachedImages.prepare(context.contentResolver, android.net.Uri.parse(uri))
        } catch (e: Exception) {
            return OperationResult.error(s.shared("image_error_not_read").format(e.message ?: ""))
        }
        val image = AttachedImageEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            sizeBytes = prepared.bytes.size.toLong(),
            width = prepared.width,
            height = prepared.height,
            createdAt = System.currentTimeMillis()
        )
        // The file first: an interruption between the two leaves a file no row names, which the
        // startup sweep removes, never a row without its file
        AttachedImages.write(context, image.id, prepared.bytes)
        imageDao.insert(image)
        return OperationResult.success(mapOf("id" to image.id, "width" to image.width, "height" to image.height, "size_bytes" to image.sizeBytes))
    }

    private suspend fun attach(params: JSONObject): OperationResult {
        val sessionId = params.optString("session_id").takeIf { it.isNotEmpty() } ?: return OperationResult.error(s.shared("service_error_missing_required_params").format("session_id"))
        val name = params.optString("name").takeIf { it.isNotEmpty() } ?: return OperationResult.error(s.shared("service_error_missing_required_params").format("name"))
        val content = params.optString("content")
        if (content.contains('\u0000')) return OperationResult.error(s.shared("file_error_not_text").format(name))
        val file = AttachedFileEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            name = name,
            mimeType = params.optString("mime_type").ifEmpty { "text/plain" },
            sizeBytes = content.toByteArray(Charsets.UTF_8).size.toLong(),
            lineCount = FileLines.count(content),
            content = content,
            createdAt = System.currentTimeMillis()
        )
        dao.insert(file)
        return OperationResult.success(mapOf("id" to file.id, "line_count" to file.lineCount, "size_bytes" to file.sizeBytes))
    }

    private suspend fun read(params: JSONObject): OperationResult {
        val id = params.optString("id").takeIf { it.isNotEmpty() } ?: return OperationResult.error(s.shared("service_error_missing_id"))
        val file = dao.getById(id) ?: return OperationResult.error(s.shared("file_error_not_found").format(id))
        val start = if (params.has("start_line")) params.getInt("start_line") else 1
        val count = if (params.has("lines")) params.getInt("lines") else null
        if (start < 1 || (count != null && count < 1)) return OperationResult.error(s.shared("file_error_range").format(start, count ?: 0))
        val part = FileLines.window(file.content, start, count)
        return OperationResult.success(mapOf(
            "id" to file.id,
            "name" to file.name,
            "mime_type" to file.mimeType,
            "line_count" to file.lineCount,
            "size_bytes" to file.sizeBytes,
            "start_line" to start,
            "lines" to part.lines,
            "text" to part.text
        ))
    }

    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)
        return when (operation) {
            "attach" -> s.shared("action_verbalize_files_attach")
            "read" -> s.shared("action_verbalize_files_read")
            "delete" -> s.shared("action_verbalize_files_delete")
            "attach_image" -> s.shared("action_verbalize_files_attach_image")
            "delete_image" -> s.shared("action_verbalize_files_delete_image")
            else -> s.shared("action_verbalize_unknown")
        }
    }
}

/** A file's text counted and cut by lines, whatever ends them (\n, \r\n, \r). */
object FileLines {

    /** A part of a file: its text, and how many lines it holds. */
    data class Part(val text: String, val lines: Int)

    private val BREAK = Regex("\r\n|\r|\n")

    /** The lines of [content]; a last line break closes the last line, it does not open one. */
    fun split(content: String): List<String> =
        if (content.isEmpty()) emptyList() else content.split(BREAK).let { if (it.last().isEmpty()) it.dropLast(1) else it }

    fun count(content: String): Int = split(content).size

    /** The [count] lines from line [start] (from 1), all the rest when [count] is null; nothing past the end. */
    fun window(content: String, start: Int, count: Int?): Part {
        val all = split(content)
        val from = (start - 1).coerceAtMost(all.size)
        val to = if (count == null) all.size else (from + count).coerceAtMost(all.size)
        val kept = all.subList(from, to)
        return Part(kept.joinToString("\n"), kept.size)
    }
}
