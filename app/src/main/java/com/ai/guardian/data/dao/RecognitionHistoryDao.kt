package com.ai.guardian.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ai.guardian.data.entity.RecognitionHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecognitionHistoryDao {
    @Query("SELECT * FROM recognition_history ORDER BY timestamp DESC")
    fun getAllHistoryFlow(): Flow<List<RecognitionHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: RecognitionHistoryEntity): Long

    @Query("DELETE FROM recognition_history")
    suspend fun deleteAllHistory()
    
    @Query("SELECT * FROM recognition_history WHERE recognitionType = :type ORDER BY timestamp DESC")
    fun getHistoryByTypeFlow(type: String): Flow<List<RecognitionHistoryEntity>>
}
