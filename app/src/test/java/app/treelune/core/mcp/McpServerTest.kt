package app.treelune.core.mcp

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The MCP server's promises (docs/design/mcp-server.md), with the app replaced by a fake. */
class McpServerTest {

    private var clock = 1_000_000L
    private val tokens = ContextTokens("secret".toByteArray(), { clock })

    private val calls = mutableListOf<Pair<String, JSONObject>>()

    private val CALLER = McpCaller("client", "Claude")

    private val backend = object : McpBackend {
        override suspend fun tools() = listOf(
            McpTool("tool_data", "Reads entries.", JSONObject("""{"type":"object","properties":{"id":{"type":"string"}},"required":["id"],"additionalProperties":false}"""), readOnly = true),
            McpTool("delete_zone", "Deletes a zone.", JSONObject("""{"type":"object","properties":{"zone_id":{"type":"string"}},"additionalProperties":false}"""), readOnly = false)
        )
        override suspend fun appContext(caller: McpCaller) = "THE APP"
        override suspend fun call(name: String, arguments: JSONObject, caller: McpCaller): McpToolResult {
            calls += name to arguments
            return McpToolResult("done $name", isError = false)
        }
        override fun dateLine() = "NOW"
        override fun text(key: String) = when (key) {
            "ai_mcp_context_token_line" -> "token: %1\$s"
            else -> key
        }
    }

    private val server = McpServer(backend, tokens, "1.0")

    private fun ask(method: String, params: String = "{}", id: Int = 1): JSONObject =
        JSONObject(runBlocking { server.handle("""{"jsonrpc":"2.0","id":$id,"method":"$method","params":$params}""", CALLER) }!!)

    private fun call(tool: String, arguments: String): JSONObject = ask("tools/call", """{"name":"$tool","arguments":$arguments}""").getJSONObject("result")

    private fun text(result: JSONObject) = result.getJSONArray("content").getJSONObject(0).getString("text")

    private fun token(): String = text(call("app_context", "{}")).substringAfter("token: ")

    @Test
    fun initializeAnswersTheVersionAskedWhenSpoken() {
        val result = ask("initialize", """{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"x","version":"1"}}""").getJSONObject("result")
        assertEquals("2025-03-26", result.getString("protocolVersion"))
        assertTrue(result.getJSONObject("capabilities").has("tools"))

        val unknown = ask("initialize", """{"protocolVersion":"1999-01-01"}""").getJSONObject("result")
        assertEquals(McpServer.PROTOCOL_VERSIONS.first(), unknown.getString("protocolVersion"))
    }

    @Test
    fun aNotificationIsNotAnswered() {
        assertNull(runBlocking { server.handle("""{"jsonrpc":"2.0","method":"notifications/initialized"}""", CALLER) })
    }

    @Test
    fun anUnknownMethodIsAnError() {
        assertEquals(McpServer.METHOD_NOT_FOUND, ask("resources/list").getJSONObject("error").getInt("code"))
    }

    @Test
    fun theToolsAreAppContextAndTheCommandsEachRequiringTheToken() {
        val tools = ask("tools/list").getJSONObject("result").getJSONArray("tools")
        val byName = (0 until tools.length()).map { tools.getJSONObject(it) }.associateBy { it.getString("name") }

        assertEquals(setOf("app_context", "tool_data", "delete_zone"), byName.keys)
        assertFalse(byName.getValue("app_context").getJSONObject("inputSchema").optJSONArray("required")?.length()?.let { it > 0 } ?: false)
        for (name in listOf("tool_data", "delete_zone")) {
            val schema = byName.getValue(name).getJSONObject("inputSchema")
            assertTrue("$name takes the token", schema.getJSONObject("properties").has("context_token"))
            assertTrue("$name requires the token", (0 until schema.getJSONArray("required").length()).any { schema.getJSONArray("required").getString(it) == "context_token" })
        }
        assertEquals(listOf("id", "context_token"), byName.getValue("tool_data").getJSONObject("inputSchema").getJSONArray("required").let { r -> (0 until r.length()).map { r.getString(it) } })
        assertTrue(byName.getValue("tool_data").getJSONObject("annotations").getBoolean("readOnlyHint"))
        assertFalse(byName.getValue("delete_zone").getJSONObject("annotations").getBoolean("readOnlyHint"))
    }

    @Test
    fun noCommandRunsWithoutTheContextRead() {
        val refused = call("tool_data", """{"id":"t1"}""")
        assertTrue(refused.getBoolean("isError"))
        assertTrue(text(refused).contains("ai_mcp_context_token_missing"))
        assertTrue(calls.isEmpty())

        val forged = call("tool_data", """{"id":"t1","context_token":"${clock}.forged"}""")
        assertTrue(forged.getBoolean("isError"))
        assertTrue(calls.isEmpty())
    }

    @Test
    fun withTheTokenACommandRunsWithoutIt() {
        val result = call("tool_data", """{"id":"t1","context_token":"${token()}"}""")
        assertFalse(result.getBoolean("isError"))
        assertEquals("NOW\n\ndone tool_data", text(result))
        assertEquals("tool_data", calls.single().first)
        assertFalse("the command never sees the token", calls.single().second.has("context_token"))
    }

    @Test
    fun aTokenExpiresAfterADay() {
        val token = token()
        clock += ContextTokens.VALIDITY
        assertTrue(call("tool_data", """{"id":"t1","context_token":"$token"}""").getBoolean("isError"))
        assertTrue(calls.isEmpty())
    }

    @Test
    fun everyAnswerOpensWithTheDate() {
        assertTrue(text(call("app_context", "{}")).startsWith("NOW\n\nTHE APP"))
    }

    @Test
    fun anUnknownToolIsAnError() {
        assertEquals(McpServer.INVALID_PARAMS, ask("tools/call", """{"name":"nope","arguments":{}}""").getJSONObject("error").getInt("code"))
    }

    @Test
    fun anotherAppsTokenIsRefused() {
        val other = ContextTokens("other".toByteArray(), { clock })
        assertFalse(tokens.isValid(other.issue()))
        assertTrue(tokens.isValid(tokens.issue()))
    }
}
