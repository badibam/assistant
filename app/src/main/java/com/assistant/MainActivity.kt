package com.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.assistant.core.ui.screens.MainScreen
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.commands.CommandStatus
import com.assistant.core.ui.UI
import com.assistant.core.ui.*
import com.assistant.core.themes.CurrentTheme
import com.assistant.core.update.UpdateManager
import com.assistant.core.utils.AppConfigManager
import com.assistant.core.ai.orchestration.AIOrchestrator
import com.assistant.core.scheduling.CoreScheduler
import com.assistant.core.scheduling.CoreSchedulerWorker
import com.assistant.core.notifications.NotificationChannels
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.assistant.core.utils.LogManager
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private lateinit var coordinator: Coordinator
    private lateinit var updateManager: UpdateManager

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize app config cache
        AppConfigManager.initialize(this)

        // Initialize notification channels (Android O+)
        NotificationChannels.initialize(this)

        // Request notification permission (Android 13+)
        requestNotificationPermissionIfNeeded()

        // Initialize model price manager (async, non-blocking)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                com.assistant.core.ai.utils.ModelPriceManager.initialize(this@MainActivity)
            } catch (e: Exception) {
                LogManager.service("Failed to initialize ModelPriceManager: ${e.message}", "WARN")
            }
        }

        // Initialize AI orchestrator singleton (V2 - suspend function)
        CoroutineScope(Dispatchers.Main).launch {
            try {
                AIOrchestrator.initialize(this@MainActivity)
            } catch (e: Exception) {
                LogManager.service("Failed to initialize AIOrchestrator: ${e.message}", "ERROR", e)
            }
        }

        // Initialize CoreScheduler with 1-minute internal heartbeat for app-open reactivity
        CoreScheduler.initialize(this)

        // Schedule WorkManager for app-closed scheduling (15 min interval)
        // Complements CoreScheduler's 1-minute heartbeat for app-open scenarios
        scheduleCoreSchedulerWorker()

        // Initialize coordinator
        coordinator = Coordinator(this)

        // Initialize update manager
        updateManager = UpdateManager(this)

        // Check for updates at startup
        updateManager.scheduleUpdateCheck { updateInfo ->
            LogManager.service("Update available: ${updateInfo.version}")
            // TODO: Show notification or dialog with UpdateInfo
        }
        
        // Preload icons for current theme using multi-step operations
        startIconPreloading()

        // Auto-retry pending transcriptions on app startup
        retryPendingTranscriptions()

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

    /**
     * Schedule CoreScheduler periodic worker for when app is closed.
     * WorkManager minimum interval: 15 minutes.
     * For app-open reactivity, CoreScheduler has internal 1-minute heartbeat.
     *
     * CoreScheduler handles:
     * - AI automations (via AIOrchestrator)
     * - Tool schedulers (via discovery pattern)
     */
    /**
     * Request POST_NOTIFICATIONS permission for Android 13+ (API 33+)
     *
     * Android 13 introduced runtime permission for notifications.
     * Without this, notifications won't be shown even if channels are created.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    private fun scheduleCoreSchedulerWorker() {
        LogManager.service("scheduleCoreSchedulerWorker: Starting WorkManager registration", "INFO")

        try {
            val intervalMinutes = 15L // Minimum allowed by WorkManager
            LogManager.service("scheduleCoreSchedulerWorker: Creating PeriodicWorkRequest with interval=${intervalMinutes}min", "DEBUG")

            val workRequest = PeriodicWorkRequestBuilder<CoreSchedulerWorker>(
                intervalMinutes, TimeUnit.MINUTES
            ).build()

            LogManager.service("scheduleCoreSchedulerWorker: Enqueueing work request with REPLACE policy", "DEBUG")

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "core_scheduler",
                ExistingPeriodicWorkPolicy.REPLACE, // Replace to update interval if changed
                workRequest
            )

            LogManager.service("scheduleCoreSchedulerWorker: SUCCESS - Core scheduler registered (${intervalMinutes}min periodic tick for app-closed)", "INFO")
        } catch (e: Exception) {
            LogManager.service("scheduleCoreSchedulerWorker: FAILED - ${e.message}", "ERROR", e)
        }
    }

    /**
     * Auto-retry pending transcriptions on app startup
     *
     * Scans all tool_data entries for pending transcriptions and re-launches them.
     * This ensures transcriptions continue after app restart.
     */
    private fun retryPendingTranscriptions() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                LogManager.service("Scanning for pending transcriptions...")

                // List pending transcriptions via service
                val result = coordinator.processUserAction("transcription.list_pending", mapOf<String, Any>())

                if (result.status == CommandStatus.SUCCESS) {
                    val pendingList = result.data?.get("pending") as? List<*>

                    if (pendingList != null && pendingList.isNotEmpty()) {
                        LogManager.service("Found ${pendingList.size} pending transcription(s), retrying...")

                        // Re-launch each pending transcription
                        pendingList.forEach { item ->
                            if (item is Map<*, *>) {
                                val entryId = item["entryId"] as? String
                                val toolInstanceId = item["toolInstanceId"] as? String
                                val tooltype = item["tooltype"] as? String
                                val fieldName = item["fieldName"] as? String
                                val audioFile = item["audioFile"] as? String
                                val model = item["model"] as? String
                                val segmentsJson = item["segmentsTimestamps"]

                                if (entryId != null && toolInstanceId != null && tooltype != null &&
                                    fieldName != null && audioFile != null && model != null && segmentsJson != null) {

                                    LogManager.service("Retrying transcription: entry=$entryId, field=$fieldName")

                                    // Launch transcription with isRetry=true
                                    coordinator.processUserAction("transcription.start", mapOf(
                                        "entryId" to entryId,
                                        "toolInstanceId" to toolInstanceId,
                                        "tooltype" to tooltype,
                                        "fieldName" to fieldName,
                                        "audioFile" to audioFile,
                                        "segmentsTimestamps" to segmentsJson,
                                        "isRetry" to true
                                    ))
                                }
                            }
                        }

                        LogManager.service("Pending transcriptions retry initiated")
                    } else {
                        LogManager.service("No pending transcriptions found")
                    }
                } else {
                    LogManager.service("Failed to list pending transcriptions: ${result.error}", "WARN")
                }

            } catch (e: Exception) {
                LogManager.service("Error retrying pending transcriptions: ${e.message}", "ERROR", e)
            }
        }
    }
}