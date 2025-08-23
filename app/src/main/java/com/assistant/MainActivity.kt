package com.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.assistant.ui.screens.MainScreen
import com.assistant.core.coordinator.Coordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log

class MainActivity : ComponentActivity() {
    
    private lateinit var coordinator: Coordinator
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize coordinator
        coordinator = Coordinator(this)
        
        // TODO: Preload icons for current theme
        // CoroutineScope(Dispatchers.IO).launch {
        //     val currentTheme = ThemeManager.getCurrentThemeName()
        //     IconManager.preloadThemeIcons(this@MainActivity, currentTheme)
        // }
        
        // Test multi-step operations (uncomment to test)
        //testMultiStepOperations()
        
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
    
    /**
     * Test function for multi-step operations
     */
    private fun testMultiStepOperations() {
        CoroutineScope(Dispatchers.Main).launch {
            Log.d("MultiStepTest", "Starting multi-step operations test")
            
            // Test 1: Create a zone first
            Log.d("MultiStepTest", "Creating test zone...")
            val zoneResult = coordinator.processUserAction("create->zone", mapOf(
                "name" to "Test Zone"
            ))
            Log.d("MultiStepTest", "Zone creation result: ${zoneResult.status}")
            
            // Test 2: Create a tool instance 
            Log.d("MultiStepTest", "Creating tool instance...")
            val toolResult = coordinator.processUserAction("create->tool_instance", mapOf(
                "zone_id" to "test-zone-id",
                "tool_type" to "tracking",
                "name" to "Test Tracking Tool"
            ))
            Log.d("MultiStepTest", "Tool creation result: ${toolResult.status}")
            
            // Test 3: Add some tracking data
            Log.d("MultiStepTest", "Adding tracking data...")
            repeat(5) { i ->
                coordinator.processUserAction("create->tracking_data", mapOf(
                    "tool_instance_id" to "test-tool-id",
                    "zone_name" to "Test Zone",
                    "tool_instance_name" to "Test Tracking Tool",
                    "name" to "test_metric_$i",
                    "value" to "${(i + 1) * 10}"
                ))
            }
            
            // Test 4: Launch multi-step correlation analysis
            Log.d("MultiStepTest", "Launching correlation analysis (multi-step)...")
            val correlationResult = coordinator.processUserAction("create->correlation_analysis", mapOf(
                "tool_instance_id" to "test-tool-id"
            ))
            
            Log.d("MultiStepTest", "Correlation analysis started:")
            Log.d("MultiStepTest", "- Status: ${correlationResult.status}")
            Log.d("MultiStepTest", "- Requires Background: ${correlationResult.requiresBackground}")
            Log.d("MultiStepTest", "- Message: ${correlationResult.message}")
            
            // Test 5: Add more operations while correlation is running
            Log.d("MultiStepTest", "Adding more operations while background processing...")
            repeat(3) { i ->
                val quickResult = coordinator.processUserAction("create->tracking_data", mapOf(
                    "tool_instance_id" to "test-tool-id",
                    "zone_name" to "Test Zone", 
                    "tool_instance_name" to "Test Tracking Tool",
                    "name" to "quick_metric_$i",
                    "value" to "${i + 100}"
                ))
                Log.d("MultiStepTest", "Quick operation $i result: ${quickResult.status}")
            }
            
            // Check queue status
            val queueInfo = coordinator.getQueueInfo()
            Log.d("MultiStepTest", "Queue status: $queueInfo")
            
            Log.d("MultiStepTest", "Multi-step test completed. Check logs for background completion.")
        }
    }
}