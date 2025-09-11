package com.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.assistant.core.ui.Screens.MainScreen
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.ui.UI
import com.assistant.core.ui.*
import com.assistant.core.update.UpdateManager
import com.assistant.core.versioning.MigrationOrchestrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log

class MainActivity : ComponentActivity() {
    
    private lateinit var coordinator: Coordinator
    private lateinit var updateManager: UpdateManager
    private lateinit var migrationOrchestrator: MigrationOrchestrator
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize coordinator
        coordinator = Coordinator(this)
        
        // Initialize update manager
        updateManager = UpdateManager(this)
        
        // Initialize migration orchestrator
        migrationOrchestrator = MigrationOrchestrator(this)
        
        // Perform startup data migrations
        CoroutineScope(Dispatchers.IO).launch {
            Log.d("Migrations", "Starting data migrations...")
            val result = migrationOrchestrator.performStartupMigrations(this@MainActivity)
            
            if (result.success) {
                if (result.migratedTooltypes.isNotEmpty()) {
                    Log.i("Migrations", "Data migrations completed successfully:")
                    result.migratedTooltypes.forEach { migration ->
                        Log.i("Migrations", "  - $migration")
                    }
                } else {
                    Log.i("Migrations", "No data migrations needed")
                }
            } else {
                Log.e("Migrations", "Data migration errors:")
                result.errors.forEach { error ->
                    Log.e("Migrations", "  - ${error.toUserFriendlyMessage()}")
                }
            }
        }
        
        // Check for updates at startup
        updateManager.scheduleUpdateCheck { updateInfo ->
            Log.i("Updates", "Update available: ${updateInfo.version}")
            // TODO: Show notification or dialog with UpdateInfo
        }
        
        // TODO: Preload icons for current theme
        // CoroutineScope(Dispatchers.IO).launch {
        //     val currentTheme = ThemeManager.getCurrentThemeName()
        //     IconManager.preloadThemeIcons(this@MainActivity, currentTheme)
        // }
        
        // Test multi-step operations (uncomment to test)
        //testMultiStepOperations()
        
        setContent {
            MaterialTheme(
                colorScheme = com.assistant.core.ui.CurrentTheme.current.colorScheme
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