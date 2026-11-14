package ca.bancroft.trails.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import ca.bancroft.trails.MainActivity
import ca.bancroft.trails.R
import ca.bancroft.trails.data.RideRecorder
import ca.bancroft.trails.nav.formatDist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps GPS running with the screen off while recording a ride or navigating (Android only allows
 * background location for a visible foreground service). Shows a small ongoing notification.
 */
class RideService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var nm: NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Riding", NotificationManager.IMPORTANCE_LOW))
        startForeground(ID, notification("Bancroft Trails is using GPS"), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        val repo = LocationRepo.get(this)
        repo.start(fastUpdates = true)
        scope.launch {
            repo.location.collect { loc ->
                if (loc == null) return@collect
                RideRecorder.add(loc)
                RideRecorder.live.value?.let { nm.notify(ID, notification("Recording ride · ${formatDist(it.distanceM)}")) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        intent?.getStringExtra(EXTRA_TEXT)?.let { nm.notify(ID, notification(it)) }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_trail).setContentTitle("Bancroft Trails").setContentText(text)
            .setOngoing(true).setContentIntent(open).setCategory(Notification.CATEGORY_NAVIGATION).build()
    }

    companion object {
        private const val CHANNEL = "riding"
        private const val ID = 7
        private const val ACTION_STOP = "stop"
        private const val EXTRA_TEXT = "text"

        fun start(ctx: Context, text: String) {
            runCatching { ctx.startForegroundService(Intent(ctx, RideService::class.java).putExtra(EXTRA_TEXT, text)) }
        }

        fun stop(ctx: Context) {
            runCatching { ctx.startService(Intent(ctx, RideService::class.java).setAction(ACTION_STOP)) }
        }
    }
}
