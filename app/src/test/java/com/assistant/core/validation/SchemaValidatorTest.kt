package com.assistant.core.validation

import org.junit.Test
import org.junit.Assert.*

/**
 * Basic tests for new SchemaValidator architecture
 */
class SchemaValidatorTest {

    @Test
    fun testBasicValidation_Success() {
        // Simple schema for testing
        val schema = """
            {
                "type": "object",
                "properties": {
                    "name": {"type": "string"},
                    "age": {"type": "number"}
                },
                "required": ["name"]
            }
        """.trimIndent()
        
        val validData = mapOf(
            "name" to "John",
            "age" to 30
        )
        
        val result = SchemaValidator.validate(schema, validData)
        
        assertTrue("Validation should succeed", result.isValid)
        assertNull("No error message expected", result.errorMessage)
    }
    
    @Test
    fun testBasicValidation_Failure() {
        val schema = """
            {
                "type": "object",
                "properties": {
                    "name": {"type": "string"},
                    "age": {"type": "number"}
                },
                "required": ["name"]
            }
        """.trimIndent()
        
        val invalidData = mapOf(
            "age" to 30
            // name is missing
        )
        
        val result = SchemaValidator.validate(schema, invalidData)
        
        assertFalse("Validation should fail", result.isValid)
        assertNotNull("Error message expected", result.errorMessage)
        assertTrue("Should contain missing field error", 
            result.errorMessage?.contains("name") == true)
    }
    
    @Test
    fun testValidationWithSchemaProvider() {
        val testProvider = object : SchemaProvider {
            override fun getConfigSchema(): String = """
                {
                    "type": "object",
                    "properties": {
                        "title": {"type": "string"}
                    },
                    "required": ["title"]
                }
            """.trimIndent()
            
            override fun getDataSchema(): String? = null
        }
        
        val validConfig = mapOf("title" to "Test Config")
        val result = SchemaValidator.validate(testProvider, validConfig, useDataSchema = false)
        
        assertTrue("Config validation should succeed", result.isValid)
    }
}