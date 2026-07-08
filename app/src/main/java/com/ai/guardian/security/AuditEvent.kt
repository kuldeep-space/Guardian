package com.ai.guardian.security

/**
 * Enumeration of all security-significant events Guardian can detect and audit.
 * Each value maps to the AuditEventEntity.eventType field stored in Room.
 */
enum class AuditEvent {
    // --- Lifecycle ---
    PROCESS_STARTED,
    FORCED_STOP_RECOVERY,

    // --- Access ---
    GUARDIAN_LAUNCHED,
    UNAUTHORIZED_SETTINGS_ACCESS,

    // --- Accessibility ---
    ACCESSIBILITY_DISABLED,
    ACCESSIBILITY_RESTORED,

    // --- Device Admin / Owner ---
    DEVICE_ADMIN_ENABLED,
    DEVICE_ADMIN_DISABLE_REQUESTED,
    DEVICE_ADMIN_DISABLED,
    DEVICE_OWNER_POLICY_APPLIED,

    // --- Package ---
    GUARDIAN_UNINSTALL_DETECTED,
    PACKAGE_INSTALL_DETECTED,

    // --- Permission ---
    PERMISSION_REVOKED,

    // --- Pairing ---
    PAIRING_ADDED,
    PAIRING_REMOVED,

    // --- Sync ---
    SYNC_FAILURE,
    SYNC_RECOVERED,

    // --- Authorization ---
    PARENT_APPROVAL_REQUESTED,
    PARENT_APPROVAL_GRANTED,
    PARENT_APPROVAL_REJECTED,
    PARENT_APPROVAL_EXPIRED,
    PARENT_APPROVAL_INVALID_TOKEN,

    // --- Protection ---
    PROTECTION_ENABLED,
    PROTECTION_DISABLED,
    FACE_PROFILE_ENROLLED,
    FACE_PROFILE_DELETED,
    SETTINGS_CHANGE_BLOCKED,
    SECURITY_PIN_ATTEMPT_FAILED,
    TAMPER_EVENT
}

