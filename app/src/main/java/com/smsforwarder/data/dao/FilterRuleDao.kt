package com.smsforwarder.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.smsforwarder.data.model.FilterRule

@Dao
interface FilterRuleDao {
    @Query("SELECT * FROM filter_rules ORDER BY id DESC")
    fun getAll(): LiveData<List<FilterRule>>

    @Query("SELECT * FROM filter_rules WHERE isEnabled = 1")
    suspend fun getEnabledRules(): List<FilterRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: FilterRule): Long

    @Update
    suspend fun update(rule: FilterRule)

    @Delete
    suspend fun delete(rule: FilterRule)
}
