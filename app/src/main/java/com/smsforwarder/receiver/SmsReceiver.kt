package com.smsforwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.smsforwarder.service.ForwardService

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        // Group message parts by sender
        val grouped = mutableMapOf<String, StringBuilder>()
        for (msg in messages) {
            val sender = msg.originatingAddress ?: continue
            grouped.getOrPut(sender) { StringBuilder() }.append(msg.messageBody)
        }

        for ((sender, body) in grouped) {
            val serviceIntent = Intent(context, ForwardService::class.java).apply {
                putExtra(ForwardService.EXTRA_SENDER, sender)
                putExtra(ForwardService.EXTRA_BODY, body.toString())
            }
            context.startForegroundService(serviceIntent)
        }
    }
}
