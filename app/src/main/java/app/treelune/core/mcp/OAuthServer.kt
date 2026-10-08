package app.treelune.core.mcp

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * A client that registered itself (RFC 7591). Its name and its addresses are what it declares,
 * shown as such: nothing proves them. A confidential client has a secret, kept as its hash.
 */
data class OAuthClient(
    val id: String,
    val name: String,
    val redirectUris: List<String>,
    val secretHash: String?,
    val createdAt: Long,
    val lastUsedAt: Long?
)

/** What a token is for: calling the server, or getting a new access token. */
enum class TokenKind { ACCESS, REFRESH }

/** A token handed out, kept as its hash: the database never holds one that works. */
data class StoredToken(val hash: String, val clientId: String, val kind: TokenKind, val expiresAt: Long)

/** Where clients and tokens are kept (a Room table each, in the app). */
interface OAuthStore {
    suspend fun addClient(client: OAuthClient)
    suspend fun client(id: String): OAuthClient?
    suspend fun clients(): List<OAuthClient>
    /** Removes the client and every token it holds. */
    suspend fun removeClient(id: String)
    suspend fun touchClient(id: String, at: Long)
    suspend fun addToken(token: StoredToken)
    suspend fun token(hash: String): StoredToken?
    suspend fun removeToken(hash: String)
    /** Removes the tokens expired before [now]. */
    suspend fun removeExpired(now: Long)
}

/**
 * A request for access waiting for its code on the phone: what the client asked, the code the
 * page shows, and how it ends.
 */
data class PendingAuthorization(
    val requestId: String,
    val client: OAuthClient,
    val redirectUri: String,
    val codeChallenge: String,
    val state: String?,
    val code: String,
    val expiresAt: Long,
    val attempts: Int = 0,
    val outcome: Outcome = Outcome.WAITING,
    /** Where the page sends the browser once it ends: the client's address with the code, or the refusal */
    val redirect: String? = null
) {
    enum class Outcome { WAITING, APPROVED, REFUSED, EXPIRED }
}

/** What the phone shows of a request for access, and lets the person answer. */
interface ApprovalPrompt {
    /** A request waits for its code: tell the person. */
    fun ask(request: PendingAuthorization)
    /** The request ended, whichever way: the prompt goes. */
    fun withdraw(requestId: String)
}

/**
 * The app as its own OAuth 2.1 authorization server (docs/design/mcp-server.md, « L'autorisation »):
 * a client registers itself, the page it opens shows a code, the person types that code on the
 * phone, and the client gets its tokens. Any client and any return address are accepted and
 * shown: what keeps a stranger out is the code, which only the one who sees the page holds, and
 * PKCE, which binds the code given back to the one who asked.
 */
