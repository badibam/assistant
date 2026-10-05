package com.assistant.core.mcp

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** A tool the server offers, other than app_context: its name, its text, its parameters' schema. */
data class McpTool(val name: String, val description: String, val inputSchema: JSONObject, val readOnly: Boolean)

/** What a tool call gives back to the AI: a text, and whether it failed. */
data class McpToolResult(val text: String, val isError: Boolean)

/**
 * What the server needs from the app, kept apart so the protocol is tested without Android.
 * Every text it returns goes to the model.
 */
interface McpBackend {
    /** The app's commands as tools (AICommands), without the context token. */
    suspend fun tools(): List<McpTool>

    /** What the app is and holds now: its notions, the always-sent data (L2) and its state (L3). */
    suspend fun appContext(): String

    /** Runs the tool [name] with [arguments], the context token already taken out. */
    suspend fun call(name: String, arguments: JSONObject): McpToolResult

    /** The current date and time line, in the app's timezone, which opens every tool's answer. */
    fun dateLine(): String

    /** A text for the model, by its key (ai_prompt_chunks.xml). */
    fun text(key: String): String
}

/**
 * The app's MCP server, as JSON-RPC messages in and out (docs/design/mcp-server.md): it answers
 * initialize, ping, tools/list and tools/call, and nothing else. Transport, authorization and the
 * access window are around it, not in it. It presumes nothing of its client.
 *
 * A tool call other than app_context needs the context token app_context hands out; without it
 * the call is refused, and the refusal says where to get it. Every tool's answer opens with the
 * current date and time.
 */
class McpServer(
    private val backend: McpBackend,
    private val tokens: ContextTokens,
    private val version: String
) {
    /**
     * The answer to the JSON-RPC message [body], or null when it asks none (a notification, or a
     * response from the client). One message at a time: a batch is refused.
     */
    suspend fun handle(body: String): String? {
        val message = try {
            JSONObject(body)
        } catch (e: JSONException) {
            return error(JSONObject.NULL, PARSE_ERROR, "Parse error").toString()
        }
        val id = message.opt("id")
        val method = message.optString("method").takeIf { it.isNotEmpty() }
            ?: return if (id == null) null else error(id, INVALID_REQUEST, "Invalid request").toString()
        // A notification asks no answer, whatever it says
        if (id == null) return null
        val params = message.optJSONObject("params") ?: JSONObject()

        return when (method) {
            "initialize" -> result(id, initialize(params))
            "ping" -> result(id, JSONObject())
            "tools/list" -> result(id, JSONObject().put("tools", toolsList()))
            "tools/call" -> callTool(id, params)
            else -> error(id, METHOD_NOT_FOUND, "Method not found: $method")
        }.toString()
    }

    private fun initialize(params: JSONObject): JSONObject {
        val asked = params.optString("protocolVersion")
        return JSONObject()
            .put("protocolVersion", if (asked in PROTOCOL_VERSIONS) asked else PROTOCOL_VERSIONS.first())
            .put("capabilities", JSONObject().put("tools", JSONObject()))
            .put("serverInfo", JSONObject().put("name", SERVER_NAME).put("version", version))
    }

    private suspend fun toolsList(): JSONArray {
        val tools = JSONArray()
        tools.put(tool(APP_CONTEXT, backend.text("ai_mcp_app_context_description"),
            JSONObject().put("type", "object").put("properties", JSONObject()).put("additionalProperties", false), readOnly = true))
        for (tool in backend.tools()) {
            val schema = JSONObject(tool.inputSchema.toString())
            schema.getJSONObject("properties").put(CONTEXT_TOKEN, JSONObject()
                .put("type", "string").put("description", backend.text("ai_mcp_context_token_description")))
            schema.put("required", (schema.optJSONArray("required") ?: JSONArray()).put(CONTEXT_TOKEN))
            tools.put(tool(tool.name, tool.description, schema, tool.readOnly))
        }
        return tools
    }

    private fun tool(name: String, description: String, schema: JSONObject, readOnly: Boolean) = JSONObject()
        .put("name", name)
        .put("description", description)
        .put("inputSchema", schema)
        .put("annotations", JSONObject().put("readOnlyHint", readOnly))

    private suspend fun callTool(id: Any, params: JSONObject): JSONObject {
        val name = params.optString("name")
        val arguments = params.optJSONObject("arguments") ?: JSONObject()

        if (name == APP_CONTEXT) {
            val text = backend.appContext() + "\n\n" + backend.text("ai_mcp_context_token_line").format(tokens.issue())
            return result(id, toolResult(McpToolResult(text, isError = false)))
        }
        if (backend.tools().none { it.name == name }) return error(id, INVALID_PARAMS, "Unknown tool: $name")

        // The token is the server's business: the command never sees it
        val token = arguments.opt(CONTEXT_TOKEN) as? String
        if (!tokens.isValid(token)) {
            return result(id, toolResult(McpToolResult(backend.text("ai_mcp_context_token_missing"), isError = true)))
        }
        val commandArguments = JSONObject(arguments.toString()).apply { remove(CONTEXT_TOKEN) }
        return result(id, toolResult(backend.call(name, commandArguments)))
    }

    private fun toolResult(result: McpToolResult) = JSONObject()
        .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", backend.dateLine() + "\n\n" + result.text)))
        .put("isError", result.isError)

    private fun result(id: Any, result: JSONObject) = JSONObject().put("jsonrpc", "2.0").put("id", id).put("result", result)

    private fun error(id: Any, code: Int, message: String) = JSONObject().put("jsonrpc", "2.0").put("id", id)
        .put("error", JSONObject().put("code", code).put("message", message))

    companion object {
        const val SERVER_NAME = "assistant"
        const val APP_CONTEXT = "app_context"
        const val CONTEXT_TOKEN = "context_token"

        /** The protocol versions it speaks, the one it proposes first. */
        val PROTOCOL_VERSIONS = listOf("2025-06-18", "2025-03-26")

        const val PARSE_ERROR = -32700
        const val INVALID_REQUEST = -32600
        const val METHOD_NOT_FOUND = -32601
        const val INVALID_PARAMS = -32602
    }
}
