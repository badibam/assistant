package app.treelune.core.ai.data

import app.treelune.core.ai.utils.JsonNormalizer
import org.json.JSONArray
import org.json.JSONObject

/**
 * AI message structure with validation, data commands and action commands
 * Important constraint: Either dataCommands OR actionCommands, never both simultaneously
 *
 * VALIDATION REQUEST CHANGE:
 * - Previously: validationRequest was a data class with message and status
 * - Now: validationRequest is a simple Boolean (true = validation required, null/false = no validation)
 * - The AI explains its intentions in preText instead of using a separate validation message
 * - The UI displays detailed verbalized actions automatically via ValidationResolver
 */
data class AIMessage(
    val preText: String,                              // Required introduction/analysis
    val validationRequest: Boolean?,                  // Optional - true if validation required before actions
    val dataCommands: List<DataCommand>?,             // Optional - AI data queries
    val actionCommands: List<DataCommand>?,           // Optional - actions to execute
    val postText: String?,                            // Optional, only if actions
    val keepControl: Boolean?,                        // Optional - true to keep control after successful actions
    val communicationModule: CommunicationModule?,    // Optional, always last
    val completed: Boolean?                           // Optional - true when AI indicates work is done (AUTOMATION sessions)
) {
    /**
     * Serialize AIMessage to JSON string for storage
     */
    fun toJson(): String {
        val json = JSONObject()

        json.put("pre_text", preText)

        // Serialize validationRequest as boolean (or omit if null/false)
        validationRequest?.let { if (it) json.put("validation_request", true) }

        dataCommands?.let { commands ->
            val commandsArray = JSONArray()
            for (command in commands) {
                val commandJson = JSONObject()
                commandJson.put("id", command.id)
                commandJson.put("type", command.type)
                commandJson.put("params", JSONObject(command.params))
                commandJson.put("is_relative", command.isRelative)
                commandsArray.put(commandJson)
            }
            json.put("data_commands", commandsArray)
        }

        actionCommands?.let { commands ->
            val commandsArray = JSONArray()
            for (command in commands) {
                val commandJson = JSONObject()
                commandJson.put("id", command.id)
                commandJson.put("type", command.type)
                commandJson.put("params", JSONObject(command.params))
                commandJson.put("is_relative", command.isRelative)
                commandsArray.put(commandJson)
            }
            json.put("action_commands", commandsArray)
        }

        postText?.let { json.put("post_text", it) }

        // Serialize keepControl as boolean (or omit if null/false)
        keepControl?.let { if (it) json.put("keep_control", true) }

        communicationModule?.let { json.put("communication_module", it.declaration) }

        // Serialize completed as boolean (or omit if null/false)
        completed?.let { if (it) json.put("completed", true) }

        return json.toString()
    }

    companion object {
        /**
         * Deserialize AIMessage from JSON string
         * Returns null if parsing fails
         */
        fun fromJson(jsonString: String): AIMessage? {
            return try {
                val json = JSONObject(jsonString)

                val preText = json.getString("pre_text")

                // Parse validationRequest as boolean (true = validation required)
                val validationRequest = if (json.has("validation_request")) {
                    json.optBoolean("validation_request", false)
                } else null

                val dataCommands = json.optJSONArray("data_commands")?.let { array ->
                    (0 until array.length()).map { i ->
                        try {
                            val cmdJson = array.getJSONObject(i)
                            val type = cmdJson.getString("type")
                            val params = parseParams(cmdJson.getJSONObject("params"))
                            val isRelative = cmdJson.optBoolean("is_relative", false)

                            // Generate deterministic ID from command content
                            val id = buildCommandId(type, params, isRelative)

                            DataCommand(
                                id = id,
                                type = type,
                                params = params,
                                isRelative = isRelative
                            )
                        } catch (e: Exception) {
                            // Fail fast - command parsing must succeed for all commands
                            android.util.Log.e("AIMessage", "Failed to parse dataCommand at index $i: ${e.message}", e)
                            throw IllegalArgumentException("dataCommands[$i]: ${e.message}", e)
                        }
                    }
                }

                val actionCommands = json.optJSONArray("action_commands")?.let { array ->
                    (0 until array.length()).map { i ->
                        try {
                            val cmdJson = array.getJSONObject(i)
                            val type = cmdJson.getString("type")
                            val params = parseParams(cmdJson.getJSONObject("params"))
                            val isRelative = cmdJson.optBoolean("is_relative", false)

                            // Generate deterministic ID from command content
                            val id = buildCommandId(type, params, isRelative)

                            DataCommand(
                                id = id,
                                type = type,
                                params = params,
                                isRelative = isRelative
                            )
                        } catch (e: Exception) {
                            // Fail fast - command parsing must succeed for all commands
                            android.util.Log.e("AIMessage", "Failed to parse actionCommand at index $i: ${e.message}", e)
                            throw IllegalArgumentException("actionCommands[$i]: ${e.message}", e)
                        }
                    }
                }

                val postText = json.optString("post_text").takeIf { it.isNotEmpty() }

                // Parse keepControl as boolean (true = keep control after successful actions)
                val keepControl = if (json.has("keep_control")) {
                    json.optBoolean("keep_control", false)
                } else null

                // Kept as written: CommunicationModules.check says what is wrong with it
                val communicationModule = json.optJSONObject("communication_module")?.let { CommunicationModule(it) }

                // Parse completed as boolean (true = work completed)
                val completed = if (json.has("completed")) {
                    json.optBoolean("completed", false)
                } else null

                AIMessage(
                    preText = preText,
                    validationRequest = validationRequest,
                    dataCommands = dataCommands,
                    actionCommands = actionCommands,
                    postText = postText,
                    keepControl = keepControl,
                    communicationModule = communicationModule,
                    completed = completed
                )

            } catch (e: Exception) {
                null
            }
        }

        /**
         * Parse params from JSON and normalize all JSON native types to Kotlin types
         *
         * JSONObject.get() returns JSON native types (JSONObject, JSONArray, etc.)
         * which are not compatible with Kotlin types (Map, List).
         * JsonNormalizer handles recursive conversion for all nested structures.
         */
        private fun parseParams(paramsJson: JSONObject): Map<String, Any?> {
            val rawParams = mutableMapOf<String, Any>()
            paramsJson.keys().forEach { key ->
                rawParams[key] = paramsJson.get(key)
            }
            // Normalize all JSON types to Kotlin equivalents
            return JsonNormalizer.normalizeParams(rawParams)
        }

        /**
         * Generate deterministic ID from command content (type + params + isRelative).
         * Same logic as EnrichmentProcessor.buildQueryId() to ensure consistency.
         */
        private fun buildCommandId(type: String, params: Map<String, Any?>, isRelative: Boolean): String {
            val sortedParams = params.toSortedMap()
            val paramString = sortedParams.map { "${it.key}_${it.value}" }.joinToString(".")
            val relativeFlag = if (isRelative) "rel" else "abs"
            return "$type.$paramString.$relativeFlag"
        }
    }
}

