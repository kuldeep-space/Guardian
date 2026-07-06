package com.ai.guardian.data.entity

import androidx.room.Embedded
import androidx.room.Relation

data class FaceProfileWithTemplates(
    @Embedded val profile: FaceProfileEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "profileId"
    )
    val templates: List<FaceTemplateEntity>
)
