package com.smsforwarder.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "filter_rules")
data class FilterRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: FilterType,
    val value: String,
    val isEnabled: Boolean = true
)

enum class FilterType {
    TEXT_KEYWORD,
    SENDER_NUMBER
}
