package com.example.tvinstaller.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.tvinstaller.MainActivity
import com.example.tvinstaller.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the process alive (and the Wi-Fi radio serving) while the TV downloads and installs.
 * All real work stays in [PushEngine]; the service only mirrors its state in a notification.
 */
class PushService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val label = intent?.getStringExtra(EXTRA_LABEL).orEmpty()
        val host = intent?.getStringExtra(EXTRA_HOST).orEmpty()

        startForeground(
            NOTIFICATION_ID,
            buildNotification(
                title = "正在推送 $label",
                text = if (host.isBlank()) "准备中…" else "目标电视 $host · 准备中…",
                percent = null,
            ),
        )

        acquireWakeLock()

        watcher?.cancel()
        watcher = scope.launch {
            PushEngine.state.collect { state ->
                if (state.isFinished) {
                    stopMyself()
                } else if (state.isRunning) {
                    notify(buildNotification(state))
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        watcher?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun stopMyself() {
        releaseWakeLock()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun notify(notification: Notification) {
        runCatching {
            val manager = getSystemService(NotificationManager::class.java)
            manager?.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(state: PushState): Notification {
        val title = when (state.phase) {
            PushPhase.SUCCESS -> "安装完成"
            PushPhase.FAILED -> "推送失败"
            else -> "正在推送 ${state.apk?.label ?: "APK"}"
        }
        val text = state.message.ifBlank { "正在连接 ${state.tvHost}…" }
        return buildNotification(title, text, state.percent)
    }

    private fun buildNotification(title: String, text: String, percent: Int?): Notification {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
        if (percent != null && percent in 0..100) {
            builder.setProgress(100, percent, false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "APK 推送进度"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val power = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tv:push").apply {
            setReferenceCounted(false)
            runCatching { acquire(10 * 60 * 1000L) }
        }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    companion object {
        private const val CHANNEL_ID = "push"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_HOST = "host"

        fun start(context: Context, apkLabel: String, host: String) {
            val intent = Intent(context, PushService::class.java)
                .putExtra(EXTRA_LABEL, apkLabel)
                .putExtra(EXTRA_HOST, host)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, PushService::class.java)) }
        }
    }
}
