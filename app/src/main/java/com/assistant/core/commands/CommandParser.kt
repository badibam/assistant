package com.assistant.core.commands

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken

/**
 * Parses JSON commands from various sources
 */
class CommandParser {
    private val gson = Gson()
    
    /**
     * Parse a single command from JSON string
     */
    fun parseCommand(jsonString: String): ParseResult<Command> {
        return try {
            val command = gson.fromJson(jsonString, Command::class.java)
            
            if (command.action.isBlank()) {
                ParseResult.failure("Action field is required")
            } else {
                ParseResult.success(command)
            }
        } catch (e: JsonSyntaxException) {
            ParseResult.failure("Invalid JSON format: ${e.message}")
        } catch (e: Exception) {
            ParseResult.failure("Failed to parse command: ${e.message}")
        }
    }
    
    /**
     * Parse multiple commands from JSON array
     */
    fun parseCommands(jsonString: String): ParseResult<List<Command>> {
        return try {
            val listType = object : TypeToken<List<Command>>() {}.type
            val commands: List<Command> = gson.fromJson(jsonString, listType)
            
            val errors = mutableListOf<String>()
            commands.forEachIndexed { index, command ->
                if (command.action.isBlank()) {
                    errors.add("Command at index $index: Action field is required")
                }
            }
            
            if (errors.isEmpty()) {
                ParseResult.success(commands)
            } else {
                ParseResult.failure("Validation errors: ${errors.joinToString(", ")}")
            }
        } catch (e: JsonSyntaxException) {
            ParseResult.failure("Invalid JSON format: ${e.message}")
        } catch (e: Exception) {
            ParseResult.failure("Failed to parse commands: ${e.message}")
        }
    }
    
    /**
     * Convert command back to JSON string
     */
    fun commandToJson(command: Command): String {
        return gson.toJson(command)
    }
    
    /**
     * Convert command result to JSON string
     */
    fun resultToJson(result: CommandResult): String {
        return gson.toJson(result)
    }
    
    /**
     * Convert list of results to JSON string
     */
    fun resultsToJson(results: List<CommandResult>): String {
        return gson.toJson(results)
    }
}

/**
 * Result of parsing operation
 */
sealed class ParseResult<T> {
    data class Success<T>(val data: T) : ParseResult<T>()
    data class Failure<T>(val error: String) : ParseResult<T>()
    
    companion object {
        fun <T> success(data: T) = Success(data)
        fun <T> failure(error: String) = Failure<T>(error)
    }
    
    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure
}