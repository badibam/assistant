package app.treelune.core.mcp

import androidx.room.*
import app.treelune.core.database.AppDatabase
import org.json.JSONArray

/**
 * A client of the MCP server that registered itself (OAuthClient). Kept on this phone alone: a
 * backup does not carry it, and restoring one or resetting the app removes every client, which
 * then has to be authorized again.
 */
@Entity(tableName = "mcp_clients")
data class McpClientEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** The addresses it declared, as a JSON array */
    @ColumnInfo(name = "redirect_uris") val redirectUris: String,
    @ColumnInfo(name = "secret_hash") val secretHash: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "last_used_at") val lastUsedAt: Long?,
    /** What it may reach, AccessMask's stored form; an empty list reaches everything */
    @ColumnInfo(name = "access_json") val accessJson: String = "[]"
)

/** A token handed to a client, as its hash (StoredToken); gone with its client. */
@Entity(
    tableName = "mcp_tokens",
    foreignKeys = [ForeignKey(entity = McpClientEntity::class, parentColumns = ["id"], childColumns = ["client_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["client_id"])]
)
data class McpTokenEntity(
    @PrimaryKey val hash: String,
    @ColumnInfo(name = "client_id") val clientId: String,
    /** TokenKind */
    val kind: String,
    @ColumnInfo(name = "expires_at") val expiresAt: Long,
    /** StoredToken.replaces */
    val replaces: String?
)

@Dao
interface McpDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertClient(client: McpClientEntity)

    @Query("SELECT * FROM mcp_clients WHERE id = :id")
    suspend fun client(id: String): McpClientEntity?

    @Query("SELECT * FROM mcp_clients ORDER BY created_at")
    suspend fun clients(): List<McpClientEntity>

    @Query("DELETE FROM mcp_clients WHERE id = :id")
    suspend fun deleteClient(id: String)

    @Query("UPDATE mcp_clients SET last_used_at = :at WHERE id = :id")
    suspend fun touchClient(id: String, at: Long)

    @Query("SELECT access_json FROM mcp_clients WHERE id = :id")
    suspend fun access(id: String): String?

    @Query("UPDATE mcp_clients SET access_json = :access WHERE id = :id")
    suspend fun setAccess(id: String, access: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertToken(token: McpTokenEntity)

    @Query("SELECT * FROM mcp_tokens WHERE hash = :hash")
    suspend fun token(hash: String): McpTokenEntity?

    @Query("DELETE FROM mcp_tokens WHERE hash = :hash")
    suspend fun deleteToken(hash: String)

    @Query("DELETE FROM mcp_tokens WHERE replaces = :hash")
    suspend fun deleteReplacing(hash: String)

    @Query("UPDATE mcp_tokens SET replaces = NULL WHERE replaces = :replaced")
    suspend fun clearReplaces(replaced: String)

    @Query("DELETE FROM mcp_tokens WHERE expires_at <= :now")
    suspend fun deleteExpired(now: Long)
}

/** The OAuth server's clients and tokens, in the app's database. */
/**
 * What a client of the connector may reach (AccessMask, docs/design/validation.md), set by the
 * user in the external access screen alone: the server never writes it.
 */
object McpClientAccess {

    /** Client [clientId]'s access; one gone or unreadable reaches nothing. */
    suspend fun read(context: android.content.Context, clientId: String): app.treelune.core.access.AccessMask {
        val stored = AppDatabase.getDatabase(context).mcpDao().access(clientId) ?: return app.treelune.core.access.AccessMask.NOTHING
        return try {
            app.treelune.core.access.AccessMask.fromJson(stored)
        } catch (e: Exception) {
            app.treelune.core.utils.LogManager.service("Access of MCP client $clientId unreadable: ${e.message}", "ERROR", e)
            app.treelune.core.access.AccessMask.NOTHING
        }
    }

    suspend fun write(context: android.content.Context, clientId: String, access: app.treelune.core.access.AccessMask) =
        AppDatabase.getDatabase(context).mcpDao().setAccess(clientId, access.toJson().toString())
}

class RoomOAuthStore(private val dao: McpDao) : OAuthStore {

    constructor(database: AppDatabase) : this(database.mcpDao())

    override suspend fun addClient(client: OAuthClient) = dao.insertClient(
        McpClientEntity(client.id, client.name, JSONArray(client.redirectUris).toString(), client.secretHash, client.createdAt, client.lastUsedAt)
    )

    override suspend fun client(id: String): OAuthClient? = dao.client(id)?.toClient()

    override suspend fun clients(): List<OAuthClient> = dao.clients().map { it.toClient() }

    // Its tokens go with it (ForeignKey.CASCADE)
    override suspend fun removeClient(id: String) = dao.deleteClient(id)

    override suspend fun touchClient(id: String, at: Long) = dao.touchClient(id, at)

    override suspend fun addToken(token: StoredToken) = dao.insertToken(McpTokenEntity(token.hash, token.clientId, token.kind.name, token.expiresAt, token.replaces))

    override suspend fun token(hash: String): StoredToken? = dao.token(hash)?.let { StoredToken(it.hash, it.clientId, TokenKind.valueOf(it.kind), it.expiresAt, it.replaces) }

    override suspend fun removeToken(hash: String) = dao.deleteToken(hash)

    override suspend fun removeReplacing(hash: String) = dao.deleteReplacing(hash)

    // Two steps with no transaction: stopped between them, the pair still names a token gone,
    // and its next use settles again, to the same end
    override suspend fun settle(replaced: String) {
        dao.deleteToken(replaced)
        dao.clearReplaces(replaced)
    }

    override suspend fun removeExpired(now: Long) = dao.deleteExpired(now)

    private fun McpClientEntity.toClient() = OAuthClient(
        id = id,
        name = name,
        redirectUris = JSONArray(redirectUris).let { a -> (0 until a.length()).map { a.getString(it) } },
        secretHash = secretHash,
        createdAt = createdAt,
        lastUsedAt = lastUsedAt
    )
}
