package com.ai.guardian.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ai.guardian.data.entity.ApprovalRequestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ApprovalRequestDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(request: ApprovalRequestEntity)

    @Query("SELECT * FROM approval_requests WHERE requestId = :requestId LIMIT 1")
    suspend fun getById(requestId: String): ApprovalRequestEntity?

    @Query("SELECT * FROM approval_requests WHERE status = 'PENDING' ORDER BY requestedAt DESC")
    fun getPendingFlow(): Flow<List<ApprovalRequestEntity>>

    @Query("UPDATE approval_requests SET status = :status WHERE requestId = :requestId")
    suspend fun updateStatus(requestId: String, status: String)

    @Query("DELETE FROM approval_requests WHERE requestedAt < :cutoff")
    suspend fun pruneOlderThan(cutoff: Long)
}
