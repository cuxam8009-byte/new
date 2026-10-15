package com.mcbot.manager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Foreground service: giữ process (và Node + các bot) sống khi tắt màn hình / thoát giao diện. */
class NodeService : Service() {
    private var wake: PowerManager.WakeLock? = null
    private var poll: Thread? = null

    @Volatile
    private var running = true

    companion object {
        const val CH = "mcbot_run"
        const val NOTI_ID = 1
        const val ACTION_EXIT = "com.mcbot.manager.EXIT"

        fun start(ctx: Context) {
            val i = Intent(ctx, NodeService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
            } catch (_: Exception) {
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_EXIT) {
            NodeRuntime.exitAll(this)
            return START_NOT_STICKY
        }
        goForeground("Bot đang chạy nền")
        if (wake == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mcbot:node").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        NodeRuntime.start(applicationContext)
        if (poll == null) startPoll()
        return START_STICKY
    }

    private fun goForeground(text: String) {
        val n = buildNotification(text)
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTI_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTI_ID, n)
            }
        } catch (_: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(text: String): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val b: Notification.Builder
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CH, "Bot chạy nền", NotificationManager.IMPORTANCE_LOW))
            b = Notification.Builder(this, CH)
        } else {
            b = Notification.Builder(this)
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val exit = PendingIntent.getService(
            this, 1, Intent(this, NodeService::class.java).setAction(ACTION_EXIT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return b.setSmallIcon(R.drawable.ic_stat_bot)
            .setContentTitle("MC Bot Manager")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Thoát", exit)
            .build()
    }

    private fun updateText(text: String) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTI_ID, buildNotification(text))
        } catch (_: Exception) {
        }
    }

    /** Cứ 15 giây hỏi Node xem có bao nhiêu bot online để cập nhật thông báo. */
    private fun startPoll() {
        val t = Thread {
            while (running) {
                try {
                    Thread.sleep(15000)
                } catch (_: InterruptedException) {
                    break
                }
                if (NodeRuntime.port == 0 || NodeRuntime.token.isEmpty()) continue
                try {
                    val c = URL("http://127.0.0.1:${NodeRuntime.port}/api/summary?token=${NodeRuntime.token}")
                        .openConnection() as HttpURLConnection
                    c.connectTimeout = 1500
                    c.readTimeout = 1500
                    val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                    updateText("${j.getInt("online")}/${j.getInt("total")} bot online")
                } catch (_: Exception) {
                }
            }
        }
        t.isDaemon = true
        poll = t
        t.start()
    }

    override fun onDestroy() {
        running = false
        poll?.interrupt()
        try {
            wake?.release()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }
}
