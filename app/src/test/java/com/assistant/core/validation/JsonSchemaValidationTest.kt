package com.assistant.core.validation

import com.assistant.tools.tracking.TrackingToolType
import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for JSON Schema validation consistency
 * Ensures UI ↔ Schema ↔ Service validation coherence
 * 
 * Note: Using static JSON strings to avoid Android dependencies in unit tests
 */
class JsonSchemaValidationTest {

    @Test
    fun testValidTrackingDataPassesSchemaValidation() {
        // Create valid tracking data JSON manually (avoiding org.json.JSONObject dependency)
        val validationJson = """
        {
            "id": "test-id-123",
            "tool_instance_id": "test-tool-123",
            "zone_name": "Test Zone",
            "tool_instance_name": "Test Tracking",
            "name": "Test Entry",
            "value": "{\"type\":\"numeric\",\"quantity\":\"10\",\"unit\":\"kg\"}",
            "recorded_at": 1693692000000
        }
        """.trimIndent()
        
        // Test that schema validation accepts valid data
        val result = JsonSchemaValidator.validateData(TrackingToolType, validationJson)
        
        assertTrue("Valid tracking data should pass schema validation", result.isValid)
        assertNull("Valid data should have no error message", result.errorMessage)
    }
    
    @Test
    fun testInvalidTrackingDataFailsSchemaValidation() {
        // Create invalid tracking data JSON with empty required fields
        val invalidJson = """
        {
            "id": "",
            "tool_instance_id": "",
            "zone_name": "",
            "tool_instance_name": "Test Tracking",
            "name": "",
            "value": "{\"invalid\":\"json\"}",
            "recorded_at": 1693692000000
        }
        """.trimIndent()
        
        // Test that schema validation rejects invalid data
        val result = JsonSchemaValidator.validateData(TrackingToolType, invalidJson)
        
        assertFalse("Invalid tracking data should fail schema validation", result.isValid)
        assertNotNull("Invalid data should have error message", result.errorMessage)
        
        // Verify error message mentions validation issues
        val errorMessage = result.errorMessage ?: ""
        assertTrue("Error should mention validation issues", 
            errorMessage.isNotEmpty())
    }
    
    @Test
    fun testSchemaValidationHandlesAllTrackingTypes() {
        val trackingTypes = listOf("numeric", "text", "scale", "boolean", "timer", "choice", "counter")
        
        trackingTypes.forEach { trackingType ->
            // Create appropriate value JSON for each tracking type
            val valueJson = when (trackingType) {
                "numeric" -> "{\\\"type\\\":\\\"numeric\\\",\\\"quantity\\\":\\\"10\\\",\\\"unit\\\":\\\"kg\\\"}"
                "text" -> "{\\\"type\\\":\\\"text\\\",\\\"text\\\":\\\"Test text\\\"}"
                "scale" -> "{\\\"type\\\":\\\"scale\\\",\\\"rating\\\":5,\\\"min_value\\\":1,\\\"max_value\\\":10}"
                "boolean" -> "{\\\"type\\\":\\\"boolean\\\",\\\"state\\\":true}"
                "timer" -> "{\\\"type\\\":\\\"timer\\\",\\\"activity\\\":\\\"Test Activity\\\",\\\"duration_minutes\\\":30}"
                "choice" -> "{\\\"type\\\":\\\"choice\\\",\\\"available_options\\\":[\\\"A\\\",\\\"B\\\"],\\\"selected_option\\\":\\\"A\\\"}"
                "counter" -> "{\\\"type\\\":\\\"counter\\\",\\\"increment\\\":1}"
                else -> "{\\\"type\\\":\\\"$trackingType\\\"}"
            }
            
            val validationJson = """
            {
                "id": "test-id-$trackingType",
                "tool_instance_id": "test-tool-123",
                "zone_name": "Test Zone",
                "tool_instance_name": "Test Tracking $trackingType",
                "name": "Test $trackingType Entry",
                "value": "$valueJson",
                "recorded_at": 1693692000000
            }
            """.trimIndent()
            
            val result = JsonSchemaValidator.validateData(TrackingToolType, validationJson)
            
            assertTrue("Tracking type '$trackingType' should be valid", result.isValid)
        }
    }
    
    @Test
    fun testSchemaValidationRobustness() {
        // Test with malformed JSON structure
        val malformedJson = """
        {
            "tool_instance_id": "test-tool-123",
            "zone_name": "Test Zone",
            "name": "Test Entry",
            "value": "{invalid json",
            "recorded_at": "not-a-number"
        }
        """.trimIndent()
        
        val result = JsonSchemaValidator.validateData(TrackingToolType, malformedJson)
        
        assertFalse("Malformed JSON should fail validation", result.isValid)
        assertNotNull("Malformed JSON should have error message", result.errorMessage)
    }
    
    @Test
    fun testSchemaValidationPerformance() {
        // Test that validation performs reasonably well
        val validationJson = """
        {
            "id": "test-perf-123",
            "tool_instance_id": "test-tool-123",
            "zone_name": "Test Zone",
            "tool_instance_name": "Test Tracking",
            "name": "Performance Test Entry",
            "value": "{\"type\":\"numeric\",\"quantity\":\"10\",\"unit\":\"kg\"}",
            "recorded_at": 1693692000000
        }
        """.trimIndent()
        
        // Measure validation time for 10 iterations (reduced for faster tests)
        val startTime = System.currentTimeMillis()
        repeat(10) {
            JsonSchemaValidator.validateData(TrackingToolType, validationJson)
        }
        val endTime = System.currentTimeMillis()
        
        val avgTimeMs = (endTime - startTime) / 10.0
        
        // Validation should be reasonably fast (< 100ms per validation on average)
        assertTrue("Schema validation should be performant (avg ${avgTimeMs}ms)", avgTimeMs < 100.0)
        
        println("Average schema validation time: ${avgTimeMs}ms")
    }
}