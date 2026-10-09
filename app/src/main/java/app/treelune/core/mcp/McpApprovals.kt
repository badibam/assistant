package app.treelune.core.mcp

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.treelune.core.icons.Icons
import app.treelune.core.notifications.NotificationChannels
import app.treelune.core.notifications.NotificationService
import app.treelune.core.strings.Strings
import app.treelune.core.utils.LogManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * The approval an AI outside the app waits for before a protected write (docs/design/validation.md):
 * a notification saying what it wants to do and why it is asked, with Allow and Refuse, which the
 * user touches without leaving the app they talk to the AI in. The call waits for the answer,
 * within [TIMEOUT_MS], under the relay's hosting cut of 120 s.
 */
object McpApprovals {

    const val TIMEOUT_MS = 90_000L

    /** How a request ended. */
    enum class Answer { ALLOWED, REFUSED, NO_ANSWER }

    private val waiting = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    /**
     * Asks the user whether [client] may run what [lines] say, one line per action with its
     * reason, and waits for the answer.
     */
    suspend fun ask(context: Context, client: String, lines: List<String>): Answer {
        val id = UUID.randomUUID().toString()
        val answer = CompletableDeferred<Boolean>()
        waiting[id] = answer
        val notificationId = id.hashCode()
        val manager = context.getSystemService(NotificationManager::class.java)
        try {
            manager.notify(notificationId, notification(context, id, notificationId, client, lines))
            return when (withTimeoutOrNull(TIMEOUT_MS) { answer.await() }) {
                true -> Answer.ALLOWED
                false -> Answer.REFUSED
                null -> Answer.NO_ANSWER
            }
        } finally {
            waiting.remove(id)
            manager.cancel(notificationId)
        }
    }

    /** The user's answer to request [id], from its notification's buttons. */
    fun answer(id: String, allowed: Boolean) {
        waiting[id]?.complete(allowed) ?: LogManager.service("MCP approval $id answered after it ended", "INFO")
    }

    private fun notification(context: Context, id: String, notificationId: Int, client: String, lines: List<String>): android.app.Notification {
        val s = Strings.`for`(context = context)
        fun button(allowed: Boolean): PendingIntent = PendingIntent.getBroadcast(
            context, notificationId + if (allowed) 1 else 2,
            Intent(context, McpApprovalReceiver::class.java).putExtra(EXTRA_ID, id).putExtra(EXTRA_ALLOWED, allowed),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = lines.joinToString("\n")
        return NotificationCompat.Builder(context, NotificationChannels.CHANNEL_HIGH)
            .setSmallIcon(Icons.lucideDrawable(context, NotificationService.APP_ICON) ?: android.R.drawable.ic_dialog_info)
            .setContentTitle(s.shared("external_access_approval_title").format(client))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .addAction(0, s.shared("external_access_approval_refuse"), button(false))
            .addAction(0, s.shared("external_access_approval_allow"), button(true))
            .build()
    }

    internal const val EXTRA_ID = "approval_id"
    internal const val EXTRA_ALLOWED = "approval_allowed"
}

/** A button of an approval's notification (McpApprovals). */
class McpApprovalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(McpApprovals.EXTRA_ID) ?: return
        McpApprovals.answer(id, intent.getBooleanExtra(McpApprovals.EXTRA_ALLOWED, false))
    }
}
