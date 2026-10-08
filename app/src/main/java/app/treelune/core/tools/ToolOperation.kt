package app.treelune.core.tools

import android.content.Context
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.fields.settings.SettingsSchemaGenerator
import app.treelune.core.strings.Strings
import app.treelune.core.validation.Schema
import app.treelune.core.validation.SchemaCategory

/**
 * An operation a tool type offers on its entries beside creating, changing and deleting them:
 * one that carries a rule the generic write ignores (sending a message now, checking a goal's
 * criterion). Its tool type's service runs it as "<tooltype>.<name>", for the screen and the
 * AI alike; the AI calls it with TOOL_OPERATION and reads it with the tool's entries schema.
 *
 * @property name The operation, in snake_case, unique within its tool type
 * @property description One sentence: what it does, for the AI to choose it
 * @property params Its parameters, declared with the fields; the tool is never one of them,
 *   the caller names it by tool_instance_id beside them
 */
data class ToolOperation(
    val name: String,
    val description: String,
    val params: List<SettingNode>
)

/** The operations tool types declare (ToolTypeContract.getOperations), and their schemas. */
object ToolOperations {

    /** The operation [name] of [toolType], or null when it declares none of that name. */
    fun find(toolType: ToolTypeContract, name: String, context: Context): ToolOperation? =
        toolType.getOperations(context).firstOrNull { it.name == name }

    /** The schema the parameters of [operation] of [tooltype] are held to, generated from them. */
    fun schema(tooltype: String, operation: ToolOperation, context: Context): Schema {
        val s = Strings.`for`(context = context)
        return Schema(
            id = "${tooltype}_${operation.name}_params",
            displayName = operation.name,
            description = operation.description,
            category = SchemaCategory.TOOL_DATA,
            content = SettingsSchemaGenerator.generate(operation.params, s::shared).toString()
        )
    }
}
