package com.assistant.core.ai.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * AI message structure with validation, data commands and action commands
 * Important constraint: Either dataCommands OR actionCommands, never both simultaneously
 */
data class AIMessage(
    val preText: String,                              // Required introduction/analysis
    val validationRequest: ValidationRequest?,        // Optional - before actions
    val dataCommands: List<DataCommand>?,             // Optional - AI data queries
    val actionCommands: List<DataCommand>?,           // Optional - actions to execute
    val postText: String?,                            // Optional, only if actions
    val communicationModule: CommunicationModule?     // Optional, always last
) {
    /**
     * Serialize AIMessage to JSON string for storage
     */
    fun toJson(): String {
        val json = JSONObject()

        json.put("preText", preText)

        validationRequest?.let {
            val validationJson = JSONObject()
            validationJson.put("message", it.message)
            it.status?.let { status -> validationJson.put("status", status.name) }
            json.put("validationRequest", validationJson)
        }

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

        communicationModule?.let { module ->
            val moduleJson = JSONObject()
            moduleJson.put("type", module.type)
            moduleJson.put("data", JSONObject(module.data))
            json.put("communicationModule", moduleJson)
        }

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

                val validationRequest = json.optJSONObject("validationRequest")?.let {
                    val message = it.getString("message")
                    val statusStr = it.optString("status")
                    val status = if (statusStr.isNotEmpty()) {
                        try {
                            ValidationStatus.valueOf(statusStr)
                        } catch (e: Exception) {
                            null
                        }
                    } else null

                    ValidationRequest(message, status)
                }

                val dataCommands = json.optJSONArray("dataCommands")?.let { array ->
                    (0 until array.length()).mapNotNull { i ->
                        try {
                            val cmdJson = array.getJSONObject(i)
                            DataCommand(
                                id = cmdJson.getString("id"),
                                type = cmdJson.getString("type"),
                                params = parseParams(cmdJson.getJSONObject("params")),
                                isRelative = cmdJson.optBoolean("isRelative", false)
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                }

                val actionCommands = json.optJSONArray("actionCommands")?.let { array ->
                    (0 until array.length()).mapNotNull { i ->
                        try {
                            val cmdJson = array.getJSONObject(i)
                            DataCommand(
                                id = cmdJson.getString("id"),
                                type = cmdJson.getString("type"),
                                params = parseParams(cmdJson.getJSONObject("params")),
                                isRelative = cmdJson.optBoolean("isRelative", false)
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                }

                val postText = json.optString("postText").takeIf { it.isNotEmpty() }

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

                AIMessage(
                    preText = preText,
                    validationRequest = validationRequest,
                    dataCommands = dataCommands,
                    actionCommands = actionCommands,
                    postText = postText,
                    communicationModule = communicationModule
                )

            } catch (e: Exception) {
                null
            }
        }

        private fun parseParams(paramsJson: JSONObject): Map<String, Any> {
            val params = mutableMapOf<String, Any>()
            paramsJson.keys().forEach { key ->
                params[key] = paramsJson.get(key)
            }
            return params
        }
    }
}

/**
 * Validation request from AI before executing actions
 */
data class ValidationRequest(
    val message: String,
    val status: ValidationStatus? = null // PENDING, CONFIRMED, REFUSED
)


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
     * Multiple choice question
     * Data: question (String), options (List<String>)
     */
    data class MultipleChoice(
        override val type: String = "MultipleChoice",
        override val data: Map<String, Any>
    ) : CommunicationModule()

    /**
     * Validation/confirmation request
     * Data: message (String)
     */
    data class Validation(
        override val type: String = "Validation",
        override val data: Map<String, Any>
    ) : CommunicationModule()

    // TODO: Add Slider, DataSelector modules when needed
}