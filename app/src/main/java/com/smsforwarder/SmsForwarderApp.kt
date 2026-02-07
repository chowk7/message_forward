package com.smsforwarder

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.smsforwarder.data.db.AppDatabase
import com.smsforwarder.data.repository.ForwarderRepository

class SmsForwarderApp : Application() {

    val database by lazy { AppDatabase.getInstance(this) }
    val repository by lazy {
        ForwarderRepository(
            database.filterRuleDao(),
            database.forwardNumberDao(),
            database.forwardLogDao()
        )
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SMS 포워딩 서비스",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "SMS 포워딩 상태 알림"
        }

        val logChannel = NotificationChannel(
            LOG_CHANNEL_ID,
            "포워딩 알림",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "SMS 포워딩 완료 알림"
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
        manager.createNotificationChannel(logChannel)
    }

    companion object {
        const val CHANNEL_ID = "sms_forward_service"
        const val LOG_CHANNEL_ID = "sms_forward_log"
    }
}
