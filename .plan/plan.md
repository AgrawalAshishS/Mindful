# Implementation Plan: Accessibility Service-only Tracking

## 1. Refactor Logic to Remove Usage Stats Dependency

### A. Update `RestrictionManager.kt`
*   **Goal**: Remove `UsageStatsManager` and use `UsageDatabaseHelper` for usage limits.
*   **Changes**:
    *   Remove `usageStatsManager` from constructor.
    *   Instantiate `UsageDatabaseHelper`.
    *   In `evaluateScreenTimeLimit`:
        *   Calculate start of day (Midnight).
        *   Call `dbHelper.queryUsageForInterval(midnight, now)`.
        *   Convert results from milliseconds to seconds.
        *   Use this map for logic.

### B. Update `MindfulTrackerService.kt`
*   **Goal**: Clean up `RestrictionManager` instantiation.
*   **Changes**:
    *   Remove any reference to `UsageStatsManager` (though likely implicitly handled by removing it from `RestrictionManager` constructor).

### C. Delete `ScreenUsageHelper.kt`
*   **Goal**: Cleanup.
*   **Changes**: Delete file.

## 2. Unit Testing
*   **Goal**: Verify new DB logic works correctly.
*   **Tests**:
    *   `UsageDatabaseHelperTest`:
        *   Test `insertUsageSession`.
        *   Test `queryUsageForInterval` (overlapping, contained, partial overlap).

## 3. Verification & Formatting
*   **Goal**: Ensure code compiles and looks good.
*   **Steps**:
    *   Run `flutter pub get`.
    *   Run `dart format .`.
    *   Verify `gradle` build (via shell if possible, or assume correctness based on previous steps).

## 4. Final Commit
*   **Goal**: Commit changes.
