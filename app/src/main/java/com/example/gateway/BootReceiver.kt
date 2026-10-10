package com.example.gateway

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        // Android 15+ restricts launching dataSync foreground services from BOOT_COMPLETED.
        // Do not repeatedly crash/restart; the owner can start the local server from the app.
        if (Build.VERSION.SDK_INT >= 35) {
            GatewayService.statusMessage.value =
                "بعد إعادة التشغيل، افتح POS وشغّل بوابة الرسائل يدويًا بسبب قيود Android 15+."
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context.applicationContext)
                val enabled = db.settingsDao().getSetting("auto_start_gateway")
                    ?.toBooleanStrictOrNull() ?: true
                if (enabled) GatewayService.startGateway(context.applicationContext)
            } catch (error: Exception) {
                GatewayService.statusMessage.value =
                    "تعذر الاسترداد التلقائي لبوابة الرسائل: ${error.localizedMessage ?: error.javaClass.simpleName}"
            } finally {
                pendingResult.finish()
            }
        }
    }
}
