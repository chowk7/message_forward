package com.smsforwarder.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.smsforwarder.R
import com.smsforwarder.SmsForwarderApp
import com.smsforwarder.data.model.FilterRule
import com.smsforwarder.data.model.FilterType
import com.smsforwarder.data.model.ForwardLog
import kotlinx.coroutines.*

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        Log.d(TAG, "SMS received broadcast triggered")

        // Check if forwarding is enabled
        val prefs = context.getSharedPreferences("sms_forwarder_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("forwarding_enabled", false)) {
            Log.d(TAG, "Forwarding is disabled, skipping")
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            Log.d(TAG, "No messages in intent")
            return
        }

        // Group message parts by sender
        val grouped = mutableMapOf<String, StringBuilder>()
        for (msg in messages) {
            val sender = msg.originatingAddress ?: continue
            grouped.getOrPut(sender) { StringBuilder() }.append(msg.messageBody)
        }

        Log.d(TAG, "Received ${grouped.size} message(s)")

        // Use goAsync() to extend broadcast processing time
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as SmsForwarderApp
                val repository = app.repository

                val rules = repository.getEnabledRules()
                val forwardNumbers = repository.getEnabledNumbers()

                Log.d(TAG, "Rules: ${rules.size}, Forward numbers: ${forwardNumbers.size}")

                if (rules.isEmpty() || forwardNumbers.isEmpty()) {
                    Log.d(TAG, "No rules or forward numbers configured")
                    return@launch
                }

                for ((sender, body) in grouped) {
                    val bodyStr = body.toString()
                    val matchedRule = findMatchingRule(rules, sender, bodyStr)

                    if (matchedRule == null) {
                        Log.d(TAG, "No matching rule for sender=$sender, body=$bodyStr")
                        continue
                    }

                    Log.d(TAG, "Matched rule: ${describeRule(matchedRule)}")

                    val forwardBody = "[포워딩] $bodyStr"

                    for (number in forwardNumbers) {
                        try {
                            sendSms(context, number.phoneNumber, forwardBody)
                            repository.insertLog(
                                ForwardLog(
                                    originalSender = sender,
                                    messageBody = bodyStr,
                                    forwardedTo = number.phoneNumber,
                                    matchedRule = describeRule(matchedRule),
                                    isSuccess = true
                                )
                            )
                            showNotification(context, sender, number.phoneNumber)
                            Log.d(TAG, "Forwarded to ${number.phoneNumber}")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to forward to ${number.phoneNumber}", e)
                            repository.insertLog(
                                ForwardLog(
                                    originalSender = sender,
                                    messageBody = bodyStr,
                                    forwardedTo = number.phoneNumber,
                                    matchedRule = describeRule(matchedRule),
                                    isSuccess = false,
                                    errorMessage = e.message
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun findMatchingRule(rules: List<FilterRule>, sender: String, body: String): FilterRule? {
        return rules.firstOrNull { rule ->
            when (rule.type) {
                FilterType.TEXT_KEYWORD -> body.contains(rule.value, ignoreCase = true)
                FilterType.SENDER_NUMBER -> {
                    val normalizedSender = sender.replace("-", "").replace(" ", "").replace("+82", "0")
                    val normalizedRule = rule.value.replace("-", "").replace(" ", "").replace("+82", "0")
                    normalizedSender.endsWith(normalizedRule) || normalizedRule.endsWith(normalizedSender)
                }
            }
        }
    }

    private fun describeRule(rule: FilterRule): String {
        return when (rule.type) {
            FilterType.TEXT_KEYWORD -> "키워드: ${rule.value}"
            FilterType.SENDER_NUMBER -> "발신번호: ${rule.value}"
        }
    }

    private fun sendSms(context: Context, destination: String, message: String) {
        val smsManager = context.getSystemService(SmsManager::class.java)
        val parts = smsManager.divideMessage(message)
        if (parts.size == 1) {
            smsManager.sendTextMessage(destination, null, message, null, null)
        } else {
            smsManager.sendMultipartTextMessage(destination, null, parts, null, null)
        }
    }

    private fun showNotification(context: Context, sender: String, forwardedTo: String) {
        val notification = NotificationCompat.Builder(context, SmsForwarderApp.LOG_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_forward)
            .setContentTitle("SMS 포워딩 완료")
            .setContentText("$sender → $forwardedTo")
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
