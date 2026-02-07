package com.smsforwarder.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.smsforwarder.data.model.ForwardLog

@Dao
interface ForwardLogDao {
    @Query("SELECT * FROM forward_logs ORDER BY timestamp DESC")
    fun getAll(): LiveData<List<ForwardLog>>

    @Insert
    suspend fun insert(log: ForwardLog): Long

    @Query("DELETE FROM forward_logs")
    suspend fun deleteAll()
}