/**
 * A question put to the user as a list of fields the AI declares (docs/AI.md): each field is
 * entered with the input of its field type, and the answer is an object of values under the
 * fields' names, checked against the schema generated from them.
 * A module without fields asks for a confirmation.
 *
 * Holds the declaration as the AI wrote it: CommunicationModules.check reads it before anything
 * uses [fields], so that a malformed module is refused with its reason instead of dropped.
 *
 * Exclusive with dataCommands and actionCommands; the question itself is the preText.
 */
class CommunicationModule(val declaration: JSONObject) {

    /** The fields asked for, each with whether an answer needs it. Valid once checked. */
    val fields: List<app.treelune.core.fields.settings.SettingNode.Field> by lazy {
        CommunicationModules.fieldsOf(declaration)
    }

    /** The labels of the fields asked for, one per line, for the history of the conversation. */
    fun toText(): String = fields.joinToString("\n") { it.definition.displayName }

    // Equal by content: a module read again from the stored message is the same module. A
    // JSONObject has no equality of its own, and the state holding a module is compared to
    // decide whether anything changed. The text is the declaration as it was stored, so the
    // same message always gives the same text.
    private val text = declaration.toString()

    override fun equals(other: Any?): Boolean = other is CommunicationModule && other.text == text

    override fun hashCode(): Int = text.hashCode()
}
