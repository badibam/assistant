package app.treelune.core.mcp

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder
import java.util.Base64

/**
 * The authorization and the addresses of the MCP server (docs/design/mcp-server.md, « L'autorisation »),
 * as a client meets them through the relay, with the phone's screen played by the test.
 */
class McpHttpTest {

    private var clock = 1_000_000L

    private val store = object : OAuthStore {
        val clients = mutableMapOf<String, OAuthClient>()
        val tokens = mutableMapOf<String, StoredToken>()
        override suspend fun addClient(client: OAuthClient) { clients[client.id] = client }
        override suspend fun client(id: String) = clients[id]
        override suspend fun clients() = clients.values.toList()
        override suspend fun removeClient(id: String) { clients.remove(id); tokens.values.removeAll { it.clientId == id } }
        override suspend fun touchClient(id: String, at: Long) { clients[id]?.let { clients[id] = it.copy(lastUsedAt = at) } }
        override suspend fun addToken(token: StoredToken) { tokens[token.hash] = token }
        override suspend fun token(hash: String) = tokens[hash]
        override suspend fun removeToken(hash: String) { tokens.remove(hash) }
        override suspend fun removeExpired(now: Long) { tokens.values.removeAll { it.expiresAt <= now } }
    }

    private val asked = mutableListOf<PendingAuthorization>()
    private val withdrawn = mutableListOf<String>()
    private val prompt = object : ApprovalPrompt {
        override fun ask(request: PendingAuthorization) { asked += request }
        override fun withdraw(requestId: String) { withdrawn += requestId }
    }

    private val oauth = OAuthServer(store, prompt, { clock })
    private var calls = 0
    private val backend = object : McpBackend {
        override suspend fun tools() = emptyList<McpTool>()
        override suspend fun appContext() = "APP"
        override suspend fun call(name: String, arguments: JSONObject) = McpToolResult("", false)
        override fun dateLine() = "NOW"
        override fun text(key: String) = if (key == "ai_mcp_context_token_line") "%1\$s" else key
    }
    private val http = McpHttp(BASE, oauth, McpServer(backend, ContextTokens("k".toByteArray(), { clock }), "1"), { key -> if (key == "mcp_authorize_instruction" || key == "mcp_authorize_invalid") "$key %1\$s" else key }) { calls++ }

    private val callback = "https://client.example/callback"
    private val verifier = "a-verifier-long-enough-for-pkce-0123456789abcdef"

    private fun request(method: String, path: String, query: String = "", headers: Map<String, String> = emptyMap(), body: String = "") =
        runBlocking { http.handle(HttpRequest("r", method, path, query, headers, body.toByteArray())) }

    private fun register(authMethod: String = "none"): JSONObject =
        JSONObject(request("POST", "/register", body = """{"client_name":"Claude","redirect_uris":["$callback"],"token_endpoint_auth_method":"$authMethod"}""").text)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun openPage(clientId: String, challenge: String = OAuthServer.challenge(verifier)) =
        request("GET", "/authorize", "response_type=code&client_id=${enc(clientId)}&redirect_uri=${enc(callback)}&code_challenge=${enc(challenge)}&code_challenge_method=S256&state=xyz")

    private fun codeOf(redirect: String) = redirect.substringAfter("code=").substringBefore('&').let { java.net.URLDecoder.decode(it, "UTF-8") }

    private fun tokenRequest(form: String, headers: Map<String, String> = emptyMap()) =
        request("POST", "/token", headers = headers + ("Content-Type" to "application/x-www-form-urlencoded"), body = form)

    /** The whole way, from registration to a call of /mcp. */
    private fun authorized(): Pair<String, JSONObject> {
        val clientId = register().getString("client_id")
        openPage(clientId)
        val pending = asked.last()
        val approved = oauth.submitCode(pending.requestId, pending.code)!!
        val code = codeOf(approved.redirect!!)
        val tokens = JSONObject(tokenRequest("grant_type=authorization_code&code=${enc(code)}&redirect_uri=${enc(callback)}&client_id=${enc(clientId)}&code_verifier=${enc(verifier)}").text)
        return clientId to tokens
    }

