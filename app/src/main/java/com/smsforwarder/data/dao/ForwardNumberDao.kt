package com.smsforwarder.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.smsforwarder.data.model.ForwardNumber

@Dao
interface ForwardNumberDao {
    @Query("SELECT * FROM forward_numbers ORDER BY id DESC")
    fun getAll(): LiveData<List<ForwardNumber>>

    @Query("SELECT * FROM forward_numbers WHERE isEnabled = 1")
    suspend fun getEnabledNumbers(): List<ForwardNumber>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(number: ForwardNumber): Long

    @Update
    suspend fun update(number: ForwardNumber)

    @Delete
    suspend fun delete(number: ForwardNumber)
}
