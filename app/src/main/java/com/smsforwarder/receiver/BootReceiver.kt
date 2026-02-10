package com.smsforwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.smsforwarder.service.MessageObserverService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("sms_forwarder_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("forwarding_enabled", false)) {
                val serviceIntent = Intent(context, MessageObserverService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}
