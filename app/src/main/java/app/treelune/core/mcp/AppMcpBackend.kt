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
 * does — AICommandProcessor, then CommandExecutor — as Source.EXTERNAL, with no session: no schema
 * is held back until sent. A write the protections guard (docs/design/validation.md) waits for the
 * user's approval, asked by a notification (McpApprovals); the client may ask before its calls too.
 * Its answer is the text the built-in AI would read, held to the chat's data threshold.
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

    override suspend fun call(name: String, arguments: JSONObject, caller: McpCaller): McpToolResult {
        val command = AICommands.find(name.uppercase())
            ?: return McpToolResult(s.shared("ai_error_command_unknown_type").format(name), isError = true)
        val dataCommand = DataCommand(id = "mcp_${UUID.randomUUID()}", type = command.type, params = JsonUtils.toMap(arguments))

        // A protected write waits for the user, who answers from a notification; an outside AI has
        // no session to add to the protections
        if (command.kind == CommandKind.ACTION) {
            app.treelune.core.ai.validation.ValidationResolver(context).reasons(listOf(dataCommand), app.treelune.core.ai.data.SessionValidation()).single()?.let { reason ->
                val line = s.shared("external_access_approval_line").format(
                    app.treelune.core.ai.validation.ActionVerbalizerHelper.verbalizeAction(dataCommand, context), reason)
                when (McpApprovals.ask(context, caller.name, listOf(line))) {
                    McpApprovals.Answer.ALLOWED -> Unit
                    McpApprovals.Answer.REFUSED -> return McpToolResult(s.shared("external_access_approval_refused"), isError = true)
                    McpApprovals.Answer.NO_ANSWER -> return McpToolResult(
                        s.shared("external_access_approval_no_answer").format(McpApprovals.TIMEOUT_MS / 1000), isError = true)
                }
            }
        }

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
