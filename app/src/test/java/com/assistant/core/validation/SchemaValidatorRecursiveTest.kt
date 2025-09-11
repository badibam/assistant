package com.assistant.core.validation

import android.content.Context
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.*
import com.assistant.core.validation.SchemaProvider

/**
 * Tests for recursive validation of nested structures
 */
class SchemaValidatorRecursiveTest {

    @Test
    fun testArrayRecursiveValidation_Success() {
        val schemaWithNestedValidation = """
            {
                "type": "object",
                "properties": {
                    "items": {
                        "type": "array",
                        "items": {
                            "type": "object",
                            "properties": {
                                "name": {"type": "string", "minLength": 2},
                                "quantity": {"type": "number", "minimum": 0}
                            },
                            "required": ["name", "quantity"]
                        }
                    }
                },
                "required": ["items"]
            }
        """.trimIndent()
        
        val validArrayData = mapOf(
            "items" to listOf(
                mapOf("name" to "Item1", "quantity" to 10),
                mapOf("name" to "Item2", "quantity" to 20),
                mapOf("name" to "Item3", "quantity" to 0)
            )
        )
        
        // Create mock SchemaProvider and Context
        val mockProvider = mock(SchemaProvider::class.java)
        val mockContext = mock(Context::class.java)
        `when`(mockProvider.getSchema("data")).thenReturn(schemaWithNestedValidation)
        
        val result = SchemaValidator.validate(mockProvider, validArrayData, mockContext, "data")
        
        assertTrue("Array items should be validated recursively", result.isValid)
    }
    
    @Test
    fun testArrayRecursiveValidation_Failure() {
        val schemaWithNestedValidation = """
            {
                "type": "object", 
                "properties": {
                    "items": {
                        "type": "array",
                        "items": {
                            "type": "object",
                            "properties": {
                                "name": {"type": "string", "minLength": 2},
                                "quantity": {"type": "number", "minimum": 0}
                            },
                            "required": ["name", "quantity"]
                        }
                    }
                },
                "required": ["items"]
            }
        """.trimIndent()
        
        val invalidArrayData = mapOf(
            "items" to listOf(
                mapOf("name" to "Item1", "quantity" to 10),
                mapOf("name" to "X", "quantity" to -5),  // name too short, quantity negative
                mapOf("name" to "Item3", "quantity" to 30)
            )
        )
        
        // Create mock SchemaProvider and Context
        val mockProvider = mock(SchemaProvider::class.java)
        val mockContext = mock(Context::class.java)
        `when`(mockProvider.getSchema("data")).thenReturn(schemaWithNestedValidation)
        
        val result = SchemaValidator.validate(mockProvider, invalidArrayData, mockContext, "data")
        
        assertFalse("Should fail due to invalid item in array", result.isValid)
        assertNotNull("Should have error message", result.errorMessage)
        assertTrue("Error should mention item index", 
            result.errorMessage?.contains("élément 2") == true || 
            result.errorMessage?.contains("item") == true)
    }
    
    @Test
    fun testNestedObjectRecursiveValidation() {
        val deepNestedSchema = """
            {
                "type": "object",
                "properties": {
                    "config": {
                        "type": "object",
                        "properties": {
                            "user": {
                                "type": "object", 
                                "properties": {
                                    "profile": {
                                        "type": "object",
                                        "properties": {
                                            "name": {"type": "string", "minLength": 1},
                                            "email": {"type": "string", "pattern": ".*@.*"}
                                        },
                                        "required": ["name", "email"]
                                    }
                                },
                                "required": ["profile"]
                            }
                        },
                        "required": ["user"]
                    }
                },
                "required": ["config"]
            }
        """.trimIndent()
        
        val invalidDeepData = mapOf(
            "config" to mapOf(
                "user" to mapOf(
                    "profile" to mapOf(
                        "name" to "John",
                        "email" to "invalid-email"  // Missing @
                    )
                )
            )
        )
        
        // Create mock SchemaProvider and Context
        val mockProvider = mock(SchemaProvider::class.java)
        val mockContext = mock(Context::class.java)
        `when`(mockProvider.getSchema("data")).thenReturn(deepNestedSchema)
        
        val result = SchemaValidator.validate(mockProvider, invalidDeepData, mockContext, "data")
        
        assertFalse("Should fail due to invalid email pattern", result.isValid) 
        assertNotNull("Should have error message", result.errorMessage)
    }
    
    @Test
    fun testArrayOfObjectsWithNestedArrays() {
        val complexSchema = """
            {
                "type": "object",
                "properties": {
                    "groups": {
                        "type": "array",
                        "items": {
                            "type": "object",
                            "properties": {
                                "name": {"type": "string"},
                                "members": {
                                    "type": "array",
                                    "items": {
                                        "type": "object",
                                        "properties": {
                                            "id": {"type": "number"},
                                            "name": {"type": "string", "minLength": 1}
                                        },
                                        "required": ["id", "name"]
                                    }
                                }
                            },
                            "required": ["name", "members"]
                        }
                    }
                },
                "required": ["groups"]
            }
        """.trimIndent()
        
        val complexValidData = mapOf(
            "groups" to listOf(
                mapOf(
                    "name" to "Group1",
                    "members" to listOf(
                        mapOf("id" to 1, "name" to "John"),
                        mapOf("id" to 2, "name" to "Jane")
                    )
                ),
                mapOf(
                    "name" to "Group2", 
                    "members" to listOf(
                        mapOf("id" to 3, "name" to "Bob")
                    )
                )
            )
        )
        
        // Create mock SchemaProvider and Context
        val mockProvider = mock(SchemaProvider::class.java)
        val mockContext = mock(Context::class.java)
        `when`(mockProvider.getSchema("data")).thenReturn(complexSchema)
        
        val result = SchemaValidator.validate(mockProvider, complexValidData, mockContext, "data")
        
        assertTrue("Complex nested arrays should validate correctly", result.isValid)
    }
    
    @Test  
    fun testRecursiveValidationWithContext() {
        // Test that context propagation works in recursive validation
        val contextualSchema = """
            {
                "type": "object",
                "properties": {
                    "type": {"type": "string", "enum": ["advanced"]},
                    "items": {
                        "type": "array",
                        "items": {
                            "type": "object",
                            "properties": {
                                "name": {"type": "string"},
                                "advanced_field": {"type": "number"}
                            },
                            "required": ["name", "advanced_field"]
                        }
                    }
                },
                "required": ["type", "items"]
            }
        """.trimIndent()
        
        val dataWithContext = mapOf(
            "type" to "advanced",  // Context that should be auto-detected
            "items" to listOf(
                mapOf("name" to "Item1", "advanced_field" to 100),
                mapOf("name" to "Item2", "advanced_field" to 200)
            )
        )
        
        // Create mock SchemaProvider and Context
        val mockProvider = mock(SchemaProvider::class.java)
        val mockContext = mock(Context::class.java)
        `when`(mockProvider.getSchema("data")).thenReturn(contextualSchema)
        
        val result = SchemaValidator.validate(mockProvider, dataWithContext, mockContext, "data")
        
        assertTrue("Context should not interfere with recursive validation", result.isValid)
    }
}