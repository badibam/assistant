package com.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.assistant.core.ui.screens.MainScreen
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.ui.UI
import com.assistant.core.ui.*
import com.assistant.core.themes.CurrentTheme
import com.assistant.core.update.UpdateManager
import com.assistant.core.versioning.MigrationOrchestrator
import com.assistant.core.utils.AppConfigManager
import com.assistant.core.ai.orchestration.AIOrchestrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.assistant.core.utils.LogManager

class MainActivity : ComponentActivity() {

    private lateinit var coordinator: Coordinator
    private lateinit var updateManager: UpdateManager
    private lateinit var migrationOrchestrator: MigrationOrchestrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize app config cache
        AppConfigManager.initialize(this)

        // Initialize model price manager (async, non-blocking)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                com.assistant.core.ai.utils.ModelPriceManager.initialize(this@MainActivity)
            } catch (e: Exception) {
                LogManager.service("Failed to initialize ModelPriceManager: ${e.message}", "WARN")
            }
        }

        // Initialize AI orchestrator singleton
        AIOrchestrator.initialize(this)

        // Initialize coordinator
        coordinator = Coordinator(this)

        // Initialize update manager
        updateManager = UpdateManager(this)

        // Initialize migration orchestrator
        migrationOrchestrator = MigrationOrchestrator(this)

        // Check for updates at startup
        updateManager.scheduleUpdateCheck { updateInfo ->
            LogManager.service("Update available: ${updateInfo.version}")
            // TODO: Show notification or dialog with UpdateInfo
        }
        
        // Preload icons for current theme using multi-step operations
        startIconPreloading()
        
        // Test multi-step operations (uncomment to test)
        //testMultiStepOperations()
        
        setContent {
            MaterialTheme(
                colorScheme = CurrentTheme.getCurrentColorScheme()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize()
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
            LogManager.coordination("Starting multi-step operations test")
            
            // Test 1: Create a zone first
            LogManager.coordination("Creating test zone...")
            val zoneResult = coordinator.processUserAction("zones.create", mapOf(
                "name" to "Test Zone"
            ))
            LogManager.coordination("Zone creation result: ${zoneResult.status}")
            
            // Test 2: Create a tool instance 
            LogManager.coordination("Creating tool instance...")
            val toolResult = coordinator.processUserAction("tools.create", mapOf(
                "zone_id" to "test-zone-id",
                "tool_type" to "tracking",
                "name" to "Test Tracking Tool"
            ))
            LogManager.coordination("Tool creation result: ${toolResult.status}")
            
            // Test 3: Add some tracking data
            LogManager.coordination("Adding tracking data...")
            repeat(5) { i ->
                coordinator.processUserAction("tool_data.create", mapOf(
                    "tool_instance_id" to "test-tool-id",
                    "zone_name" to "Test Zone",
                    "tool_instance_name" to "Test Tracking Tool",
                    "name" to "test_metric_$i",
                    "value" to "${(i + 1) * 10}"
                ))
            }
            
            // Test 4: Launch multi-step correlation analysis
            LogManager.coordination("Launching correlation analysis (multi-step)...")
            val correlationResult = coordinator.processUserAction("correlation.create", mapOf(
                "tool_instance_id" to "test-tool-id"
            ))
            
            LogManager.coordination("Correlation analysis started:")
            LogManager.coordination("- Status: ${correlationResult.status}")
            LogManager.coordination("- Requires Background: ${correlationResult.requiresBackground}")
            LogManager.coordination("- Message: ${correlationResult.message}")
            
            // Test 5: Add more operations while correlation is running
            LogManager.coordination("Adding more operations while background processing...")
            repeat(3) { i ->
                val quickResult = coordinator.processUserAction("tool_data.create", mapOf(
                    "tool_instance_id" to "test-tool-id",
                    "zone_name" to "Test Zone", 
                    "tool_instance_name" to "Test Tracking Tool",
                    "name" to "quick_metric_$i",
                    "value" to "${i + 100}"
                ))
                LogManager.coordination("Quick operation $i result: ${quickResult.status}")
            }
            
            // Check queue status
            val queueInfo = coordinator.getQueueInfo()
            LogManager.coordination("Queue status: $queueInfo")
            
            LogManager.coordination("Multi-step test completed. Check logs for background completion.")
        }
    }
    
    /**
     * Start icon preloading in background using multi-step operations
     * Non-blocking operation that loads all theme icons for better UX
     */
    private fun startIconPreloading() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = coordinator.processUserAction("icon_preload.preload_theme_icons", mapOf(
                    "operationId" to "startup_preload_${System.currentTimeMillis()}"
                ))
                
                LogManager.service("Started icon preloading: ${result.status} - ${result.message}")
                
            } catch (e: Exception) {
                LogManager.service("Failed to start icon preloading: ${e.message}", "WARN", e)
            }
        }
    }
}