    @Test
    fun theDescriptionsPointToTheServersAddresses() {
        assertEquals("$BASE/mcp", JSONObject(request("GET", "/.well-known/oauth-protected-resource").text).getString("resource"))
        assertEquals("$BASE/mcp", JSONObject(request("GET", "/.well-known/oauth-protected-resource/mcp").text).getString("resource"))
        val server = JSONObject(request("GET", "/.well-known/oauth-authorization-server").text)
        assertEquals("$BASE/token", server.getString("token_endpoint"))
        assertEquals("$BASE/register", server.getString("registration_endpoint"))
    }

    @Test
    fun mcpWithoutATokenSaysWhereToGetOne() {
        val refused = request("POST", "/mcp", body = """{"jsonrpc":"2.0","id":1,"method":"ping"}""")
        assertEquals(401, refused.status)
        assertTrue(refused.headers.getValue("WWW-Authenticate").contains("$BASE/.well-known/oauth-protected-resource"))
        assertEquals(0, calls)
    }

    @Test
    fun theCodeTypedOnThePhoneOpensMcp() {
        val (_, tokens) = authorized()
        // The page opened and the tokens handed out were activity too
        assertEquals(2, calls)
        val answer = request("POST", "/mcp", headers = mapOf("Authorization" to "Bearer ${tokens.getString("access_token")}"), body = """{"jsonrpc":"2.0","id":1,"method":"ping"}""")
        assertEquals(200, answer.status)
        assertEquals(1, JSONObject(answer.text).getInt("id"))
        assertEquals(3, calls)
    }

    @Test
    fun thePageShowsTheCodeAndTheClientsName() {
        val clientId = register().getString("client_id")
        val page = openPage(clientId)
        assertEquals(200, page.status)
        assertTrue(page.text.contains(asked.single().code))
        assertTrue(page.text.contains("mcp_authorize_instruction Claude"))
    }

    @Test
    fun aWrongCodeThreeTimesRefusesTheRequest() {
        val clientId = register().getString("client_id")
        openPage(clientId)
        val pending = asked.single()
        val wrong = if (pending.code == "0000") "1111" else "0000"
        assertEquals(PendingAuthorization.Outcome.WAITING, oauth.submitCode(pending.requestId, wrong)!!.outcome)
        assertEquals(PendingAuthorization.Outcome.WAITING, oauth.submitCode(pending.requestId, wrong)!!.outcome)
        val refused = oauth.submitCode(pending.requestId, wrong)!!
        assertEquals(PendingAuthorization.Outcome.REFUSED, refused.outcome)
        assertTrue(refused.redirect!!.contains("error=access_denied"))
        assertTrue(refused.redirect!!.contains("state=xyz"))
        // Refused, the right code no longer opens anything
        assertEquals(PendingAuthorization.Outcome.REFUSED, oauth.submitCode(pending.requestId, pending.code)!!.outcome)
        assertEquals(listOf(pending.requestId), withdrawn)
    }

    @Test
    fun aRequestExpiresAndOneWaitsAtATime() {
        val clientId = register().getString("client_id")
        openPage(clientId)
        assertEquals(400, openPage(clientId).status)
        clock += OAuthServer.REQUEST_VALIDITY
        assertNull(oauth.waiting())
        assertEquals("EXPIRED", JSONObject(request("GET", "/authorize/status", "request=${asked.first().requestId}").text).getString("outcome"))
        assertEquals(200, openPage(clientId).status)
    }

