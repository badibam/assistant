package com.assistant.core.validation

import org.junit.Test
import org.junit.Assert.*

/**
 * Advanced tests for SchemaValidator with complex scenarios
 */
class SchemaValidatorAdvancedTest {

    @Test
    fun testTrackingData_WithContext() {
        // Simulated tracking schema with conditional properties
        val trackingSchema = """
            {
                "type": "object",
                "properties": {
                    "tool_instance_id": {"type": "string"},
                    "value": {
                        "type": "object",
                        "properties": {
                            "type": {"type": "string", "enum": ["numeric", "text"]},
                            "quantity": {"type": "number"},
                            "text_value": {"type": "string"}
                        },
                        "required": ["type"]
                    }
                },
                "required": ["tool_instance_id", "value"]
            }
        """.trimIndent()
        
        val validTrackingData = mapOf(
            "tool_instance_id" to "test-123",
            "value" to mapOf(
                "type" to "numeric",
                "quantity" to 75.5
            )
        )
        
        val result = SchemaValidator.validate(trackingSchema, validTrackingData)
        
        assertTrue("Tracking data should be valid", result.isValid)
    }
    
    @Test
    fun testNestedObjects() {
        val nestedSchema = """
            {
                "type": "object",
                "properties": {
                    "user": {
                        "type": "object",
                        "properties": {
                            "profile": {
                                "type": "object",
                                "properties": {
                                    "name": {"type": "string"},
                                    "age": {"type": "number"}
                                },
                                "required": ["name"]
                            }
                        },
                        "required": ["profile"]
                    }
                },
                "required": ["user"]
            }
        """.trimIndent()
        
        val nestedData = mapOf(
            "user" to mapOf(
                "profile" to mapOf(
                    "name" to "John",
                    "age" to 30
                )
            )
        )
        
        val result = SchemaValidator.validate(nestedSchema, nestedData)
        
        assertTrue("Nested objects should validate correctly", result.isValid)
    }
    
    @Test 
    fun testArraysInData() {
        val arraySchema = """
            {
                "type": "object",
                "properties": {
                    "items": {
                        "type": "array",
                        "items": {
                            "type": "object",
                            "properties": {
                                "name": {"type": "string"},
                                "value": {"type": "number"}
                            },
                            "required": ["name"]
                        }
                    }
                },
                "required": ["items"]
            }
        """.trimIndent()
        
        val arrayData = mapOf(
            "items" to listOf(
                mapOf("name" to "Item1", "value" to 10),
                mapOf("name" to "Item2", "value" to 20)
            )
        )
        
        val result = SchemaValidator.validate(arraySchema, arrayData)
        
        assertTrue("Arrays should validate correctly", result.isValid)
    }
    
    @Test
    fun testContextAutoDetection() {
        // Schema without conditions - context should not break anything
        val simpleSchema = """
            {
                "type": "object",
                "properties": {
                    "type": {"type": "string"},
                    "value": {"type": "number"}
                },
                "required": ["type", "value"]
            }
        """.trimIndent()
        
        val dataWithContext = mapOf(
            "type" to "numeric",  // This should be auto-detected as context
            "value" to 42
        )
        
        val result = SchemaValidator.validate(simpleSchema, dataWithContext)
        
        assertTrue("Auto-detected context should not break validation", result.isValid)
    }
    
    @Test
    fun testValidationError_DetailedMessage() {
        val schema = """
            {
                "type": "object",
                "properties": {
                    "name": {"type": "string"},
                    "age": {"type": "number", "minimum": 0, "maximum": 150}
                },
                "required": ["name", "age"]
            }
        """.trimIndent()
        
        val invalidData = mapOf(
            "name" to "John",
            "age" to "not-a-number"  // Wrong type
        )
        
        val result = SchemaValidator.validate(schema, invalidData)
        
        assertFalse("Should fail validation", result.isValid)
        assertNotNull("Should have error message", result.errorMessage)
        assertTrue("Error should mention age field", 
            result.errorMessage?.contains("age") == true)
    }
}