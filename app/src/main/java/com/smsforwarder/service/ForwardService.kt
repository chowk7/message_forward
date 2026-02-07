package com.smsforwarder.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import com.smsforwarder.R
import com.smsforwarder.SmsForwarderApp
import com.smsforwarder.data.model.FilterRule
import com.smsforwarder.data.model.FilterType
import com.smsforwarder.data.model.ForwardLog
import kotlinx.coroutines.*

class ForwardService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sender = intent?.getStringExtra(EXTRA_SENDER) ?: return START_NOT_STICKY
        val body = intent.getStringExtra(EXTRA_BODY) ?: return START_NOT_STICKY

        serviceScope.launch {
            processMessage(sender, body)
            stopSelf(startId)
        }

        return START_NOT_STICKY
    }

    private suspend fun processMessage(sender: String, body: String) {
        val app = application as SmsForwarderApp
        val repository = app.repository

        // Check if forwarding is enabled
        val prefs = getSharedPreferences("sms_forwarder_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("forwarding_enabled", false)) return

        val rules = repository.getEnabledRules()
        val forwardNumbers = repository.getEnabledNumbers()

        if (rules.isEmpty() || forwardNumbers.isEmpty()) return

        val matchedRule = findMatchingRule(rules, sender, body) ?: return

        val forwardBody = "[포워딩] $body"

        for (number in forwardNumbers) {
            try {
                sendSms(number.phoneNumber, forwardBody)
                repository.insertLog(
                    ForwardLog(
                        originalSender = sender,
                        messageBody = body,
                        forwardedTo = number.phoneNumber,
                        matchedRule = describeRule(matchedRule),
                        isSuccess = true
                    )
                )
                showForwardNotification(sender, number.phoneNumber)
            } catch (e: Exception) {
                repository.insertLog(
                    ForwardLog(
                        originalSender = sender,
                        messageBody = body,
                        forwardedTo = number.phoneNumber,
                        matchedRule = describeRule(matchedRule),
                        isSuccess = false,
                        errorMessage = e.message
                    )
                )
            }
        }
    }

    private fun findMatchingRule(rules: List<FilterRule>, sender: String, body: String): FilterRule? {
        return rules.firstOrNull { rule ->
            when (rule.type) {
                FilterType.TEXT_KEYWORD -> body.contains(rule.value, ignoreCase = true)
                FilterType.SENDER_NUMBER -> {
                    val normalizedSender = sender.replace("-", "").replace(" ", "")
                    val normalizedRule = rule.value.replace("-", "").replace(" ", "")
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

    private fun sendSms(destination: String, message: String) {
        val smsManager = getSystemService(SmsManager::class.java)
        val parts = smsManager.divideMessage(message)
        if (parts.size == 1) {
            smsManager.sendTextMessage(destination, null, message, null, null)
        } else {
            smsManager.sendMultipartTextMessage(destination, null, parts, null, null)
        }
    }

    private fun showForwardNotification(sender: String, forwardedTo: String) {
        val notification = NotificationCompat.Builder(this, SmsForwarderApp.LOG_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_forward)
            .setContentTitle("SMS 포워딩 완료")
            .setContentText("$sender → $forwardedTo")
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, SmsForwarderApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_forward)
            .setContentTitle("SMS 포워딩")
            .setContentText("SMS 포워딩 서비스 실행 중")
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val EXTRA_SENDER = "extra_sender"
        const val EXTRA_BODY = "extra_body"
        private const val NOTIFICATION_ID = 1001
    }
}
