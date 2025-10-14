package com.grindrplus.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import com.grindrplus.persistence.model.HttpBodyLogEntity

@Dao
interface HttpBodyLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: HttpBodyLogEntity)
}