class OAuthServer(
    private val store: OAuthStore,
    private val prompt: ApprovalPrompt,
    private val now: () -> Long,
    private val random: SecureRandom = SecureRandom()
) {
    // Read by the relay's loop and by the phone's screen, each on its own thread
    @Volatile private var pending: PendingAuthorization? = null
    private val codes = java.util.concurrent.ConcurrentHashMap<String, IssuedCode>()

    /** An authorization code, good once, for a short while. */
    private data class IssuedCode(val clientId: String, val redirectUri: String, val codeChallenge: String, val expiresAt: Long)

    /** A failure as OAuth names it (RFC 6749 §5.2), with what to tell. */
    class OAuthError(val error: String, val description: String) : Exception(description)

    /** The tokens handed to a client. */
    data class Tokens(val access: String, val refresh: String, val expiresIn: Long)

    // ---------------------------------------------------------------- registration

    /**
     * Registers a client as it declares itself (RFC 7591). A client asking to authenticate at
     * the token endpoint (`client_secret_basic`, `client_secret_post`, which is also the default)
     * gets a secret; one asking `none` is public and relies on PKCE alone.
     *
     * @return The client, and its secret when it has one
     */
    suspend fun register(name: String?, redirectUris: List<String>, authMethod: String?): Pair<OAuthClient, String?> {
        if (redirectUris.isEmpty()) throw OAuthError("invalid_redirect_uri", "redirect_uris is required")
        if (redirectUris.any { !it.startsWith("https://") && !it.startsWith("http://localhost") && !it.startsWith("http://127.0.0.1") }) {
            throw OAuthError("invalid_redirect_uri", "A redirect URI is https, or http on this machine")
        }
        val method = authMethod ?: "client_secret_basic"
        if (method !in AUTH_METHODS) throw OAuthError("invalid_client_metadata", "token_endpoint_auth_method $method is not supported")
        val secret = if (method == "none") null else token()
        val client = OAuthClient(
            id = token(),
            name = name?.take(100)?.takeIf { it.isNotBlank() } ?: "?",
            redirectUris = redirectUris,
            secretHash = secret?.let(::hash),
            createdAt = now(),
            lastUsedAt = null
        )
        store.addClient(client)
        return client to secret
    }

    // ---------------------------------------------------------------- authorization

    /**
     * A request for access from the page a client opened: checked, then waiting for its code on
     * the phone, at most one at a time.
     *
     * @throws OAuthError when the client or its return address is unknown, when PKCE is missing,
     *   or when another request is already waiting
     */
    suspend fun authorize(clientId: String?, redirectUri: String?, responseType: String?, codeChallenge: String?, challengeMethod: String?, state: String?): PendingAuthorization {
        val client = clientId?.let { store.client(it) } ?: throw OAuthError("invalid_client", "Unknown client")
        if (redirectUri == null || redirectUri !in client.redirectUris) throw OAuthError("invalid_request", "Unknown redirect_uri")
        if (responseType != "code") throw OAuthError("unsupported_response_type", "response_type must be code")
        if (codeChallenge.isNullOrEmpty() || challengeMethod != "S256") throw OAuthError("invalid_request", "PKCE with S256 is required")

        expire()
        if (pending?.outcome == PendingAuthorization.Outcome.WAITING) throw OAuthError("temporarily_unavailable", "Another request is waiting")

        val request = PendingAuthorization(
            requestId = token(),
            client = client,
            redirectUri = redirectUri,
            codeChallenge = codeChallenge,
            state = state,
            code = (0 until CODE_DIGITS).joinToString("") { random.nextInt(10).toString() },
            expiresAt = now() + REQUEST_VALIDITY
        )
        pending = request
        prompt.ask(request)
        return request
    }

    /** The request [requestId] as it stands, to show on the page; null when it is not the one known. */
    fun status(requestId: String): PendingAuthorization? {
        expire()
        return pending?.takeIf { it.requestId == requestId }
    }

    /** The request waiting for its code, for the phone's screen; null when none is. */
    fun waiting(): PendingAuthorization? {
        expire()
        return pending?.takeIf { it.outcome == PendingAuthorization.Outcome.WAITING }
    }

    /**
     * The code typed on the phone for [requestId]. The right one approves the request; a wrong
     * one counts, and the third ends it, refused.
     *
     * @return The request as it stands after it
     */
    fun submitCode(requestId: String, typed: String): PendingAuthorization? {
        val request = waiting()?.takeIf { it.requestId == requestId } ?: return status(requestId)
        val updated = if (typed.trim() == request.code) {
            val code = token()
            codes[code] = IssuedCode(request.client.id, request.redirectUri, request.codeChallenge, now() + CODE_VALIDITY)
            request.copy(outcome = PendingAuthorization.Outcome.APPROVED, redirect = redirect(request, "code" to code))
        } else if (request.attempts + 1 >= MAX_ATTEMPTS) {
            request.copy(attempts = request.attempts + 1, outcome = PendingAuthorization.Outcome.REFUSED, redirect = redirect(request, "error" to "access_denied"))
        } else {
            request.copy(attempts = request.attempts + 1)
        }
        pending = updated
        if (updated.outcome != PendingAuthorization.Outcome.WAITING) prompt.withdraw(requestId)
        return updated
    }

    /** The person refuses [requestId] on the phone. */
    fun refuse(requestId: String): PendingAuthorization? {
        val request = waiting()?.takeIf { it.requestId == requestId } ?: return status(requestId)
        pending = request.copy(outcome = PendingAuthorization.Outcome.REFUSED, redirect = redirect(request, "error" to "access_denied"))
        prompt.withdraw(requestId)
        return pending
    }

    private fun expire() {
        val request = pending ?: return
        if (request.outcome == PendingAuthorization.Outcome.WAITING && now() >= request.expiresAt) {
            pending = request.copy(outcome = PendingAuthorization.Outcome.EXPIRED, redirect = redirect(request, "error" to "access_denied"))
            prompt.withdraw(request.requestId)
        }
    }

    private fun redirect(request: PendingAuthorization, vararg params: Pair<String, String>): String {
        val all = params.toList() + listOfNotNull(request.state?.let { "state" to it })
        val separator = if ('?' in request.redirectUri) '&' else '?'
        return request.redirectUri + separator + all.joinToString("&") { (k, v) -> "$k=${java.net.URLEncoder.encode(v, "UTF-8")}" }
    }

    // ---------------------------------------------------------------- tokens

    /**
     * Exchanges an authorization code (once) for tokens: the client and its return address must
     * be the ones the code was given to, and the verifier must be the one its challenge hides.
     */
    suspend fun exchangeCode(clientId: String?, clientSecret: String?, code: String?, redirectUri: String?, verifier: String?): Tokens {
        val client = authenticate(clientId, clientSecret)
        val issued = code?.let { codes.remove(it) } ?: throw OAuthError("invalid_grant", "Unknown or used code")
        if (now() >= issued.expiresAt) throw OAuthError("invalid_grant", "Expired code")
        if (issued.clientId != client.id || issued.redirectUri != redirectUri) throw OAuthError("invalid_grant", "The code was given to another client or address")
        if (verifier == null || challenge(verifier) != issued.codeChallenge) throw OAuthError("invalid_grant", "PKCE verification failed")
        return handOut(client.id)
    }

    /** Exchanges a refresh token for new tokens; the old one no longer works. */
    suspend fun refresh(clientId: String?, clientSecret: String?, refreshToken: String?): Tokens {
        val client = authenticate(clientId, clientSecret)
        val stored = refreshToken?.let { store.token(hash(it)) }
        if (stored == null || stored.kind != TokenKind.REFRESH || stored.clientId != client.id || now() >= stored.expiresAt) {
            throw OAuthError("invalid_grant", "Unknown or expired refresh token")
        }
        store.removeToken(stored.hash)
        return handOut(client.id)
    }

    /** The client an access token [bearer] was handed to, while it lasts; null otherwise. */
    suspend fun clientOf(bearer: String?): OAuthClient? {
        val stored = bearer?.let { store.token(hash(it)) } ?: return null
        if (stored.kind != TokenKind.ACCESS || now() >= stored.expiresAt) return null
        val client = store.client(stored.clientId) ?: return null
        store.touchClient(client.id, now())
        return client
    }

    private suspend fun authenticate(clientId: String?, clientSecret: String?): OAuthClient {
        val client = clientId?.let { store.client(it) } ?: throw OAuthError("invalid_client", "Unknown client")
        val expected = client.secretHash
        if (expected != null && (clientSecret == null || !MessageDigest.isEqual(hash(clientSecret).toByteArray(), expected.toByteArray()))) {
            throw OAuthError("invalid_client", "Client authentication failed")
        }
        return client
    }

    private suspend fun handOut(clientId: String): Tokens {
        store.removeExpired(now())
        val access = token()
        val refresh = token()
        store.addToken(StoredToken(hash(access), clientId, TokenKind.ACCESS, now() + ACCESS_VALIDITY))
        store.addToken(StoredToken(hash(refresh), clientId, TokenKind.REFRESH, now() + REFRESH_VALIDITY))
        store.touchClient(clientId, now())
        return Tokens(access, refresh, ACCESS_VALIDITY / 1000)
    }

    private fun token(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        const val CODE_DIGITS = 4
        const val MAX_ATTEMPTS = 3
        const val REQUEST_VALIDITY = 5 * 60 * 1000L
        const val CODE_VALIDITY = 60 * 1000L
        const val ACCESS_VALIDITY = 60 * 60 * 1000L
        /** Renewed at each use: a client unused this long has to be authorized again */
        const val REFRESH_VALIDITY = 90 * 24 * 60 * 60 * 1000L
        val AUTH_METHODS = setOf("none", "client_secret_basic", "client_secret_post")

        fun hash(value: String): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.toByteArray()))

        /** The PKCE S256 challenge of [verifier] (RFC 7636). */
        fun challenge(verifier: String): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
    }
}
