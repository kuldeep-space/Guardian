package com.ai.guardian.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ai.guardian.data.entity.FaceProfileEntity
import com.ai.guardian.data.entity.FaceTemplateEntity
import com.ai.guardian.data.entity.FaceProfileWithTemplates
import kotlinx.coroutines.flow.Flow

@Dao
interface FaceDao {
    @Transaction
    @Query("SELECT * FROM face_profiles")
    fun getAllProfilesWithTemplatesFlow(): Flow<List<FaceProfileWithTemplates>>

    @Transaction
    @Query("SELECT * FROM face_profiles")
    suspend fun getAllProfilesWithTemplates(): List<FaceProfileWithTemplates>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: FaceProfileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplates(templates: List<FaceTemplateEntity>)

    @Query("DELETE FROM face_profiles WHERE id = :id")
    suspend fun deleteProfileById(id: Long)

    @Query("UPDATE face_profiles SET name = :name WHERE id = :id")
    suspend fun renameProfile(id: Long, name: String)

    @Query("UPDATE face_profiles SET avatarColorArgb = :color WHERE id = :id")
    suspend fun updateAvatarColor(id: Long, color: Int)

    @Query("DELETE FROM face_profiles")
    suspend fun deleteAllProfiles()
}
