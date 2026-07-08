package com.ai.guardian.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ai.guardian.data.entity.AuditEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: AuditEventEntity)

    @Query("SELECT * FROM audit_events ORDER BY timestamp DESC LIMIT 200")
    fun getRecentEventsFlow(): Flow<List<AuditEventEntity>>

    @Query("SELECT * FROM audit_events WHERE isSynced = 0 ORDER BY timestamp ASC LIMIT 50")
    suspend fun getUnsynced(): List<AuditEventEntity>

    @Query("UPDATE audit_events SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("DELETE FROM audit_events WHERE timestamp < :cutoff")
    suspend fun pruneOlderThan(cutoff: Long)
}
