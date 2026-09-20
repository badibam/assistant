package com.assistant.core.ai.providers

import com.assistant.core.ai.data.MessageSender
import com.assistant.core.ai.data.PromptData
import com.assistant.core.ai.data.SessionMessage

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
    val s = com.assistant.core.strings.Strings.`for`(context = context)
    val locale = com.assistant.core.utils.LocaleUtils.getAppLocale(context)
    val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", locale)

    val now = System.currentTimeMillis()
    val currentLine = s.shared("ai_prompt_current_datetime")
        .format(dateFormat.format(java.util.Date(now)), now)

    val scheduled = scheduledExecutionTime ?: return currentLine
    val scheduledLine = s.shared("ai_prompt_scheduled_datetime")
        .format(dateFormat.format(java.util.Date(scheduled)), scheduled)
    return "$currentLine\n$scheduledLine"
}
