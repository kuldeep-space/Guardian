# Guardian Platform Limitations & Behaviors

This document outlines expected Android operating system behaviors and limitations that affect Guardian's background execution and remote synchronization capabilities. 

Future maintainers should reference this document to understand why certain background constraints cannot be purely "fixed" in application code and require user education or system-level exemptions.

## 1. Android Force Stop Behavior

**Scenario:** A user navigates to `Settings -> Apps -> Guardian -> Force Stop`.

**Expected OS Behavior:**
Android deliberately isolates Force Stopped applications. When this action is taken:
* All active components (Activities, Services, BroadcastReceivers) are immediately killed.
* **GuardianForegroundService** is terminated.
* **SyncEngineManager** and its coroutines are cancelled.
* **Firestore Listeners** are destroyed.
* Pending `WorkManager` jobs and `AlarmManager` intents are wiped from the system scheduler for this application.
* **Critical:** The `GuardianAccessibilityService` is immediately disabled at the system level.

**Recovery:**
There is no automated recovery from a Force Stop. The application remains in a dormant state until the user manually launches it again. Furthermore, the user will be required to manually re-navigate to the Accessibility Settings to re-enable Guardian's Accessibility Service. This is an intentional security design of the Android OS.

## 2. OEM Background Process Killing

**Scenario:** Aggressive battery optimization by Original Equipment Manufacturers (OEMs) such as Xiaomi (MIUI/HyperOS), Oppo (ColorOS), Vivo (Funtouch OS), and Huawei (EMUI).

**Expected OS Behavior:**
Despite Android's standard Foreground Service guidelines (`START_STICKY`, Notification Channels), aggressive OEMs deploy proprietary task killers to extend battery life. These systems routinely terminate Foreground Services when:
* The user swipes the app away from the Recents menu.
* The screen turns off.
* The app consumes sustained CPU/Network (e.g., maintaining WebSockets for Firestore).

**Recovery & Mitigation:**
* **Accessibility Anchor:** Active Accessibility Services are highly privileged on most OEMs. `GuardianAccessibilityService` acts as our strongest background anchor, often surviving OEM purges and allowing us to resurrect the `GuardianForegroundService`.
* **User Exemption:** For guaranteed reliability, the user **must** manually exempt Guardian from battery optimization. We utilize `BatteryOptimizationUtil` to detect OEM restrictions and prompt the user to enable "Auto-start" and "Unrestricted Background Activity". 

## 3. Foreground Service Type Restrictions

**Scenario:** Android 14 (API 34) introduced strict Foreground Service (FGS) types.

**Expected OS Behavior:**
An application must declare an explicit purpose for its Foreground Service. Guardian uses `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` for device protection and remote synchronization. While this satisfies the OS technically, Google Play policies heavily scrutinize `specialUse`. 
If policies change, maintaining background syncing without an active Accessibility Service or a user-visible overlay may become more difficult.

## 4. Multi-Device Scalability & Firestore Limits

**Scenario:** Synchronizing application state across many devices or synchronizing a massive number of applications.

**System Limits:**
* **Listener Count:** The architecture relies on exactly two Firestore snapshot listeners per device, regardless of how many parent controllers are paired. This scales effectively to unlimited remote parents.
* **Firestore Batch Limits:** A single Firestore `WriteBatch` is strictly limited by Google to 500 operations. 
* **Mitigation:** The `DeviceSyncManager` partitions full-sync uploads into chunks of 400 operations. Maintainers must ensure any future metadata additions or bulk-operations adhere to this chunking methodology to prevent `IllegalArgumentException` crashes on power-user devices.

## 5. Conflict Resolution & Time Sync

**Scenario:** Concurrent changes occur on the local device and remote parent device while offline or on poor network conditions.

**Expected Behavior:**
The system enforces **Last-Write-Wins** conflict resolution using **Server-Authoritative Timestamps**.
* Local device clocks (`System.currentTimeMillis()`) are inherently untrustworthy and can be manipulated or drift.
* All synchronization conflicts are resolved by comparing the Firebase `@ServerTimestamp` attached to the update. 
* If a remote change arrives with a server timestamp older than the last known server timestamp for that application, it is rejected.
* This ensures chronological consistency globally, even if the devices exist in different time zones or have desynced hardware clocks.
