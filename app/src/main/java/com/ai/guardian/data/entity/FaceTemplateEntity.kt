package com.ai.guardian.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "face_templates",
    foreignKeys = [
        ForeignKey(
            entity = FaceProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["profileId"])]
)
data class FaceTemplateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val profileId: Long,
    val embeddingData: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FaceTemplateEntity

        if (id != other.id) return false
        if (profileId != other.profileId) return false
        if (!embeddingData.contentEquals(other.embeddingData)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + profileId.hashCode()
        result = 31 * result + embeddingData.contentHashCode()
        return result
    }
}
