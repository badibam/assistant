package com.assistant.core.ai.providers

import com.assistant.core.ai.data.MessageSender
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
