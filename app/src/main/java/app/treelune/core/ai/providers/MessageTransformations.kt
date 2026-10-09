package app.treelune.core.ai.providers

import app.treelune.core.ai.data.MessageSender
import app.treelune.core.ai.data.PromptData
import app.treelune.core.ai.data.PromptPart
import app.treelune.core.ai.data.SessionMessage
import app.treelune.core.ai.data.SystemMessage
import app.treelune.core.ai.data.CommandStatus

/**
 * Common message transformations for AI providers
 *
 * These transformations prepare SessionMessage lists from DB for API consumption
 * by normalizing message types and structures.
 */

/**
 * Transform all SYSTEM messages to USER messages
 *
 * This is Step 1 of provider message preparation and is common to all providers.
 * It converts SYSTEM messages (enrichments, query results, etc.) into USER messages
 * while preserving the exact order.
 *
 * Why: Most AI APIs only accept USER and ASSISTANT roles, not SYSTEM in message history.
 * SYSTEM messages in our DB represent contextual data that should be presented as USER input.
 *
 * @param messages Original list from database with USER, AI, SYSTEM senders
 * @return Transformed list with only USER and AI senders (SYSTEM → USER)
 *
 * Example:
 * INPUT:  [USER "question", SYSTEM "data", AI "response", SYSTEM "more data"]
 * OUTPUT: [USER "question", USER "data", AI "response", USER "more data"]
 */
fun transformSystemMessagesToUser(messages: List<SessionMessage>): List<SessionMessage> {
    return messages.map { message ->
        if (message.sender == MessageSender.SYSTEM) {
            // Convert SYSTEM to USER while preserving all other fields
            message.copy(sender = MessageSender.USER)
        } else {
            message
        }
    }
}

/**
 * Build the dated closing message every provider appends to the history.
 *
 * It always carries the current time. An AUTOMATION run also carries the time it was scheduled
 * for, which is what its relative periods resolved against: catching up on a missed day, the two
 * are days apart, and the AI needs both to tell the data it reads (scheduled time) from what it
 * does now (current time). The two lines are the whole of that separation, so they travel
 * together and are built here once rather than in each provider.
 */
internal fun PromptData.buildDatetimeMessage(context: android.content.Context): String {
    val s = app.treelune.core.strings.Strings.`for`(context = context)
    // ISO 8601 with its offset, in the app's timezone: the form every date takes in front of
    // the AI, so this one reads like the dates in the data beside it.
    val zone = app.treelune.core.utils.AppConfigManager.getDateTimeConfig().getZoneId()
    fun iso(timestamp: Long) = app.treelune.core.utils.DateTimeConverter.timestampToISO(timestamp, zone)

    val now = System.currentTimeMillis()
    val currentLine = s.shared("ai_prompt_current_datetime").format(iso(now))

    val scheduled = scheduledExecutionTime ?: return currentLine
    val scheduledLine = s.shared("ai_prompt_scheduled_datetime").format(iso(scheduled))
    return "$currentLine\n$scheduledLine"
}

/**
 * A system message as the model reads it, whatever the provider: the summary, a line per
 * command result, and the data a query added.
 *
 * An action's line carries its data -- above all the id of what it created, which the model
 * needs for its next command. That hangs on isActionCommand, so the message has to be read
 * back with it: a copy of the parser that forgot the flag left every id out, and the model
 * asked for the zone or the tool it had just created all over again.
 */
fun SystemMessage.toPromptText(): String = buildString {
    appendLine(summary)

    if (commandResults.isNotEmpty()) {
        appendLine()
        commandResults.forEach { result ->
            if (result.details == null) return@forEach
            append("- ${result.details}")
            if (result.isActionCommand && !result.data.isNullOrEmpty()) {
                append(" (${result.data.entries.joinToString(", ") { (k, v) -> "$k: $v" }})")
            }
            if (result.status == CommandStatus.FAILED && result.error != null) {
                append(" → ${result.status.name}: ${result.error}")
            }
            appendLine()
        }
    }

    if (formattedData != null) {
        appendLine()
        append(formattedData)
    }
}

/**
 * What a message from the user's side says to the model, in parts: the parts PromptManager
 * prepared for a message with images, otherwise its [text] alone; nothing for a blank text.
 */
internal fun SessionMessage.userParts(text: String?): List<PromptPart> =
    promptParts ?: listOfNotNull(text?.takeIf { it.isNotBlank() }?.let { PromptPart.Text(it) })

/** The JPEG of an image as base64, by its id: read from its file when the request is built. */
typealias ImageData = (imageId: String) -> String

/** The media type every kept image has: AttachedImages writes JPEG only. */
internal const val IMAGE_MEDIA_TYPE = "image/jpeg"
