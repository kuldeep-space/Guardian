package com.ai.guardian.security

/**
 * Enumeration of every operation that requires explicit Parent authorization.
 * No protected action may bypass this gate.
 */
enum class ProtectedAction(val displayName: String) {
    OPEN_GUARDIAN_SETTINGS("Open Guardian Settings"),
    DISABLE_GLOBAL_PROTECTION("Disable Global Protection"),
    DISABLE_APP_PROTECTION("Disable App Protection"),
    ENROLL_FACE_PROFILE("Enroll Face Profile"),
    DELETE_FACE_PROFILE("Delete Face Profile"),
    REPLACE_FACE_PROFILE("Replace Face Profile"),
    REMOVE_PARENT_PAIRING("Remove Parent Pairing"),
    ADD_NEW_PARENT("Add New Parent Device"),
    RESET_GUARDIAN("Reset Guardian"),
    CLEAR_LOCAL_DATA("Clear Local Data"),
    CLEAR_SYNC_DATA("Clear Sync Data"),
    DISABLE_REMOTE_MANAGEMENT("Disable Remote Management"),
    CHANGE_SECURITY_SETTINGS("Change Security Settings"),
    DISABLE_ACCESSIBILITY("Disable Accessibility Protection"),
    DISABLE_DEVICE_ADMIN("Disable Device Admin"),
    REMOVE_DEVICE_OWNER_POLICY("Remove Device Owner Policy"),
    STOP_SYNC_ENGINE("Stop Sync Engine"),
    STOP_FOREGROUND_SERVICE("Stop Guardian Service"),
    DISABLE_TAMPER_PROTECTION("Disable Tamper Protection")
}
