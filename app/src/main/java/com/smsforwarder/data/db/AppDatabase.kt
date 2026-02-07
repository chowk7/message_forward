package com.smsforwarder.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.smsforwarder.data.dao.FilterRuleDao
import com.smsforwarder.data.dao.ForwardLogDao
import com.smsforwarder.data.dao.ForwardNumberDao
import com.smsforwarder.data.model.FilterRule
import com.smsforwarder.data.model.ForwardLog
import com.smsforwarder.data.model.ForwardNumber

@Database(
    entities = [FilterRule::class, ForwardNumber::class, ForwardLog::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun filterRuleDao(): FilterRuleDao
    abstract fun forwardNumberDao(): ForwardNumberDao
    abstract fun forwardLogDao(): ForwardLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sms_forwarder_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