    @Test
    fun anUnknownReturnAddressOrNoPkceIsRefused() {
        val clientId = register().getString("client_id")
        assertEquals(400, request("GET", "/authorize", "response_type=code&client_id=$clientId&redirect_uri=${enc("https://evil.example/cb")}&code_challenge=x&code_challenge_method=S256").status)
        assertEquals(400, request("GET", "/authorize", "response_type=code&client_id=$clientId&redirect_uri=${enc(callback)}").status)
        assertTrue(asked.isEmpty())
    }

    @Test
    fun theCodeNeedsItsVerifierAndServesOnce() {
        val clientId = register().getString("client_id")
        openPage(clientId)
        val pending = asked.single()
        val code = codeOf(oauth.submitCode(pending.requestId, pending.code)!!.redirect!!)

        val badVerifier = tokenRequest("grant_type=authorization_code&code=${enc(code)}&redirect_uri=${enc(callback)}&client_id=${enc(clientId)}&code_verifier=wrong")
        assertEquals(400, badVerifier.status)
        assertEquals("invalid_grant", JSONObject(badVerifier.text).getString("error"))
        // The failed attempt used the code up
        assertEquals(400, tokenRequest("grant_type=authorization_code&code=${enc(code)}&redirect_uri=${enc(callback)}&client_id=${enc(clientId)}&code_verifier=${enc(verifier)}").status)
    }

    @Test
    fun aRefreshTokenGivesNewTokensOnce() {
        val (clientId, tokens) = authorized()
        val refresh = tokens.getString("refresh_token")
        val renewed = tokenRequest("grant_type=refresh_token&refresh_token=${enc(refresh)}&client_id=${enc(clientId)}")
        assertEquals(200, renewed.status)
        assertNotNull(JSONObject(renewed.text).getString("access_token"))
        assertEquals(400, tokenRequest("grant_type=refresh_token&refresh_token=${enc(refresh)}&client_id=${enc(clientId)}").status)
    }

    @Test
    fun anAccessTokenLastsAnHour() {
        val (_, tokens) = authorized()
        clock += OAuthServer.ACCESS_VALIDITY
        assertEquals(401, request("POST", "/mcp", headers = mapOf("Authorization" to "Bearer ${tokens.getString("access_token")}"), body = "{}").status)
    }

    @Test
    fun aConfidentialClientAuthenticatesAtTheTokenEndpoint() {
        val registered = register("client_secret_basic")
        val clientId = registered.getString("client_id")
        val secret = registered.getString("client_secret")
        openPage(clientId)
        val pending = asked.single()
        val code = codeOf(oauth.submitCode(pending.requestId, pending.code)!!.redirect!!)
        val form = "grant_type=authorization_code&code=${enc(code)}&redirect_uri=${enc(callback)}&code_verifier=${enc(verifier)}"

        assertEquals(401, tokenRequest("$form&client_id=${enc(clientId)}").status)
        openPage(clientId)
        val second = asked.last()
        val code2 = codeOf(oauth.submitCode(second.requestId, second.code)!!.redirect!!)
        val basic = "Basic " + Base64.getEncoder().encodeToString("$clientId:$secret".toByteArray())
        assertEquals(200, tokenRequest("grant_type=authorization_code&code=${enc(code2)}&redirect_uri=${enc(callback)}&code_verifier=${enc(verifier)}", mapOf("Authorization" to basic)).status)
    }

    @Test
    fun aRevokedClientIsShutOut() {
        val (clientId, tokens) = authorized()
        runBlocking { store.removeClient(clientId) }
        assertEquals(401, request("POST", "/mcp", headers = mapOf("Authorization" to "Bearer ${tokens.getString("access_token")}"), body = "{}").status)
    }

    @Test
    fun noStreamIsOpened() {
        val (_, tokens) = authorized()
        assertEquals(405, request("GET", "/mcp", headers = mapOf("Authorization" to "Bearer ${tokens.getString("access_token")}")).status)
    }

    @Test
    fun aRequestAndAResponseTravelAsTheRelaySpeaks() {
        val json = JSONObject("""{"id":"q1","method":"post","path":"/mcp","query":"","headers":{"Authorization":"Bearer t"},"body":"${Base64.getEncoder().encodeToString("{}".toByteArray())}"}""")
        val parsed = HttpRequest.fromJson(json)
        assertEquals("POST", parsed.method)
        assertEquals("Bearer t", parsed.header("authorization"))
        assertEquals("{}", String(parsed.body))
        val response = HttpResponse.json(200, JSONObject().put("a", 1)).toJson()
        assertEquals("""{"a":1}""", String(Base64.getDecoder().decode(response.getString("body"))))
    }

    @Test
    fun anythingElseDoesNotExist() {
        assertEquals(404, request("GET", "/admin").status)
    }

    companion object {
        const val BASE = "https://relay.example/treelune"
    }
}
