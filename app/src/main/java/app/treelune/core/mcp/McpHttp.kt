package app.treelune.core.mcp

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.util.Base64

/** An HTTP request as the relay hands it over (docs/design/mcp-server.md, « Le contrat du relais »). */
data class HttpRequest(
    val id: String,
    val method: String,
    val path: String,
    val query: String,
    val headers: Map<String, String>,
    val body: ByteArray
) {
    /** A header by its name, whatever its case. */
    fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    /** The query string's parameters, decoded. */
    val queryParams: Map<String, String> get() = form(query)

    /** The body read as a form (application/x-www-form-urlencoded). */
    fun formBody(): Map<String, String> = form(String(body, Charsets.UTF_8))

    companion object {
        fun fromJson(json: JSONObject): HttpRequest = HttpRequest(
            id = json.getString("id"),
            method = json.getString("method").uppercase(),
            path = json.getString("path"),
            query = json.optString("query"),
            headers = json.optJSONObject("headers")?.let { h -> h.keys().asSequence().associateWith { h.getString(it) } } ?: emptyMap(),
            body = Base64.getDecoder().decode(json.optString("body"))
        )

        private fun form(text: String): Map<String, String> = text.split('&').filter { it.isNotEmpty() }.associate { pair ->
            val (k, v) = pair.split('=', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
        }
    }
}

/** An HTTP response, as the relay takes it back. */
data class HttpResponse(val status: Int, val headers: Map<String, String> = emptyMap(), val body: ByteArray = ByteArray(0)) {
    fun toJson(): JSONObject = JSONObject()
        .put("status", status)
        .put("headers", JSONObject(headers))
        .put("body", Base64.getEncoder().encodeToString(body))

    val text: String get() = String(body, Charsets.UTF_8)

    companion object {
        fun json(status: Int, body: JSONObject, headers: Map<String, String> = emptyMap()) =
            HttpResponse(status, headers + ("Content-Type" to "application/json"), body.toString().toByteArray())

        fun html(status: Int, body: String) =
            HttpResponse(status, mapOf("Content-Type" to "text/html; charset=utf-8", "Cache-Control" to "no-store"), body.toByteArray())
    }
}

/**
 * Every address the server answers under the relay's public one ([base]): the two OAuth
 * descriptions, the client registration, the authorization page and its status, the token
 * endpoint, and /mcp, which only an access token opens. Nothing else exists.
 *
 * @param text A text for the person, on the authorization page (the strings system)
 * @param onCall Told of each call that does something: /mcp let through, an authorization page
 *   opened, tokens handed out. It keeps the access open; a stranger's knocking does not
 */
class McpHttp(
    private val base: String,
    private val oauth: OAuthServer,
    private val mcp: McpServer,
    private val text: (String) -> String,
    /** Where each request for tokens goes, granted or refused and why: never a token */
    private val log: (String) -> Unit = {},
    private val onCall: () -> Unit = {}
) {
    private val resource get() = "$base$MCP_PATH"

    suspend fun handle(request: HttpRequest): HttpResponse = try {
        when {
            request.path.startsWith("/.well-known/oauth-protected-resource") -> HttpResponse.json(200, JSONObject()
                .put("resource", resource)
                .put("authorization_servers", JSONArray().put(base)))
            request.path.startsWith("/.well-known/oauth-authorization-server") -> HttpResponse.json(200, JSONObject()
                .put("issuer", base)
                .put("authorization_endpoint", "$base/authorize")
                .put("token_endpoint", "$base/token")
                .put("registration_endpoint", "$base/register")
                .put("response_types_supported", JSONArray().put("code"))
                .put("grant_types_supported", JSONArray().put("authorization_code").put("refresh_token"))
                .put("code_challenge_methods_supported", JSONArray().put("S256"))
                .put("token_endpoint_auth_methods_supported", JSONArray(OAuthServer.AUTH_METHODS.sorted())))
            request.path == "/register" && request.method == "POST" -> register(request)
            request.path == "/authorize" && request.method == "GET" -> authorize(request)
            request.path == "/authorize/status" && request.method == "GET" -> authorizationStatus(request)
            request.path == "/token" && request.method == "POST" -> token(request)
            request.path == MCP_PATH -> mcp(request)
            else -> HttpResponse(404)
        }
    } catch (e: OAuthServer.OAuthError) {
        HttpResponse.json(if (e.error == "invalid_client") 401 else 400, JSONObject().put("error", e.error).put("error_description", e.description))
    }

    private suspend fun register(request: HttpRequest): HttpResponse {
        val body = JSONObject(request.text())
        val uris = body.optJSONArray("redirect_uris")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
        val (client, secret) = oauth.register(body.optString("client_name").takeIf { it.isNotEmpty() }, uris, body.optString("token_endpoint_auth_method").takeIf { it.isNotEmpty() })
        return HttpResponse.json(201, JSONObject()
            .put("client_id", client.id)
            .apply { secret?.let { put("client_secret", it).put("client_secret_expires_at", 0) } }
            .put("client_id_issued_at", client.createdAt / 1000)
            .put("client_name", client.name)
            .put("redirect_uris", JSONArray(client.redirectUris))
            .put("token_endpoint_auth_method", if (secret == null) "none" else body.optString("token_endpoint_auth_method").ifEmpty { "client_secret_basic" })
            .put("grant_types", JSONArray().put("authorization_code").put("refresh_token"))
            .put("response_types", JSONArray().put("code")))
    }

    /** The page the client opens: it shows the code to type on the phone, and follows the request until it ends. */
    private suspend fun authorize(request: HttpRequest): HttpResponse {
        val q = request.queryParams
        val pending = try {
            oauth.authorize(q["client_id"], q["redirect_uri"], q["response_type"], q["code_challenge"], q["code_challenge_method"], q["state"])
        } catch (e: OAuthServer.OAuthError) {
            val message = if (e.error == "temporarily_unavailable") text("mcp_authorize_busy") else text("mcp_authorize_invalid").format(e.description)
            return HttpResponse.html(400, page(message, null))
        }
        onCall()
        return HttpResponse.html(200, page(text("mcp_authorize_instruction").format(pending.client.name), pending))
    }

    private fun authorizationStatus(request: HttpRequest): HttpResponse {
        val status = request.queryParams["request"]?.let { oauth.status(it) } ?: return HttpResponse(404)
        return HttpResponse.json(200, JSONObject()
            .put("outcome", status.outcome.name)
            .apply { status.redirect?.let { put("redirect", it) } }
            .put("message", when (status.outcome) {
                PendingAuthorization.Outcome.WAITING -> text("mcp_authorize_waiting")
                PendingAuthorization.Outcome.APPROVED -> text("mcp_authorize_approved")
                PendingAuthorization.Outcome.REFUSED -> text("mcp_authorize_refused")
                PendingAuthorization.Outcome.EXPIRED -> text("mcp_authorize_expired")
            }))
    }

    private suspend fun token(request: HttpRequest): HttpResponse {
        val form = request.formBody()
        // The client's credentials, in a Basic header or in the form
        val basic = request.header("Authorization")?.takeIf { it.startsWith("Basic ", ignoreCase = true) }?.let {
            String(Base64.getDecoder().decode(it.substring(6).trim())).split(':', limit = 2).map { part -> URLDecoder.decode(part, "UTF-8") }
        }
        val clientId = basic?.getOrNull(0) ?: form["client_id"]
        val secret = basic?.getOrNull(1) ?: form["client_secret"]
        val grant = form["grant_type"]
        val tokens = try {
            when (grant) {
                "authorization_code" -> oauth.exchangeCode(clientId, secret, form["code"], form["redirect_uri"], form["code_verifier"])
                "refresh_token" -> oauth.refresh(clientId, secret, form["refresh_token"])
                else -> throw OAuthServer.OAuthError("unsupported_grant_type", "grant_type must be authorization_code or refresh_token")
            }
        } catch (e: OAuthServer.OAuthError) {
            log("token $grant for client $clientId refused: ${e.error}, ${e.description}")
            throw e
        }
        log("token $grant for client $clientId granted")
        onCall()
        return HttpResponse.json(200, JSONObject()
            .put("access_token", tokens.access)
            .put("token_type", "Bearer")
            .put("expires_in", tokens.expiresIn)
            .put("refresh_token", tokens.refresh), mapOf("Cache-Control" to "no-store"))
    }

    private suspend fun mcp(request: HttpRequest): HttpResponse {
        val bearer = request.header("Authorization")?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }?.substring(7)?.trim()
        val client = oauth.clientOf(bearer)
            ?: return HttpResponse(401, mapOf("WWW-Authenticate" to "Bearer resource_metadata=\"$base/.well-known/oauth-protected-resource\""))
        // No stream: every answer goes whole, as JSON
        if (request.method != "POST") return HttpResponse(405, mapOf("Allow" to "POST"))
        onCall()
        val answer = mcp.handle(request.text(), McpCaller(client.id, client.name)) ?: return HttpResponse(202)
        return HttpResponse(200, mapOf("Content-Type" to "application/json"), answer.toByteArray())
    }

    private fun HttpRequest.text() = String(body, Charsets.UTF_8)

    /** The authorization page: the code and the instruction, then a script that follows the request and sends the browser on. */
    private fun page(message: String, pending: PendingAuthorization?): String {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        val follow = pending?.let { """
<p id="status">${esc(text("mcp_authorize_waiting"))}</p>
<script>
(function poll() {
  fetch("$base/authorize/status?request=${pending.requestId}").then(function (r) { return r.json(); }).then(function (s) {
    document.getElementById("status").textContent = s.message;
    if (s.outcome === "WAITING") { setTimeout(poll, 2000); return; }
    if (s.redirect) { setTimeout(function () { window.location.href = s.redirect; }, 1000); }
  }).catch(function () { setTimeout(poll, 4000); });
})();
</script>""" } ?: ""
        val code = pending?.let { "<p class=\"code\">${esc(it.code)}</p>" } ?: ""
        return """<!doctype html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>${esc(text("mcp_authorize_title"))}</title>
<style>body{font-family:sans-serif;max-width:28rem;margin:3rem auto;padding:0 1rem;line-height:1.5}.code{font-size:3rem;letter-spacing:.5rem;font-weight:bold;text-align:center}</style>
</head><body>
<h1>${esc(text("mcp_authorize_title"))}</h1>
<p>${esc(message)}</p>
$code
$follow
</body></html>"""
    }

    companion object {
        const val MCP_PATH = "/mcp"
    }
}
