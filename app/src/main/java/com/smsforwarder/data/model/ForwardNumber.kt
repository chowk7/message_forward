package com.smsforwarder.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "forward_numbers")
data class ForwardNumber(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val phoneNumber: String,
    val label: String = "",
    val isEnabled: Boolean = true
)
