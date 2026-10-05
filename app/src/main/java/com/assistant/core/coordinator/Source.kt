package com.assistant.core.coordinator

/**
 * Who a command comes from (Origin carries it through the calls it makes).
 */
enum class Source {
    /** A person, from a screen */
    USER,
    /** The AI, in a chat or an automation */
    AI,
    /** An AI outside the app, a client of its MCP server */
    EXTERNAL,
    /** A scheduler's task, which no one is watching */
    SCHEDULER,
    /** The app itself: its start, what it builds for a prompt */
    SYSTEM
}