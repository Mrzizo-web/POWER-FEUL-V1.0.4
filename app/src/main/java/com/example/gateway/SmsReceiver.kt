package com.example.gateway

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.engine.IngestResult
import com.example.data.engine.WalletMatchingEngine
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].displayOriginatingAddress ?: ""
        val bodyBuilder = java.lang.StringBuilder()
        for (msg in messages) {
            bodyBuilder.append(msg.displayMessageBody)
        }
        val fullBody = bodyBuilder.toString()
        val receivedAt = messages[0].timestampMillis

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val engine = WalletMatchingEngine(db)
                val result = engine.ingestSms(sender, fullBody, receivedAt)

                when (result) {
                    is IngestResult.Created -> {
                        val shiftText = if (result.isOutOfShift) "(خارج الدوام)" else "(شفت مفتوح)"
                        showNotification(
                            context = context,
                            title = "حوالة واردة جديدة $shiftText",
                            message = "${result.tx.amount} ريال عبر ${result.tx.walletCode} من ${result.tx.sender}",
                            notifId = result.tx.id.hashCode()
                        )
                    }
                    is IngestResult.Duplicate -> {
                        showNotification(
                            context = context,
                            title = "رسالة حوالة مكررة",
                            message = "${result.reason} (مبلغ: ${result.tx.amount} ريال)",
                            notifId = result.tx.id.hashCode()
                        )
                    }
                    is IngestResult.Unmatched -> {
                        // Unmatched SMS
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(context: Context, title: String, message: String, notifId: Int) {
        val channelId = "wallet_incoming_sms_channel"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "إشعارات الحوالات الواردة",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "إشعارات وصول حوالات جيب وفلوسك وحوالتي"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .build()

        manager.notify(notifId, notification)
    }
}
