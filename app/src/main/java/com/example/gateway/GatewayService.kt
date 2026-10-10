package com.example.gateway

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.engine.WalletMatchingEngine
import com.example.data.local.AppDatabase
import com.example.util.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class GatewayService : Service() {

    @Volatile
    private var server: GatewayServer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var startupJob: Job? = null

    companion object {
        const val CHANNEL_ID = "gateway_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START_GATEWAY"
        const val ACTION_STOP = "ACTION_STOP_GATEWAY"
        const val ACTION_RESTART = "ACTION_RESTART_GATEWAY"

        val isRunning = MutableStateFlow(false)
        val activePort = MutableStateFlow(8080)
        val activeIp = MutableStateFlow("127.0.0.1")
        val statusMessage = MutableStateFlow("")

        fun startGateway(context: Context) {
            val intent = Intent(context, GatewayService::class.java).apply { action = ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stopGateway(context: Context) {
            val intent = Intent(context, GatewayService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }

        fun restartGateway(context: Context) {
            val intent = Intent(context, GatewayService::class.java).apply { action = ACTION_RESTART }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                startupJob?.cancel()
                server?.stop()
                server = null
                isRunning.value = false
                statusMessage.value = "تم إيقاف خادم بوابة الرسائل"
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RESTART -> {
                startupJob?.cancel()
                server?.stop()
                server = null
                isRunning.value = false
                startServer()
            }
            else -> startServer()
        }
        return START_STICKY
    }

    private fun startServer() {
        if (server != null) {
            isRunning.value = true
            return
        }

        // Enter foreground immediately; loading Room settings happens asynchronously.
        startForeground(
            NOTIFICATION_ID,
            buildNotification("بوابة الحوالات", "جارٍ تجهيز الخادم المحلي...")
        )
        startupJob?.cancel()
        startupJob = serviceScope.launch {
            try {
                val db = AppDatabase.getInstance(applicationContext)
                val configuredPort = db.settingsDao().getSetting("gateway_port")
                    ?.toIntOrNull()
                    ?.takeIf { it in 1..65535 } ?: 8080
                val candidate = GatewayServer(db, WalletMatchingEngine(db), port = configuredPort)
                if (candidate.start()) {
                    server = candidate
                    activePort.value = configuredPort
                    activeIp.value = NetworkUtils.getLocalIpAddress()
                    isRunning.value = true
                    statusMessage.value = "الخادم يعمل على ${activeIp.value}:${activePort.value}"
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(
                        NOTIFICATION_ID,
                        buildNotification("بوابة الحوالات نشطة", "الخادم يستمع على http://${activeIp.value}:${activePort.value}")
                    )
                } else {
                    isRunning.value = false
                    statusMessage.value = "تعذر تشغيل الخادم على المنفذ $configuredPort"
                    stopSelf()
                }
            } catch (error: Exception) {
                isRunning.value = false
                statusMessage.value = "فشل تشغيل بوابة الحوالات: ${error.localizedMessage ?: error.javaClass.simpleName}"
                stopSelf()
            }
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "POWER FEUL SMS Gateway",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "خدمة تشغيل خادم الحوالات عبر الشبكة المحلية"
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        startupJob?.cancel()
        server?.stop()
        server = null
        isRunning.value = false
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
