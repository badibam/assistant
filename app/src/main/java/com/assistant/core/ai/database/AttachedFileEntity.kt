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

@Dao
interface AttachedFileDao {
    @Query("SELECT * FROM attached_files WHERE id = :id")
    suspend fun getById(id: String): AttachedFileEntity?

    @Query("SELECT * FROM attached_files WHERE session_id = :sessionId")
    suspend fun getForSession(sessionId: String): List<AttachedFileEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(file: AttachedFileEntity)

    @Query("DELETE FROM attached_files WHERE id = :id")
    suspend fun delete(id: String)
}
