package com.assistant.tools.base

/**
 * Contract interface that all tool types must implement
 * Defines mandatory elements for tool type integration
 */
interface ToolTypeContract {
    
    /**
     * Get path to metadata.json file for this tool type
     */
    fun getMetadataPath(): String = "metadata.json"
    
    /**
     * Get default configuration template
     */
    fun getDefaultConfig(): String
    
    /**
     * Validate a configuration JSON
     */
    fun validateConfig(configJson: String): ConfigValidationResult
    
    /**
     * Execute an operation with given parameters
     */
    suspend fun execute(operation: String, params: Map<String, Any>): OperationResult
    
    /**
     * Get available operations for this tool type
     */
    fun getAvailableOperations(): List<String>
}

/**
 * Result of configuration validation
 */
data class ConfigValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    companion object {
        fun valid() = ConfigValidationResult(true)
        fun invalid(errors: List<String>) = ConfigValidationResult(false, errors)
    }
}

/**
 * Result of tool operation execution
 */
data class OperationResult(
    val success: Boolean,
    val message: String? = null,
    val data: Map<String, Any>? = null,
    val error: String? = null
) {
    companion object {
        fun success(message: String? = null, data: Map<String, Any>? = null) = 
            OperationResult(true, message, data)
        fun failure(error: String) = 
            OperationResult(false, error = error)
    }
}