package com.assistant.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.assistant.core.strings.Strings
import com.assistant.core.utils.LogManager

/**
 * Centralized registry for all notification channels.
 *
 * Defines the 3 priority-based channels used across the app:
 * - assistant_high: Popup heads-up notifications (interruption)
 * - assistant_default: Badge + sound notifications (standard)
 * - assistant_low: Silent notifications (no sound/vibration)
 *
 * Architecture:
 * - Single source of truth for channel metadata
 * - Created once at app startup (MainActivity)
 * - Reusable by all tools (Messages, future Alerts, etc.)
 * - User can override importance in Android settings
 *
 * Channel lifecycle:
 * - initialize() called from MainActivity.onCreate()
 * - Channels created for all Android versions >= O (API 26)
 * - Once created, channel importance is controlled by user
 * - App can only suggest default importance at creation
 */
object NotificationChannels {

    // Channel IDs (public for NotificationService routing)
    const val CHANNEL_HIGH = "assistant_high"
    const val CHANNEL_DEFAULT = "assistant_default"
    const val CHANNEL_LOW = "assistant_low"

    /**
     * Initialize notification channels.
     * Must be called at app startup before any notifications are sent.
     *
     * Android O+ requirement: Channels must exist before sending notifications.
     * Creating channels multiple times is idempotent (safe to call repeatedly).
     *
     * @param context Application context
     */
    fun initialize(context: Context) {
        // Channels only needed for Android O (API 26) and above
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            LogManager.service("NotificationChannels: Skipping initialization (API < 26)", "DEBUG")
            return
        }

        LogManager.service("NotificationChannels: Initializing channels", "INFO")

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val s = Strings.`for`(context = context)

        try {
            // Channel 1: HIGH priority (popup heads-up)
            val highChannel = NotificationChannel(
                CHANNEL_HIGH,
                s.shared("notification_channel_high_name"),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = s.shared("notification_channel_high_description")
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(highChannel)
            LogManager.service("NotificationChannels: Created channel '$CHANNEL_HIGH'", "DEBUG")

            // Channel 2: DEFAULT priority (badge + sound)
            val defaultChannel = NotificationChannel(
                CHANNEL_DEFAULT,
                s.shared("notification_channel_default_name"),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = s.shared("notification_channel_default_description")
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(defaultChannel)
            LogManager.service("NotificationChannels: Created channel '$CHANNEL_DEFAULT'", "DEBUG")

            // Channel 3: LOW priority (silent)
            val lowChannel = NotificationChannel(
                CHANNEL_LOW,
                s.shared("notification_channel_low_name"),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = s.shared("notification_channel_low_description")
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(lowChannel)
            LogManager.service("NotificationChannels: Created channel '$CHANNEL_LOW'", "DEBUG")

            LogManager.service("NotificationChannels: All channels initialized successfully", "INFO")

        } catch (e: Exception) {
            LogManager.service("NotificationChannels: Failed to initialize: ${e.message}", "ERROR", e)
        }
    }

    /**
     * Get channel ID for a given priority string.
     * Used by NotificationService to route notifications.
     *
     * @param priority Priority string: "high", "default", "low"
     * @return Channel ID corresponding to priority
     */
    fun getChannelIdForPriority(priority: String): String {
        return when (priority.lowercase()) {
            "high" -> CHANNEL_HIGH
            "low" -> CHANNEL_LOW
            else -> CHANNEL_DEFAULT
        }
    }
}
