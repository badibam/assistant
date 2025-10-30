package com.assistant.core.ai.data

import android.content.Context
import com.assistant.core.ai.utils.JsonNormalizer
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

        json.put("preText", preText)

        // Serialize validationRequest as boolean (or omit if null/false)
        validationRequest?.let { if (it) json.put("validationRequest", true) }

        dataCommands?.let { commands ->
            val commandsArray = JSONArray()
            for (command in commands) {
                val commandJson = JSONObject()
                commandJson.put("id", command.id)
                commandJson.put("type", command.type)
                commandJson.put("params", JSONObject(command.params))
                commandJson.put("isRelative", command.isRelative)
                commandsArray.put(commandJson)
            }
            json.put("dataCommands", commandsArray)
        }

        actionCommands?.let { commands ->
            val commandsArray = JSONArray()
            for (command in commands) {
                val commandJson = JSONObject()
                commandJson.put("id", command.id)
                commandJson.put("type", command.type)
                commandJson.put("params", JSONObject(command.params))
                commandJson.put("isRelative", command.isRelative)
                commandsArray.put(commandJson)
            }
            json.put("actionCommands", commandsArray)
        }

        postText?.let { json.put("postText", it) }

        // Serialize keepControl as boolean (or omit if null/false)
        keepControl?.let { if (it) json.put("keepControl", true) }

        communicationModule?.let { module ->
            val moduleJson = JSONObject()
            moduleJson.put("type", module.type)
            moduleJson.put("data", JSONObject(module.data))
            json.put("communicationModule", moduleJson)
        }

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

                val preText = json.getString("preText")

                // Parse validationRequest as boolean (true = validation required)
                val validationRequest = if (json.has("validationRequest")) {
                    json.optBoolean("validationRequest", false)
                } else null

                val dataCommands = json.optJSONArray("dataCommands")?.let { array ->
                    (0 until array.length()).map { i ->
                        try {
                            val cmdJson = array.getJSONObject(i)
                            val type = cmdJson.getString("type")
                            val params = parseParams(cmdJson.getJSONObject("params"))
                            val isRelative = cmdJson.optBoolean("isRelative", false)

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

                val actionCommands = json.optJSONArray("actionCommands")?.let { array ->
                    (0 until array.length()).map { i ->
                        try {
                            val cmdJson = array.getJSONObject(i)
                            val type = cmdJson.getString("type")
                            val params = parseParams(cmdJson.getJSONObject("params"))
                            val isRelative = cmdJson.optBoolean("isRelative", false)

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

                val postText = json.optString("postText").takeIf { it.isNotEmpty() }

                // Parse keepControl as boolean (true = keep control after successful actions)
                val keepControl = if (json.has("keepControl")) {
                    json.optBoolean("keepControl", false)
                } else null

                val communicationModule = json.optJSONObject("communicationModule")?.let { moduleJson ->
                    try {
                        val type = moduleJson.getString("type")
                        val data = parseParams(moduleJson.getJSONObject("data"))

                        when (type) {
                            "MultipleChoice" -> CommunicationModule.MultipleChoice(type, data)
                            "Validation" -> CommunicationModule.Validation(type, data)
                            else -> null
                        }
                    } catch (e: Exception) {
                        null
                    }
                }

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
        private fun parseParams(paramsJson: JSONObject): Map<String, Any> {
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
        private fun buildCommandId(type: String, params: Map<String, Any>, isRelative: Boolean): String {
            val sortedParams = params.toSortedMap()
            val paramString = sortedParams.map { "${it.key}_${it.value}" }.joinToString(".")
            val relativeFlag = if (isRelative) "rel" else "abs"
            return "$type.$paramString.$relativeFlag"
        }
    }
}

/**
 * Communication modules for user interaction
 * Sealed class for controlled set of core modules
 *
 * Pattern analogous to tool_data: common type + variable data in Map
 * Data validated via CommunicationModuleSchemas
 *
 * Exclusive with dataCommands/actionCommands/postText:
 * - If communicationModule present → only preText + communicationModule (+ optional validationRequest if related)
 * - User response stored as simple SessionMessage with textContent
 */
sealed class CommunicationModule {
    abstract val type: String
    abstract val data: Map<String, Any>

    /**
     * Convert module to text representation for display in message history
     * Used to show the question/prompt even after the module is no longer interactive
     */
    abstract fun toText(context: Context): String

    /**
     * Multiple choice question
     * Data: question (String), options (List<String>)
     */
    data class MultipleChoice(
        override val type: String = "MultipleChoice",
        override val data: Map<String, Any>
    ) : CommunicationModule() {
        override fun toText(context: Context): String {
            val question = data["question"] as? String ?: ""
            @Suppress("UNCHECKED_CAST")
            val options = data["options"] as? List<String> ?: emptyList()

            val optionsList = options.mapIndexed { index, option ->
                "${index + 1}. $option"
            }.joinToString("\n")

            return "$question\n\n$optionsList"
        }
    }

    /**
     * Validation/confirmation request
     * Data: message (String)
     */
    data class Validation(
        override val type: String = "Validation",
        override val data: Map<String, Any>
    ) : CommunicationModule() {
        override fun toText(context: Context): String {
            return data["message"] as? String ?: ""
        }
    }

    // TODO: Add Slider, DataSelector modules when needed
}