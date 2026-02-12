package com.smsforwarder.service

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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

class MessageObserverService : Service() {

    companion object {
        private const val TAG = "MsgObserverService"
        private const val NOTIFICATION_ID = 2001
        private const val PREF_LAST_MMS_ID = "last_mms_id"
        private const val PREF_LAST_CHAT_SMS_ID = "last_chat_sms_id"
        private val MMS_URI = Uri.parse("content://mms/")
        private const val FORWARD_PREFIX_MMS = "[MMS 포워딩]"
        private const val FORWARD_PREFIX_CHAT = "[채팅+ 포워딩]"
        private const val FORWARD_PREFIX_SMS = "[포워딩]"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var mmsObserver: ContentObserver? = null
    private var smsObserver: ContentObserver? = null
    // Track recently processed IDs to prevent duplicate processing
    private val recentlyProcessedChatIds = mutableSetOf<Long>()
    private val recentlyProcessedMmsIds = mutableSetOf<Long>()
    // Flag to suppress observer while we're sending
    @Volatile
    private var isSendingMessage = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        resetLastIds()
        registerObservers()
        Log.d(TAG, "MessageObserverService started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        mmsObserver?.let { contentResolver.unregisterContentObserver(it) }
        smsObserver?.let { contentResolver.unregisterContentObserver(it) }
        serviceScope.cancel()
        Log.d(TAG, "MessageObserverService stopped")
    }

    private fun startForegroundNotification() {
        val prefs = getSharedPreferences("sms_forwarder_prefs", MODE_PRIVATE)
        val chatEnabled = prefs.getBoolean("chat_forwarding_enabled", false)

        val desc = buildString {
            append("MMS 메시지 감시 중")
            if (chatEnabled) append(" / 채팅+ 감시 중")
        }

        val notification = NotificationCompat.Builder(this, SmsForwarderApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_forward)
            .setContentTitle("메시지 포워딩 활성화")
            .setContentText(desc)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    /**
     * Always reset to current latest IDs when service starts.
     * This prevents forwarding old messages that arrived before the service started.
     */
    private fun resetLastIds() {
        val prefs = getSharedPreferences("sms_forwarder_prefs", MODE_PRIVATE)
        prefs.edit()
            .putLong(PREF_LAST_MMS_ID, getLatestMmsId())
            .putLong(PREF_LAST_CHAT_SMS_ID, getLatestSmsId())
            .apply()
        Log.d(TAG, "Reset last IDs: MMS=${prefs.getLong(PREF_LAST_MMS_ID, 0)}, Chat=${prefs.getLong(PREF_LAST_CHAT_SMS_ID, 0)}")
    }

    private fun registerObservers() {
        val handler = Handler(Looper.getMainLooper())

        // MMS observer
        mmsObserver = object : ContentObserver(handler) {
            private var debounceJob: Job? = null
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                if (isSendingMessage) return
                Log.d(TAG, "MMS content changed: $uri")
                debounceJob?.cancel()
                debounceJob = serviceScope.launch {
                    delay(3000) // MMS needs time to fully download
                    processNewMmsMessages()
                }
            }
        }
        contentResolver.registerContentObserver(MMS_URI, true, mmsObserver!!)

        // SMS observer for Chat+/RCS messages
        smsObserver = object : ContentObserver(handler) {
            private var debounceJob: Job? = null
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                if (isSendingMessage) return
                // Check if Chat+ forwarding is enabled
                val prefs = getSharedPreferences("sms_forwarder_prefs", MODE_PRIVATE)
                if (!prefs.getBoolean("chat_forwarding_enabled", false)) return
                Log.d(TAG, "SMS content changed: $uri")
                debounceJob?.cancel()
                debounceJob = serviceScope.launch {
                    delay(1500)
                    processNewChatMessages()
                }
            }
        }
        contentResolver.registerContentObserver(
            Telephony.Sms.CONTENT_URI, true, smsObserver!!
        )
    }

    private fun getLatestMmsId(): Long {
        return try {
            contentResolver.query(
                MMS_URI,
                arrayOf("_id"),
                null, null, "_id DESC LIMIT 1"
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            } ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get latest MMS id", e)
            0L
        }
    }

