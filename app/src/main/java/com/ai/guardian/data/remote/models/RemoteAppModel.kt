package com.ai.guardian.data.remote.models

import com.google.firebase.firestore.PropertyName

data class RemoteAppModel(
    val packageName: String = "",
    val appName: String = "",
    @get:PropertyName("isLocked")
    @PropertyName("isLocked")
    val isLocked: Boolean = false,
    @com.google.firebase.firestore.ServerTimestamp
    val timestamp: java.util.Date? = null,
    val updatedBy: String = "child",
    val updatedByDeviceId: String = ""
)
