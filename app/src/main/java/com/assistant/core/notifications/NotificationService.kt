package com.assistant.core.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.assistant.MainActivity
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.services.ExecutableService
import com.assistant.core.services.OperationResult
import com.assistant.core.strings.Strings
import com.assistant.core.utils.LogManager
import org.json.JSONObject

/**
 * Core notification service for sending Android notifications.
 *
 * Architecture:
 * - ExecutableService without provider pattern (single Android implementation)
 * - Reusable across all tools (Messages, future Alerts, system notifications)
 * - Routes to appropriate channel based on priority parameter
 *
 * Channel mapping:
 * - "high" → assistant_high (popup heads-up)
 * - "default" → assistant_default (badge + sound)
 * - "low" → assistant_low (silent)
 *
 * Prerequisites:
 * - NotificationChannels.initialize() must be called at app startup
 * - Channels created before sending first notification
 *
 * Operation: send
 * - Params: title (required), content (optional), priority (required)
 * - Returns: success or error
 * - Best effort: No retry mechanism (Android notification delivery is fire-and-forget)
 *
 * @param context Application context
 */
class NotificationService(private val context: Context) : ExecutableService {

    private val notificationManager = NotificationManagerCompat.from(context)

    override suspend fun execute(
        operation: String,
        params: JSONObject,
        token: CancellationToken
    ): OperationResult {
        return when (operation) {
            "send" -> send(params)
            else -> {
                val error = "Unknown operation: $operation"
                LogManager.service("NotificationService: $error", "ERROR")
                OperationResult.error(error)
            }
        }
    }

    /**
     * Send a notification.
     *
     * Required params:
     * - title: String (notification title)
     * - priority: String ("high"|"default"|"low")
     *
     * Optional params:
     * - content: String? (notification body)
     * - notification_id: Int? (for updating/replacing notifications, auto-generated if null)
     *
     * Returns:
     * - success: true with notification_id
     * - error: false with error message
     */
    private suspend fun send(params: JSONObject): OperationResult {
        val s = Strings.`for`(context = context)

        try {
            // Extract and validate params
            val title = params.optString("title", null)
            if (title == null) {
                val error = s.shared("notification_error_missing_title")
                LogManager.service("NotificationService.send: $error", "ERROR")
                return OperationResult.error(error)
            }

            val priority = params.optString("priority", null)
            if (priority == null) {
                val error = s.shared("notification_error_missing_priority")
                LogManager.service("NotificationService.send: $error", "ERROR")
                return OperationResult.error(error)
            }

            val content = params.optString("content", null)
            val notificationId = if (params.has("notification_id")) params.getInt("notification_id") else generateNotificationId()

            // Get channel ID for priority
            val channelId = NotificationChannels.getChannelIdForPriority(priority)

            LogManager.service(
                "NotificationService.send: title='$title', priority='$priority', channel='$channelId', id=$notificationId",
                "INFO"
            )

            // Create notification
            val notification = buildNotification(
                channelId = channelId,
                title = title,
                content = content
            )

            // Send notification
            notificationManager.notify(notificationId, notification.build())

            LogManager.service("NotificationService.send: Notification sent successfully (id=$notificationId)", "DEBUG")

            return OperationResult.success(
                mapOf("notification_id" to notificationId)
            )

        } catch (e: Exception) {
            val error = "${s.shared("notification_error_send_failed")}: ${e.message}"
            LogManager.service("NotificationService.send: $error", "ERROR", e)
            return OperationResult.error(error)
        }
    }

    /**
     * Build notification with standard configuration.
     *
     * @param channelId Notification channel ID
     * @param title Notification title
     * @param content Notification body (optional)
     * @return NotificationCompat.Builder configured notification
     */
    private fun buildNotification(
        channelId: String,
        title: String,
        content: String?
    ): NotificationCompat.Builder {
        // Create intent to open app when notification clicked
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Build notification
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Default Android icon (TODO: Use custom app icon)
            .setContentTitle(title)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true) // Dismiss when clicked
            .setPriority(mapPriorityToCompat(channelId)) // For API < 26 compatibility

        // Add content if provided
        if (!content.isNullOrEmpty()) {
            builder.setContentText(content)
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(content))
        }

        return builder
    }

    /**
     * Map channel ID to NotificationCompat priority for API < 26 compatibility.
     *
     * @param channelId Channel ID
     * @return NotificationCompat priority constant
     */
    private fun mapPriorityToCompat(channelId: String): Int {
        return when (channelId) {
            NotificationChannels.CHANNEL_HIGH -> NotificationCompat.PRIORITY_HIGH
            NotificationChannels.CHANNEL_LOW -> NotificationCompat.PRIORITY_LOW
            else -> NotificationCompat.PRIORITY_DEFAULT
        }
    }

    /**
     * Generate unique notification ID.
     * Uses current timestamp to ensure uniqueness.
     *
     * @return Unique notification ID
     */
    private fun generateNotificationId(): Int {
        return (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
    }

    /**
     * Verbalize notification action (for validation/feedback).
     *
     * @param operation Operation name
     * @param params Operation parameters
     * @param context Android context
     * @return Human-readable description
     */
    override fun verbalize(
        operation: String,
        params: JSONObject,
        context: Context
    ): String {
        val s = Strings.`for`(context = context)
        return when (operation) {
            "send" -> {
                val title = params.optString("title", s.shared("content_unnamed"))
                val priority = params.optString("priority", "default")
                "Envoi notification \"$title\" (priorité: $priority)"
            }
            else -> "Opération notification: $operation"
        }
    }
}
