package com.smsforwarder.service

import android.app.NotificationManager
import android.app.Service
import android.content.ContentResolver
import android.content.Context
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
        private val MMS_SMS_URI = Uri.parse("content://mms-sms/")
        private val MMS_URI = Uri.parse("content://mms/")
        private val MMS_PART_URI = Uri.parse("content://mms/part")
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var mmsObserver: ContentObserver? = null
    private var smsObserver: ContentObserver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        initLastIds()
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
        val notification = NotificationCompat.Builder(this, SmsForwarderApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_forward)
            .setContentTitle("메시지 포워딩 활성화")
            .setContentText("MMS/채팅+ 메시지를 감시 중입니다")
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun initLastIds() {
        val prefs = getSharedPreferences("sms_forwarder_prefs", MODE_PRIVATE)
        if (prefs.getLong(PREF_LAST_MMS_ID, -1L) == -1L) {
            prefs.edit().putLong(PREF_LAST_MMS_ID, getLatestMmsId()).apply()
        }
        if (prefs.getLong(PREF_LAST_CHAT_SMS_ID, -1L) == -1L) {
            prefs.edit().putLong(PREF_LAST_CHAT_SMS_ID, getLatestSmsId()).apply()
        }
    }

    private fun registerObservers() {
        val handler = Handler(Looper.getMainLooper())

        // MMS observer
        mmsObserver = object : ContentObserver(handler) {
            private var debounceJob: Job? = null
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                Log.d(TAG, "MMS content changed: $uri")
                // Debounce to avoid duplicate processing
                debounceJob?.cancel()
                debounceJob = serviceScope.launch {
                    delay(3000) // MMS needs time to fully download
                    processNewMmsMessages()
                }
            }
        }
        contentResolver.registerContentObserver(MMS_URI, true, mmsObserver!!)

        // SMS observer for Chat+/RCS messages (these appear in SMS content provider but don't trigger SMS_RECEIVED)
        smsObserver = object : ContentObserver(handler) {
            private var debounceJob: Job? = null
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                Log.d(TAG, "SMS content changed: $uri")
                debounceJob?.cancel()
                debounceJob = serviceScope.launch {
                    delay(1000)
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
                arrayOf("_id", "date"),
                "_id > ?",
                arrayOf(lastId.toString()),
                "_id ASC"
            )?.use { cursor ->
                var maxId = lastId
                while (cursor.moveToNext()) {
                    val mmsId = cursor.getLong(0)
                    if (mmsId > maxId) maxId = mmsId

                    val sender = getMmsSender(mmsId)
                    val body = getMmsTextContent(mmsId)

                    Log.d(TAG, "New MMS: id=$mmsId, sender=$sender, body=$body")

                    if (sender.isNullOrEmpty() || body.isNullOrEmpty()) continue

                    val matchedRule = findMatchingRule(rules, sender, body) ?: continue
                    Log.d(TAG, "MMS matched rule: ${describeRule(matchedRule)}")

                    val forwardBody = "[MMS 포워딩] $body"
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
    }

    private suspend fun processNewChatMessages() {
        val prefs = getSharedPreferences("sms_forwarder_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("forwarding_enabled", false)) return

        val lastId = prefs.getLong(PREF_LAST_CHAT_SMS_ID, 0L)

        try {
            val app = applicationContext as SmsForwarderApp
            val repository = app.repository
            val rules = repository.getEnabledRules()
            val forwardNumbers = repository.getEnabledNumbers()

            if (rules.isEmpty() || forwardNumbers.isEmpty()) return

            // Query for new incoming messages (type=1 is inbox)
            // Chat+/RCS messages are stored with protocol=null or specific sub_id
            contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf("_id", "address", "body", "type", "protocol"),
                "_id > ? AND type = ?",
                arrayOf(lastId.toString(), "1"), // type 1 = inbox (received)
                "_id ASC"
            )?.use { cursor ->
                var maxId = lastId
                val idIdx = cursor.getColumnIndex("_id")
                val addrIdx = cursor.getColumnIndex("address")
                val bodyIdx = cursor.getColumnIndex("body")
                val protocolIdx = cursor.getColumnIndex("protocol")

                while (cursor.moveToNext()) {
                    val msgId = cursor.getLong(idIdx)
                    if (msgId > maxId) maxId = msgId

                    val protocol = cursor.getString(protocolIdx)
                    // SMS_RECEIVED broadcast handles protocol != null (standard SMS)
                    // Chat+/RCS messages have protocol = null when inserted by messaging app
                    if (protocol != null) continue

                    val sender = cursor.getString(addrIdx) ?: continue
                    val body = cursor.getString(bodyIdx) ?: continue

                    Log.d(TAG, "New Chat+ message: id=$msgId, sender=$sender, body=$body")

                    val matchedRule = findMatchingRule(rules, sender, body) ?: continue
                    Log.d(TAG, "Chat+ matched rule: ${describeRule(matchedRule)}")

                    val forwardBody = "[채팅+ 포워딩] $body"
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

    private fun sendSms(destination: String, message: String) {
        val smsManager = getSystemService(SmsManager::class.java)
        val parts = smsManager.divideMessage(message)
        if (parts.size == 1) {
            smsManager.sendTextMessage(destination, null, message, null, null)
        } else {
            smsManager.sendMultipartTextMessage(destination, null, parts, null, null)
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
