package com.qbili.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.qbili.MainActivity
import com.qbili.QBiliApp
import com.qbili.domain.download.DownloadStatus
import kotlinx.coroutines.*

class OfflineDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val downloads get() = (application as QBiliApp).container.offlineDownloads
    private var token = 0L
    private var observing = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "离线缓存", NotificationManager.IMPORTANCE_LOW))
        startForeground(NOTIFICATION, notification("准备下载", 0, 0))
        if (token == 0L) token = downloads.activateService()
        if (intent?.action == PAUSE) {
            scope.launch { withContext(Dispatchers.IO) { downloads.pauseAll() }; stopSelf() }
            return START_NOT_STICKY
        }
        downloads.start(token)
        if (!observing) {
            observing = true
            scope.launch {
                downloads.tasks.collect { tasks ->
                    val pending = tasks.filter { it.status in listOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING) }
                    if (pending.isEmpty()) { stopSelf(); return@collect }
                    val current = pending.firstOrNull { it.status == DownloadStatus.DOWNLOADING } ?: pending.first()
                    manager.notify(NOTIFICATION, notification("${current.title} · ${current.part}", current.bytes, current.total))
                    downloads.start(token)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun notification(title: String, bytes: Long, total: Long): android.app.Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val pause = PendingIntent.getService(this, 1, Intent(this, OfflineDownloadService::class.java).setAction(PAUSE), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("QBili 离线缓存").setContentText(title).setContentIntent(open).setOnlyAlertOnce(true)
            .setOngoing(true).setProgress(100, if (total > 0) (bytes * 100 / total).toInt().coerceIn(0, 100) else 0, total <= 0)
            .addAction(android.R.drawable.ic_media_pause, "暂停全部", pause).build()
    }

    override fun onTimeout(startId: Int, fgsType: Int) { downloads.stopService(token); stopSelf() }
    override fun onTaskRemoved(rootIntent: Intent?) { downloads.stopService(token); stopSelf() }
    override fun onDestroy() { downloads.stopService(token); scope.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "offline_downloads"
        private const val NOTIFICATION = 2801
        private const val PAUSE = "com.qbili.download.PAUSE"
        fun start(context: Context) { ContextCompat.startForegroundService(context, Intent(context, OfflineDownloadService::class.java)) }
        fun networkAvailable(context: Context): Boolean {
            val manager = context.getSystemService(ConnectivityManager::class.java)
            val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
    }
}
