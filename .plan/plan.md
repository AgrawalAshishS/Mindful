# Implementation Plan: Transition to Accessibility Service-only Architecture

## 1. Data Persistence & Communication
*   **Goal**: Replace Service Binding with Shared Preferences for communicating restrictions to `MindfulAccessibilityService`.
*   **Changes**:
    *   **`SharedPrefsHelper`**: Add methods to get/set:
        *   `AppRestrictions` (JSON String)
        *   `RestrictionGroups` (JSON String)
        *   `FocusedApps` (Set<String>)
        *   `BedtimeApps` (Set<String>)
    *   **`UsageDatabaseHelper`**: Add `getAppLaunchCount(packageName, startTime, endTime)` to replace in-memory counting.

## 2. Refactor `MindfulAccessibilityService`
*   **Goal**: Make it the central hub for restriction enforcement.
*   **Changes**:
    *   Instantiate `OverlayManager`, `ReminderManager`, `ContinuousUsageManager`, `RestrictionManager`.
    *   Implement `onSharedPreferenceChanged` to reload restrictions/focus/bedtime apps into `RestrictionManager`.
    *   Initialize `RestrictionManager` with data from `SharedPrefs` on startup.
    *   Integrate `onNewAppLaunch` logic (moved from `TrackerService`).
    *   Hook `TrackingManager` events to `onNewAppLaunch`.

## 3. Update External Callers
*   **`FgMethodCallHandler`**:
    *   Remove `MindfulTrackerService` binding.
    *   Update methods (`updateAppRestrictions`, etc.) to write to `SharedPrefsHelper`.
    *   Update `getAppsLaunchCount` to query `UsageDatabaseHelper`.
*   **`FocusSessionService`**:
    *   Remove `MindfulTrackerService` binding.
    *   Update start/stop/update logic to write `FocusedApps` to `SharedPrefsHelper`.

## 4. Cleanup
*   **Delete**:
    *   `MindfulTrackerService.kt`
    *   `LaunchTrackingManager.kt`
*   **Update `AndroidManifest.xml`**: Remove `MindfulTrackerService`.
*   **Update `DeviceBootReceiver`**: Remove `MindfulTrackerService` start logic.

## 5. Verification
*   **Tests**:
    *   Verify `UsageDatabaseHelper.getLaunchCount`.
    *   Verify `RestrictionManager` logic with mocked DB/Prefs.
*   **Manual**: Check if restrictions apply immediately after saving settings in Flutter.

## 6. Edge Cases
*   **Service Restart**: `MindfulAccessibilityService` reads Prefs on `onServiceConnected`, ensuring state restoration.
*   **Race Conditions**: SharedPrefs listener ensures updates are propagated.