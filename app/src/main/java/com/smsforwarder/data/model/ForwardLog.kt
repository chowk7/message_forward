package com.smsforwarder.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "forward_logs")
data class ForwardLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val originalSender: String,
    val messageBody: String,
    val forwardedTo: String,
    val matchedRule: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSuccess: Boolean = true,
    val errorMessage: String? = null
)
