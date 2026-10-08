package app.treelune.core.mcp

import android.content.Context
import app.treelune.core.ai.data.AICommands
import app.treelune.core.ai.data.CommandKind
import app.treelune.core.ai.data.CommandStatus
import app.treelune.core.ai.data.DataCommand
import app.treelune.core.ai.data.SessionType
import app.treelune.core.ai.data.SystemMessageType
import app.treelune.core.ai.data.withinChars
import app.treelune.core.ai.processing.AICommandProcessor
import app.treelune.core.ai.prompts.CommandExecutor
import app.treelune.core.ai.prompts.PromptChunks
import app.treelune.core.ai.prompts.PromptManager
import app.treelune.core.ai.prompts.toPromptSection
import app.treelune.core.ai.providers.toPromptText
import app.treelune.core.coordinator.Source
import app.treelune.core.strings.Strings
import app.treelune.core.utils.AppConfigManager
import app.treelune.core.utils.DateTimeConverter
import app.treelune.core.utils.JsonUtils
import org.json.JSONObject
import java.util.UUID

/**
 * The app behind its MCP server: each command of AICommands is a tool named by its type in lower
 * case, described by its text and typed by its declaration, and runs the way the built-in AI's
 * does — AICommandProcessor, then CommandExecutor — as Source.EXTERNAL, with no session: no
 * validation is asked in the app (the client asks before each call it is told to), and no schema
 * is held back until sent. Its answer is the text the built-in AI would read, held to the chat's
 * data threshold.
 */
class AppMcpBackend(private val context: Context) : McpBackend {

    private val s = Strings.`for`(context = context)

    override suspend fun tools(): List<McpTool> = AICommands.ALL.map { command ->
        McpTool(
            name = command.type.lowercase(),
            description = PromptChunks.commandText(context, command),
            inputSchema = command.schema(),
            readOnly = command.kind == CommandKind.QUERY
        )
    }

    override suspend fun appContext(): String = listOf(
        s.shared("ai_mcp_context_intro"),
        PromptChunks.buildAppNotions(context),
        PromptManager.buildLevel2Content(context, sessionId = null, sessionType = null),
        PromptManager.buildAppStateContent(context)
    ).joinToString("\n\n")

    override suspend fun call(name: String, arguments: JSONObject): McpToolResult {
        val command = AICommands.find(name.uppercase())
            ?: return McpToolResult(s.shared("ai_error_command_unknown_type").format(name), isError = true)
        val dataCommand = DataCommand(id = "mcp_${UUID.randomUUID()}", type = command.type, params = JsonUtils.toMap(arguments))

        val processor = AICommandProcessor(context)
        val transformation = when (command.kind) {
            CommandKind.QUERY -> processor.processDataCommands(listOf(dataCommand), System.currentTimeMillis())
            CommandKind.ACTION -> processor.processActionCommands(listOf(dataCommand))
        }
        if (transformation.errors.isNotEmpty()) return McpToolResult(transformation.errors.joinToString("\n"), isError = true)

        val executed = CommandExecutor(context).executeCommands(
            commands = transformation.executableCommands,
            messageType = if (command.kind == CommandKind.QUERY) SystemMessageType.DATA_ADDED else SystemMessageType.ACTIONS_EXECUTED,
            origin = Source.EXTERNAL,
            level = "mcp",
            sessionId = null
        )
        // A query's data is in its results, as the built-in AI's session puts it in the message
        val message = executed.systemMessage.let { message ->
            executed.promptResults.toPromptSection().takeIf { it.isNotEmpty() }?.let { message.copy(formattedData = it) } ?: message
        }
        val held = message.withinChars(AppConfigManager.getAILimits().getLimitsForSessionType(SessionType.CHAT).maxDataChars) {
            s.shared("ai_system_result_cut").format(it)
        }
        return McpToolResult(held.toPromptText(), isError = message.commandResults.any { it.status == CommandStatus.FAILED })
    }

    override fun dateLine(): String {
        val zone = AppConfigManager.getDateTimeConfig().getZoneId()
        return s.shared("ai_prompt_current_datetime").format(DateTimeConverter.timestampToISO(System.currentTimeMillis(), zone))
    }

    override fun text(key: String): String = s.shared(key)
}
