package com.assistant.core.ai.database

import androidx.room.*

/**
 * A text file joined to a message of a session (docs/design/missing-tools.md, « L'import »):
 * read once when it is joined, kept here, so the composer holds only its id and the AI can ask
 * for it again (FILE, IMPORT_PLAN, IMPORT_DATA). It belongs to its session: deleted with it,
 * saved in backups with it; a file taken off the composer before sending is deleted then.
 */
@Entity(
    tableName = "attached_files",
    foreignKeys = [
        ForeignKey(
            entity = AISessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["session_id"])]
)
data class AttachedFileEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    /** Its name on the phone, "aliments.csv" */
    val name: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "line_count") val lineCount: Int,
    val content: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

/**
 * The files joined to messages. A file's content is read in parts: Android reads a row of the
 * database in one window of 2 MB at most, and a file can be bigger (getById, getForSession).
 */
@Dao
interface AttachedFileDao {
    /** The file without its content, which stays empty. */
    @Query("SELECT id, session_id, name, mime_type, size_bytes, line_count, '' AS content, created_at FROM attached_files WHERE id = :id")
    suspend fun getInfo(id: String): AttachedFileEntity?

    @Query("SELECT id FROM attached_files WHERE session_id = :sessionId")
    suspend fun idsForSession(sessionId: String): List<String>

    /** [length] characters of the file's content from character [start], counted from 1. */
    @Query("SELECT substr(content, :start, :length) FROM attached_files WHERE id = :id")
    suspend fun contentPart(id: String, start: Int, length: Int): String?

    /** The length of the file's content, in characters. */
    @Query("SELECT length(content) FROM attached_files WHERE id = :id")
    suspend fun contentLength(id: String): Int?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(file: AttachedFileEntity)

    @Query("DELETE FROM attached_files WHERE id = :id")
    suspend fun delete(id: String)
}

// A part of 250 000 characters is 1 MB at most in UTF-8, well under the window
private const val CONTENT_PART = 250_000

/** The file whole, its content read part by part. */
suspend fun AttachedFileDao.getById(id: String): AttachedFileEntity? {
    val info = getInfo(id) ?: return null
    val length = contentLength(id) ?: return null
    val content = StringBuilder(length)
    var start = 1
    while (start <= length) {
        content.append(contentPart(id, start, CONTENT_PART) ?: return null)
        start += CONTENT_PART
    }
    return info.copy(content = content.toString())
}

/** The files of session [sessionId], whole. */
suspend fun AttachedFileDao.getForSession(sessionId: String): List<AttachedFileEntity> =
    idsForSession(sessionId).mapNotNull { getById(it) }
