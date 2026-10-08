package app.treelune.core.mcp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import app.treelune.MainActivity
import app.treelune.core.icons.Icons
import app.treelune.core.strings.Strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The external access open (McpAccess): a foreground service, so Android does not stop the loop
 * while the app is out of sight, and so its notification always says the door is open. The
 * notification closes it; so does half an hour without a call.
 */
class McpAccessService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CLOSE) {
            loop?.cancel()
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = notification(null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (loop?.isActive != true) {
            loop = scope.launch {
                try {
                    McpAccess.run(applicationContext) { at ->
                        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(at))
                    }
                } finally {
                    stopSelf()
                }
            }
        }
        // Stopped by Android, it is not opened again by itself: the access is opened by hand
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** The notification of the access open, with the last call that did something when there was one. */
    private fun notification(lastCallAt: Long?): Notification {
        val s = Strings.`for`(context = this)
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, s.shared("external_access_channel"), NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val close = PendingIntent.getService(this, 1, Intent(this, McpAccessService::class.java).setAction(ACTION_CLOSE), PendingIntent.FLAG_IMMUTABLE)
        val text = lastCallAt?.let { s.shared("external_access_notification_last").format(app.treelune.core.utils.DateUtils.formatTimeForDisplay(it)) }
            ?: s.shared("external_access_notification_text")
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(Icons.lucideDrawable(this, ICON) ?: android.R.drawable.stat_sys_upload)
            .setContentTitle(s.shared("external_access_notification_title"))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, s.shared("external_access_close"), close)
            .build()
    }

    companion object {
        const val ACTION_CLOSE = "app.treelune.mcp.CLOSE"
        const val CHANNEL = "treelune_external_access"
        const val ICON = "radio_tower"
        private const val NOTIFICATION_ID = 0x4d41 // "MA"
    }
}
