package com.assistant.core.commands;

/**
 * Parses JSON commands from various sources
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00006\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\bJ\u0014\u0010\t\u001a\b\u0012\u0004\u0012\u00020\b0\n2\u0006\u0010\u000b\u001a\u00020\u0006J\u001a\u0010\f\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\b0\r0\n2\u0006\u0010\u000b\u001a\u00020\u0006J\u000e\u0010\u000e\u001a\u00020\u00062\u0006\u0010\u000f\u001a\u00020\u0010J\u0014\u0010\u0011\u001a\u00020\u00062\f\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\u00100\rR\u000e\u0010\u0003\u001a\u00020\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0013"}, d2 = {"Lcom/assistant/core/commands/CommandParser;", "", "()V", "gson", "Lcom/google/gson/Gson;", "commandToJson", "", "command", "Lcom/assistant/core/commands/Command;", "parseCommand", "Lcom/assistant/core/commands/ParseResult;", "jsonString", "parseCommands", "", "resultToJson", "result", "Lcom/assistant/core/commands/CommandResult;", "resultsToJson", "results", "app_debug"})
public final class CommandParser {
    @org.jetbrains.annotations.NotNull()
    private final com.google.gson.Gson gson = null;
    
    public CommandParser() {
        super();
    }
    
    /**
     * Parse a single command from JSON string
     */
    @org.jetbrains.annotations.NotNull()
    public final com.assistant.core.commands.ParseResult<com.assistant.core.commands.Command> parseCommand(@org.jetbrains.annotations.NotNull()
    java.lang.String jsonString) {
        return null;
    }
    
    /**
     * Parse multiple commands from JSON array
     */
    @org.jetbrains.annotations.NotNull()
    public final com.assistant.core.commands.ParseResult<java.util.List<com.assistant.core.commands.Command>> parseCommands(@org.jetbrains.annotations.NotNull()
    java.lang.String jsonString) {
        return null;
    }
    
    /**
     * Convert command back to JSON string
     */
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String commandToJson(@org.jetbrains.annotations.NotNull()
    com.assistant.core.commands.Command command) {
        return null;
    }
    
    /**
     * Convert command result to JSON string
     */
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String resultToJson(@org.jetbrains.annotations.NotNull()
    com.assistant.core.commands.CommandResult result) {
        return null;
    }
    
    /**
     * Convert list of results to JSON string
     */
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String resultsToJson(@org.jetbrains.annotations.NotNull()
    java.util.List<com.assistant.core.commands.CommandResult> results) {
        return null;
    }
}