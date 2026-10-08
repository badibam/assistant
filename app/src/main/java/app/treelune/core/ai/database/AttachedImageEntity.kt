package app.treelune.core.ai.database

import androidx.room.*

/**
 * An image joined to a message of a session: a reduced JPEG kept as a file of its own,
 * `files/attachments/<id>.jpg` (AttachedImages), which this row describes. The path follows from
 * the id: none is stored. It belongs to its session: its row goes with it by cascade, its file by
 * the service that deletes the session; an image taken off the composer before sending is deleted
 * then.
 *
 * The file is written before the row and deleted after it, so an orphan can only be a file, which
 * the startup sweep removes; a row without its file is an error, never repaired.
 */
@Entity(
    tableName = "attached_images",
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
data class AttachedImageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    /** The size of its file, the reduced JPEG */
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    val width: Int,
    val height: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Dao
interface AttachedImageDao {
    @Query("SELECT * FROM attached_images WHERE id = :id")
    suspend fun getById(id: String): AttachedImageEntity?

    @Query("SELECT * FROM attached_images WHERE session_id = :sessionId")
    suspend fun getForSession(sessionId: String): List<AttachedImageEntity>

    /** Every image's id, for the startup sweep to tell the files no row names. */
    @Query("SELECT id FROM attached_images")
    suspend fun allIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(image: AttachedImageEntity)

    @Query("DELETE FROM attached_images WHERE id = :id")
    suspend fun delete(id: String)
}
