package com.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import com.assistant.core.coordinator.isSuccess
import androidx.compose.runtime.setValue
import com.assistant.core.ui.screens.MainScreen
import com.assistant.core.commands.CommandStatus
import com.assistant.core.ui.UI
import com.assistant.core.ui.*
import com.assistant.core.ui.sound.scrollEndSound
import com.assistant.core.themes.CurrentTheme
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import com.assistant.core.utils.LogManager
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001

        /** The tool a notification opens, on its oldest entry that waits. */
        const val EXTRA_TOOL_INSTANCE_ID = "tool_instance_id"
    }

    /** The tool asked for by the notification that opened the app, until the screens have opened it. */
    private var openToolId by androidx.compose.runtime.mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_TOOL_INSTANCE_ID)?.let { openToolId = it }
    }

    // Said again whenever the window comes back (a dialog closed, the app reopened): the system
    // shows the bar again on its own then
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideNavigationBar(window)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Once only: a rotation recreates the activity with the same intent
        if (savedInstanceState == null) openToolId = intent.getStringExtra(EXTRA_TOOL_INSTANCE_ID)

        // Initialize LogManager first (for DB persistence)
        LogManager.initialize(this)

        // Purge old logs at startup to prevent DB bloat and CursorWindow overflow
        CoroutineScope(Dispatchers.IO).launch {
            try {
                LogManager.manualPurge()
            } catch (e: Exception) {
                println("LogManager: Startup purge failed: ${e.message}")
            }
        }

        // Initialize app config cache
        AppConfigManager.initialize(this)

        // Initialize notification channels (Android O+)
        NotificationChannels.initialize(this)

        // Request notification permission (Android 13+)
        requestNotificationPermissionIfNeeded()

        // Request battery optimization exemption for background scheduling
        requestBatteryOptimizationExemptionIfNeeded()

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

        // The phone's dark theme setting from the first frame on
        CurrentTheme.systemDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

        setContent {
            // The phone's dark theme setting, which the mode "as the phone" follows as it changes
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            androidx.compose.runtime.SideEffect { CurrentTheme.systemDark = systemDark }
            // The app's screens, moved whole from one theme's frame to another's when the theme
            // changes: each theme draws FullScreen its own way, and without moving them every
            // screen's state would start over, the interface settings being edited among them
            val app = androidx.compose.runtime.remember { androidx.compose.runtime.movableContentOf {
                // The demo first, installed afresh after an update, before the home screen
                // reads the zones; once per activity, a recreation finding it done
                var demoReady by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
                if (!demoReady) {
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        val failed = withContext(Dispatchers.IO) { com.assistant.core.demo.DemoStartup.run(this@MainActivity) }
                        if (failed != null) com.assistant.core.ui.UI.Toast(this@MainActivity, failed, com.assistant.core.ui.Duration.LONG)
                        demoReady = true
                    }
                    com.assistant.core.ui.components.DemoInstalling()
                } else {
                    // What waits for the user and the stopwatches running, marked on every tile of
                    // every screen: watched once the demo is written, not at each of its writes
                    androidx.compose.runtime.CompositionLocalProvider(
                        com.assistant.core.ui.LocalWaiting provides com.assistant.core.ui.rememberWaiting(null),
                        com.assistant.core.ui.LocalRunning provides com.assistant.core.ui.rememberRunning()
                    ) {
                        // The theme's sounds loaded
                        com.assistant.core.ui.sound.UISoundsLoader()
                        // A long operation running shows over every screen; one whose caller
                        // left (its screen closed) says once here how it ended
                        androidx.compose.runtime.LaunchedEffect(Unit) {
                            com.assistant.core.coordinator.LongOperation.unclaimed.collect { ended ->
                                val s = com.assistant.core.strings.Strings.`for`(context = this@MainActivity)
                                val message = if (ended.result.isSuccess) s.shared("long_operation_done").format(ended.label)
                                    else s.shared("long_operation_failed").format(ended.label, ended.result.error ?: "")
                                com.assistant.core.ui.UI.Toast(this@MainActivity, message, com.assistant.core.ui.Duration.LONG)
                            }
                        }
                        // The end of any list of the screens heard
                        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.fillMaxSize().scrollEndSound()) {
                            com.assistant.core.ui.components.LongOperationBar()
                            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.weight(1f)) {
                                MainScreen(openToolId = openToolId, onToolOpened = { openToolId = null })
                            }
                        }
                    }
                }
            } }
            MaterialTheme(
                colorScheme = CurrentTheme.getCurrentColorScheme()
            ) {
                UI.FullScreen { app() }
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

    /**
     * Request battery optimization exemption to allow background scheduling
     *
     * Without this exemption, Android's Doze mode will suspend the CoreScheduler's
     * 1-minute heartbeat coroutine when the app is in the background, preventing
     * scheduled messages (and other scheduled tasks) from executing.
     *
     * This opens the system settings dialog for the user to grant the exemption.
     * The exemption is critical for reliable background notifications.
     */
    private fun requestBatteryOptimizationExemptionIfNeeded() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            val packageName = packageName

            // Check if already ignoring battery optimizations
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    LogManager.service("Battery optimization is enabled - requesting exemption for background scheduling", "INFO")

                    // Create intent to request exemption
                    val intent = Intent().apply {
                        action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                        data = Uri.parse("package:$packageName")
                    }

                    // Launch settings dialog
                    startActivity(intent)
                } else {
                    LogManager.service("Battery optimization exemption already granted", "DEBUG")
                }
            }
        } catch (e: Exception) {
            LogManager.service("Failed to request battery optimization exemption: ${e.message}", "WARN", e)
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
}