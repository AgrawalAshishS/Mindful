We're transitioning from the current dual-tracking system (Usage Stats + Accessibility) to a more reliable and efficient Accessibility Service-only setup for tracking app usage and enforcing restrictions.
🧩 Problem with Current Implementation

At present, Mindful uses both Usage Stats and Accessibility Service to track app usage and apply restrictions. This results in a lot of redundant and boilerplate code.

However, there are a few critical issues:

    Inconsistent behavior on custom ROMs: For example, on some Samsung devices, Usage Stats fails to detect the active app.
    Foreground service limitations: Foreground services require an ongoing notification. If the user revokes notification permission, the system may kill the service - breaking usage tracking.
    Notification Permission Requirement: Due to Android’s restrictions, Mindful is forced to request notification permission just to keep its tracking service alive - not ideal for user experience.

✨ The New Approach

We’re switching to an Accessibility Service-only model to simplify and strengthen the tracking

    No need for a persistent notification - so no more dependency on notification permission.
    Better service persistence - Accessibility services are managed by the system and are less likely to be killed, even on custom ROMs.
    Improved tamper protection - Even if users disable overlay permissions, Mindful can still take users to the home screen when they try to open blocked content.
    Modern and modular - This approach is now standard among many apps with similar functionality.

Overall, this results in a more reliable, robust, and maintainable tracking mechanism.
⚠️ Caveats

    This is a significant shift and will likely introduce bugs and edge cases in the beginning.
    It will require time for debugging and stabilization - I’ll release this in beta first.
    Once fully tested and stable, we’ll roll it out to production.
    Accessibility permission will now be required during onboarding - it will no longer be optional.

📌 Tasks

    Implement the new Accessibility Service
    Detect active app accurately
    Enforce app usage restrictions
    Enforce focus mode
    Enforce bedtime restrictions
    Enforce short-form content restrictions (YouTube Shorts, Instagram Reels, etc.)
    Enforce web content restrictions (may include new time-based rules)
    Implement new database tables schema
	
- First create plan and put it in .plan folder with <date>-<time>.md file.
- Read previous plan files to understand what was done in past.
- Finish plan with following must haves before start coding.
- Make sure you check why your logic should work and is it the best way of doing the work?
- Looking to have best code at the end, so remove any unwanted old legacy code.
- Write and run unit tests to check implemented functionality.
- Ensure at end of your work code is formatted and compiled successfully.
- Do not remove plan file.
- commit your code at end with proper message.
- Verification: Verify that app usage is correctly tracked and blocked (if restricted) using the new Accessibility-based detection.
- Edge Cases: Watch for behavior when the Accessibility Service is toggled off/on by the system or user (logic handles this by closing/opening sessions).


I have defined requirement details in .requirement/requirement.md.