    private fun getLatestSmsId(): Long {
        return try {
            contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf("_id"),
                null, null, "_id DESC LIMIT 1"
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            } ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get latest SMS id", e)
            0L
        }
    }

    private suspend fun processNewMmsMessages() {
        val prefs = getSharedPreferences("sms_forwarder_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("forwarding_enabled", false)) return

        val lastId = prefs.getLong(PREF_LAST_MMS_ID, 0L)
        Log.d(TAG, "Processing new MMS messages after id=$lastId")

        try {
            val app = applicationContext as SmsForwarderApp
            val repository = app.repository
            val rules = repository.getEnabledRules()
            val forwardNumbers = repository.getEnabledNumbers()

            if (rules.isEmpty() || forwardNumbers.isEmpty()) return

            contentResolver.query(
                MMS_URI,
                arrayOf("_id", "date", "msg_box"),
                "_id > ?",
                arrayOf(lastId.toString()),
                "_id ASC"
            )?.use { cursor ->
                var maxId = lastId
                val idIdx = cursor.getColumnIndex("_id")
                val msgBoxIdx = cursor.getColumnIndex("msg_box")

                while (cursor.moveToNext()) {
                    val mmsId = cursor.getLong(idIdx)
                    if (mmsId > maxId) maxId = mmsId

                    // msg_box: 1=inbox, 2=sent, 3=draft, 4=outbox
                    val msgBox = if (msgBoxIdx >= 0) cursor.getInt(msgBoxIdx) else 1
                    if (msgBox != 1) continue // Only process inbox (received) MMS

                    if (recentlyProcessedMmsIds.contains(mmsId)) continue
                    recentlyProcessedMmsIds.add(mmsId)

                    val sender = getMmsSender(mmsId)
                    val body = getMmsTextContent(mmsId)

                    Log.d(TAG, "New MMS: id=$mmsId, sender=$sender, body=$body")

                    if (sender.isNullOrEmpty() || body.isNullOrEmpty()) continue
                    // Skip if this is a forwarded message (prevent loops)
                    if (body.startsWith(FORWARD_PREFIX_MMS) || body.startsWith(FORWARD_PREFIX_CHAT) || body.startsWith(FORWARD_PREFIX_SMS)) continue

                    val matchedRule = findMatchingRule(rules, sender, body) ?: continue
                    Log.d(TAG, "MMS matched rule: ${describeRule(matchedRule)}")

                    val forwardBody = "$FORWARD_PREFIX_MMS $body"
                    for (number in forwardNumbers) {
                        try {
                            sendSmsWithFlag(number.phoneNumber, forwardBody)
                            repository.insertLog(
                                ForwardLog(
                                    originalSender = sender,
                                    messageBody = body,
                                    forwardedTo = number.phoneNumber,
                                    matchedRule = describeRule(matchedRule),
                                    isSuccess = true
                                )
                            )
                            showNotification(sender, number.phoneNumber, "MMS")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to forward MMS to ${number.phoneNumber}", e)
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
                prefs.edit().putLong(PREF_LAST_MMS_ID, maxId).apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing MMS messages", e)
        }

        // Trim processed IDs set to prevent memory leak
        if (recentlyProcessedMmsIds.size > 200) {
            val sorted = recentlyProcessedMmsIds.sorted()
            recentlyProcessedMmsIds.clear()
            recentlyProcessedMmsIds.addAll(sorted.takeLast(50))
        }
    }

    private suspend fun processNewChatMessages() {
        val prefs = getSharedPreferences("sms_forwarder_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("forwarding_enabled", false)) return
        if (!prefs.getBoolean("chat_forwarding_enabled", false)) return

        val lastId = prefs.getLong(PREF_LAST_CHAT_SMS_ID, 0L)

        try {
            val app = applicationContext as SmsForwarderApp
            val repository = app.repository
            val rules = repository.getEnabledRules()
            val forwardNumbers = repository.getEnabledNumbers()

            if (rules.isEmpty() || forwardNumbers.isEmpty()) return

            // Query for new incoming messages (type=1 is inbox)
            contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf("_id", "address", "body", "type", "protocol", "date"),
                "_id > ? AND type = ?",
                arrayOf(lastId.toString(), "1"), // type 1 = inbox (received)
                "_id ASC"
            )?.use { cursor ->
                var maxId = lastId
                val idIdx = cursor.getColumnIndex("_id")
                val addrIdx = cursor.getColumnIndex("address")
                val bodyIdx = cursor.getColumnIndex("body")
                val protocolIdx = cursor.getColumnIndex("protocol")
                val dateIdx = cursor.getColumnIndex("date")

                while (cursor.moveToNext()) {
                    val msgId = cursor.getLong(idIdx)
                    if (msgId > maxId) maxId = msgId

                    // Skip already processed
                    if (recentlyProcessedChatIds.contains(msgId)) continue
                    recentlyProcessedChatIds.add(msgId)

                    val protocol = cursor.getString(protocolIdx)
                    // Standard SMS has protocol != null (handled by SmsReceiver)
                    // Chat+/RCS messages have protocol = null when inserted by messaging app
                    if (protocol != null) continue

                    // Skip messages older than 30 seconds (safety net for stale messages)
                    val msgDate = cursor.getLong(dateIdx)
                    if (System.currentTimeMillis() - msgDate > 30_000) {
                        Log.d(TAG, "Skipping old Chat+ message id=$msgId (age=${System.currentTimeMillis() - msgDate}ms)")
                        continue
                    }

                    val sender = cursor.getString(addrIdx) ?: continue
                    val body = cursor.getString(bodyIdx) ?: continue

                    // Skip forwarded messages (prevent infinite loop)
                    if (body.startsWith(FORWARD_PREFIX_CHAT) || body.startsWith(FORWARD_PREFIX_MMS) || body.startsWith(FORWARD_PREFIX_SMS)) continue

                    Log.d(TAG, "New Chat+ message: id=$msgId, sender=$sender, body=$body")

                    val matchedRule = findMatchingRule(rules, sender, body) ?: continue
                    Log.d(TAG, "Chat+ matched rule: ${describeRule(matchedRule)}")

                    val forwardBody = "$FORWARD_PREFIX_CHAT $body"
                    for (number in forwardNumbers) {
                        try {
                            sendSmsWithFlag(number.phoneNumber, forwardBody)
                            repository.insertLog(
                                ForwardLog(
                                    originalSender = sender,
                                    messageBody = body,
                                    forwardedTo = number.phoneNumber,
                                    matchedRule = describeRule(matchedRule),
                                    isSuccess = true
                                )
                            )
                            showNotification(sender, number.phoneNumber, "채팅+")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to forward Chat+ to ${number.phoneNumber}", e)
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
                prefs.edit().putLong(PREF_LAST_CHAT_SMS_ID, maxId).apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing Chat+ messages", e)
        }

        // Trim processed IDs set to prevent memory leak
        if (recentlyProcessedChatIds.size > 200) {
            val sorted = recentlyProcessedChatIds.sorted()
            recentlyProcessedChatIds.clear()
            recentlyProcessedChatIds.addAll(sorted.takeLast(50))
        }
    }

    private fun getMmsSender(mmsId: Long): String? {
        return try {
            val addrUri = Uri.parse("content://mms/$mmsId/addr")
            contentResolver.query(
                addrUri,
                arrayOf("address", "type"),
                "type=137", // 137 = FROM (PduHeaders.FROM)
                null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndex("address"))
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get MMS sender for id=$mmsId", e)
            null
        }
    }

    private fun getMmsTextContent(mmsId: Long): String? {
        return try {
            val partUri = Uri.parse("content://mms/$mmsId/part")
            contentResolver.query(
                partUri,
                arrayOf("_id", "ct", "text"),
                "ct='text/plain'",
                null, null
            )?.use { cursor ->
                val sb = StringBuilder()
                while (cursor.moveToNext()) {
                    val text = cursor.getString(cursor.getColumnIndex("text"))
                    if (!text.isNullOrEmpty()) {
                        sb.append(text)
                    }
                }
                sb.toString().ifEmpty { null }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get MMS text for id=$mmsId", e)
            null
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

    /**
     * Send SMS with isSendingMessage flag to suppress ContentObserver
     * during the send, preventing feedback loops.
     */
    private fun sendSmsWithFlag(destination: String, message: String) {
        isSendingMessage = true
        try {
            val smsManager = getSystemService(SmsManager::class.java)
            val parts = smsManager.divideMessage(message)
            if (parts.size == 1) {
                smsManager.sendTextMessage(destination, null, message, null, null)
            } else {
                smsManager.sendMultipartTextMessage(destination, null, parts, null, null)
            }
        } finally {
            // Delay clearing the flag so the observer triggered by send is also suppressed
            Handler(Looper.getMainLooper()).postDelayed({
                isSendingMessage = false
            }, 2000)
        }
    }

    private fun showNotification(sender: String, forwardedTo: String, type: String) {
        val notification = NotificationCompat.Builder(this, SmsForwarderApp.LOG_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_forward)
            .setContentTitle("$type 포워딩 완료")
            .setContentText("$sender → $forwardedTo")
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
