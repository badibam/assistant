package com.assistant.core.ai.prompts

import com.assistant.core.ai.data.DataCommand
import com.assistant.core.utils.LogManager
import java.security.MessageDigest

/**
 * Handles command deduplication across all 4 prompt levels
 *
 * Two phases of deduplication:
 * 1. Remove identical commands (same hash)
 * 2. Remove included commands (business logic inclusion)
 *
 * Preserves order for cache efficiency - first command in list has priority
 */
object QueryDeduplicator {

    /**
     * Complete deduplication pipeline for pre-execution commands
     * Combines identical removal and business logic inclusion while preserving order
     */
    fun deduplicateCommands(orderedCommands: List<DataCommand>): List<DataCommand> {
        LogManager.aiPrompt("QueryDeduplicator processing ${orderedCommands.size} commands")

        // Phase 1: Remove identical commands (same hash ID)
        val withoutDuplicates = removeIdenticalCommands(orderedCommands)
        LogManager.aiPrompt("After removing identical: ${withoutDuplicates.size} commands")

        // Phase 2: Remove included commands (business logic)
        val withoutInclusions = removeIncludedCommands(withoutDuplicates)
        LogManager.aiPrompt("After removing included: ${withoutInclusions.size} commands")

        LogManager.aiPrompt("Command deduplication completed: ${orderedCommands.size} → ${withoutInclusions.size}")
        return withoutInclusions
    }

    /**
     * Phase 1: Remove commands with identical content (same hash)
     * First occurrence in list is kept (order preservation)
     */
    fun removeIdenticalCommands(commands: List<DataCommand>): List<DataCommand> {
        val seenHashes = mutableSetOf<String>()
        val result = mutableListOf<DataCommand>()

        commands.forEach { command ->
            val hash = generateCommandHash(command)
            if (hash !in seenHashes) {
                seenHashes.add(hash)
                result.add(command)
            } else {
                LogManager.aiPrompt("Removed identical command: ${command.type} (hash: ${hash.take(8)}...)")
            }
        }

        return result
    }

    /**
     * Phase 2: Remove commands that are included by other commands
     * Business logic inclusion - more general commands include specific ones
     */
    fun removeIncludedCommands(commands: List<DataCommand>): List<DataCommand> {
        val result = mutableListOf<DataCommand>()

        commands.forEach { candidate ->
            val isIncluded = result.any { existing ->
                commandIncludes(existing, candidate)
            }

            if (!isIncluded) {
                result.add(candidate)
                LogManager.aiPrompt("Kept command: ${candidate.type}")
            } else {
                LogManager.aiPrompt("Removed included command: ${candidate.type}")
            }
        }

        return result
    }

    /**
     * Generate deterministic hash for command identity
     * Hash includes: type + sorted params + isRelative
     */
    private fun generateCommandHash(command: DataCommand): String {
        // Sort parameters for deterministic hash
        val sortedParams = command.params.toSortedMap().toString()
        val content = "${command.type}|$sortedParams|${command.isRelative}"

        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(content.toByteArray())
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            LogManager.aiPrompt("Hash generation failed, using fallback: ${e.message}", "WARN")
            content.hashCode().toString()
        }
    }

    /**
     * Check if command1 includes/supersedes command2 based on business logic
     * Implements smart inclusion rules to avoid redundant commands
     */
    private fun commandIncludes(command1: DataCommand, command2: DataCommand): Boolean {
        // TODO: Implement business logic inclusion rules for DataCommand
        // - Zone config commands include tool config commands for same zone
        // - Full tool data includes sample data for same instance
        // - Sample data includes field data for same instance
        // - Larger time periods include smaller periods for same tool instance

        LogManager.aiPrompt("TODO: commandIncludes() business logic - returning false for now")
        return false
    }

    // TODO: Implement helper methods for command inclusion logic when needed
    // - sameToolInstanceWithLargerPeriod()
    // - getZoneIdForTool()
    // - other business logic helpers
}