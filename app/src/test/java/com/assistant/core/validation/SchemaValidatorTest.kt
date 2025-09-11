package com.assistant.core.validation

import android.content.Context
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.*
import com.assistant.core.validation.SchemaProvider

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
        
        // Create mock SchemaProvider and Context
        val mockProvider = mock(SchemaProvider::class.java)
        val mockContext = mock(Context::class.java)
        `when`(mockProvider.getSchema("data")).thenReturn(schema)
        
        val result = SchemaValidator.validate(mockProvider, validData, mockContext, "data")
        
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
        
        // Create mock SchemaProvider and Context
        val mockProvider = mock(SchemaProvider::class.java)
        val mockContext = mock(Context::class.java)
        `when`(mockProvider.getSchema("data")).thenReturn(schema)
        
        val result = SchemaValidator.validate(mockProvider, invalidData, mockContext, "data")
        
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
        // Create mock Context
        val mockContext = mock(Context::class.java)
        
        val result = SchemaValidator.validate(testProvider, validConfig, mockContext, "config")
        
        assertTrue("Config validation should succeed", result.isValid)
    }
    
    @Test
    fun testErrorPrioritization_TypeErrorOverSchemaError() {
        // Schema with conditional validation that can generate multiple errors
        val schema = """
            {
                "type": "object",
                "properties": {
                    "value": {
                        "type": "object",
                        "allOf": [
                            {
                                "properties": {
                                    "type": {"type": "string"}
                                }
                            },
                            {
                                "if": {"properties": {"type": {"const": "numeric"}}},
                                "then": {
                                    "properties": {
                                        "quantity": {"type": "number"},
                                        "unit": {"type": "string"}
                                    }
                                }
                            }
                        ]
                    }
                }
            }
        """.trimIndent()
        
        // Data that triggers both "not defined" and "wrong type" errors
        val wrongTypeData = mapOf(
            "value" to mapOf(
                "type" to "numeric",
                "quantity" to "abc",  // String instead of number
                "unit" to "kg"
            )
        )
        
        // Create mock SchemaProvider and Context
        val mockProvider = mock(SchemaProvider::class.java)
        val mockContext = mock(Context::class.java)
        `when`(mockProvider.getSchema("data")).thenReturn(schema)
        
        val result = SchemaValidator.validate(mockProvider, wrongTypeData, mockContext, "data")
        
        assertFalse("Validation should fail", result.isValid)
        
        // Should prioritize type error over schema structure error
        val errorMsg = result.errorMessage!!
        assertTrue("Should show type error with French message", 
            errorMsg.contains("trouvé") && errorMsg.contains("attendu"))
        assertFalse("Should not show schema structure error", 
            errorMsg.contains("not defined in the schema"))
    }
    
    @Test
    fun testErrorPrioritization_RequiredFieldError() {
        val schema = """
            {
                "type": "object",
                "properties": {
                    "name": {"type": "string"}
                },
                "required": ["name"]
            }
        """.trimIndent()
        
        val missingData = mapOf<String, Any>()
        
        // Create mock SchemaProvider and Context
        val mockProvider = mock(SchemaProvider::class.java)
        val mockContext = mock(Context::class.java)
        `when`(mockProvider.getSchema("data")).thenReturn(schema)
        
        val result = SchemaValidator.validate(mockProvider, missingData, mockContext, "data")
        
        assertFalse("Validation should fail", result.isValid)
        assertTrue("Should show required field error", 
            result.errorMessage?.contains("obligatoire") == true)
    }
}