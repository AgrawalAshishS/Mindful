package com.mindful.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.VpnService
import androidx.activity.result.ActivityResultLauncher
import com.mindful.android.enums.DndWakeLock
import com.mindful.android.generics.SafeServiceConnection
import com.mindful.android.generics.ServiceBinder
import com.mindful.android.helpers.AlarmTasksSchedulingHelper.cancelBedtimeRoutineTasks
import com.mindful.android.helpers.AlarmTasksSchedulingHelper.cancelNotificationBatchTask
import com.mindful.android.helpers.AlarmTasksSchedulingHelper.scheduleBedtimeRoutineTasks
import com.mindful.android.helpers.AlarmTasksSchedulingHelper.scheduleNotificationBatchTask
import com.mindful.android.helpers.device.DeviceAppsHelper.getDeviceAppInfos
import com.mindful.android.helpers.device.NewActivitiesLaunchHelper
import com.mindful.android.helpers.device.NotificationHelper
import com.mindful.android.helpers.device.PermissionsHelper
import com.mindful.android.helpers.storage.SharedPrefsHelper
import com.mindful.android.helpers.storage.UsageDatabaseHelper
import com.mindful.android.helpers.usages.AppsUsageHelper.getAppsUsageForInterval
import com.mindful.android.models.BedtimeSchedule
import com.mindful.android.models.FocusSession
import com.mindful.android.models.Notification
import com.mindful.android.models.NotificationSettings
import com.mindful.android.services.accessibility.MindfulAccessibilityService
import com.mindful.android.services.notification.MindfulNotificationListenerService
import com.mindful.android.services.timer.EmergencyPauseService
import com.mindful.android.services.timer.FocusSessionService
import com.mindful.android.services.vpn.MindfulVpnService
import com.mindful.android.utils.AppUtils
import com.mindful.android.utils.JsonUtils
import com.mindful.android.utils.Utils
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import java.util.Calendar
import java.util.Locale

class FgMethodCallHandler(
    private val context: Context,
    private val activity: Activity? = null,
    private val vpnPermLauncher: ActivityResultLauncher<Intent>? = null,
) : MethodCallHandler {

    private val focusServiceConn =
        SafeServiceConnection(
            context = context,
            serviceClass = FocusSessionService::class.java
        )

    private val vpnServiceConn =
        SafeServiceConnection(
            context = context,
            serviceClass = MindfulVpnService::class.java
        )

    private val notificationServiceConn =
        SafeServiceConnection(
            context = context,
            serviceClass = MindfulNotificationListenerService::class.java
        )


    init {
        // Bind to Services if they are already running
        vpnServiceConn.bindService()
        notificationServiceConn.bindService()
        focusServiceConn.bindService()
    }


    fun dispose() {
        // Unbind all services
        vpnServiceConn.unBindService()
        notificationServiceConn.unBindService()
        focusServiceConn.unBindService()
    }

    private fun updateLocale(languageCode: String) {
        if (languageCode.isNotEmpty()) {
            val newLocale = Locale(languageCode)
            Locale.setDefault(newLocale)
            val config = Configuration()
            config.setLocale(newLocale)
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
        }
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            // ==============================================================================================================
            // ====================================== SYSTEM =================================================================
            // ==============================================================================================================

            "updateLocale" -> {
                updateLocale(call.arguments() ?: "en")
                result.success(true)
            }

            "updateExcludedApps" -> {
                SharedPrefsHelper.getSetExcludedApps(context, call.arguments() ?: "")
                result.success(true)
            }

            "getDeviceInfo" -> {
                result.success(AppUtils.getDeviceInfoMap(context))
            }

            "getDeviceAppsInfo" -> {
                getDeviceAppInfos(
                    context = context,
                    onSuccess = { data -> result.success(data) }
                )
            }

            "getAppsUsageForInterval" -> {
                getAppsUsageForInterval(
                    context = context,
                    startMsEpoch = call.argument("startDateTime"),
                    endMsEpoch = call.argument("endDateTime"),
                    onSuccess = { data -> result.success(data) }
                )
            }

            "getAppsLaunchCount" -> {
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis
                val end = System.currentTimeMillis()

                result.success(
                    UsageDatabaseHelper.getInstance(context).getAppLaunchCounts(start, end)
                )
            }

            "getShortsScreenTimeMs" -> {
                result.success(SharedPrefsHelper.getSetShortsScreenTimeMs(context, null))
            }

            "getNativeCrashLogs" -> {
                result.success(SharedPrefsHelper.getCrashLogsArrayJsonString(context))
            }

            "clearNativeCrashLogs" -> {
                SharedPrefsHelper.clearCrashLogs(context)
                result.success(true)
            }

            // ==============================================================================================================
            // ====================================== SERVICES =================================================================
            // ==============================================================================================================

            "updateAppRestrictions" -> {
                SharedPrefsHelper.getSetAppRestrictions(
                    context,
                    call.arguments() ?: ""
                )
                result.success(true)
            }

            "updateRestrictionsGroups" -> {
                SharedPrefsHelper.getSetRestrictionGroups(
                    context,
                    call.arguments() ?: ""
                )
                result.success(true)
            }

            "updateInternetBlockedApps" -> {
                val blockedApps =
                    JsonUtils.parseStringSet(call.arguments() ?: "")
                if (vpnServiceConn.isActive) {
                    vpnServiceConn.service?.updateBlockedApps(blockedApps)
                } else if (blockedApps.isNotEmpty() && getAndAskVpnPermission(false)) {
                    vpnServiceConn.setOnConnectedCallback { service ->
                        service.updateBlockedApps(
                            blockedApps
                        )
                    }
                    vpnServiceConn.startAndBind()
                }
                result.success(true)
            }

            "updateWellBeingSettings" -> {
                SharedPrefsHelper.getSetWellBeingSettings(
                    context,
                    call.arguments() ?: ""
                )
                result.success(true)
            }

            "updateBedtimeSchedule" -> {
                val jsonBedtimeSettings = call.arguments() ?: ""
                val bedtimeSettings = BedtimeSchedule.fromJson(jsonBedtimeSettings)
                if (bedtimeSettings.isScheduleOn) {
                    scheduleBedtimeRoutineTasks(context, jsonBedtimeSettings)
                } else {
                    cancelBedtimeRoutineTasks(context)
                    if (bedtimeSettings.shouldStartDnd) {
                        NotificationHelper.toggleDnd(context, DndWakeLock.BEDTIME_MODE, false)
                    }
                }
                result.success(true)
            }

            "activeEmergencyPause" -> {
                if (!Utils.isServiceRunning(context, EmergencyPauseService::class.java)
                    && Utils.isServiceRunning(context, MindfulAccessibilityService::class.java)
                ) {
                    context.startService(
                        Intent(context, EmergencyPauseService::class.java).setAction(
                            ServiceBinder.ACTION_START_MINDFUL_SERVICE
                        )
                    )
                    result.success(true)
                } else {
                    result.success(false)
                }
            }

            "updateFocusSession" -> {
                val focusSession = FocusSession.fromJson(call.arguments() ?: "")
                if (focusServiceConn.isActive) {
                    focusServiceConn.service?.updateFocusSession(focusSession)
                } else {
                    focusServiceConn.setOnConnectedCallback { service: FocusSessionService ->
                        service.startFocusSession(
                            focusSession
                        )
                    }
                    focusServiceConn.startAndBind()
                }
                result.success(true)
            }

            "giveUpOrFinishFocusSession" -> {
                if (focusServiceConn.isActive) {
                    focusServiceConn.service?.giveUpOrStopFocusSession(call.arguments() ?: false)
                    focusServiceConn.unBindService()
                }
                result.success(true)
            }

            "updateNotificationSettings" -> {
                val settingsJson = call.arguments() ?: ""
                val settings = NotificationSettings.fromJson(settingsJson)

                /// Update service
                if (notificationServiceConn.isActive) {
                    notificationServiceConn.service?.updateNotificationSettings(settings)
                } else if (settings.batchedApps.isNotEmpty() || settings.storeNonBatchedToo) {
                    notificationServiceConn.setOnConnectedCallback { service: MindfulNotificationListenerService ->
                        service.updateNotificationSettings(settings)
                    }
                    notificationServiceConn.bindService()
                }

                /// Schedule batches
                if (settings.schedules.isNotEmpty()) {
                    scheduleNotificationBatchTask(context, settingsJson)
                } else {
                    cancelNotificationBatchTask(context)
                }

                result.success(true)
            }

            // ==============================================================================================================
            // ===================================== PERMISSIONS ============================================================
            // ==============================================================================================================

            "getAndAskAccessibilityPermission" -> {
                result.success(
                    PermissionsHelper.getAndAskAccessibilityPermission(
                        context,
                        call.arguments() ?: false
                    )
                )
            }

            "getAndAskAdminPermission" -> {
                result.success(
                    PermissionsHelper.getAndAskAdminPermission(
                        context,
                        call.arguments() ?: false
                    )
                )
            }

            "getAndAskUsageAccessPermission" -> {
                // Deprecated: Usage Access is no longer required. Mocking true.
                result.success(true)
            }

            "getAndAskIgnoreBatteryOptimizationPermission" -> {
                result.success(
                    PermissionsHelper.getAndAskIgnoreBatteryOptimizationPermission(
                        context,
                        call.arguments() ?: false
                    )
                )
            }

            "getAndAskDisplayOverlayPermission" -> {
                result.success(
                    PermissionsHelper.getAndAskDisplayOverlayPermission(
                        context,
                        call.arguments() ?: false
                    )
                )
            }

            "getAndAskExactAlarmPermission" -> {
                result.success(
                    PermissionsHelper.getAndAskExactAlarmPermission(
                        context,
                        call.arguments() ?: false
                    )
                )
            }

            "getAndAskNotificationPermission" -> {
                result.success(
                    activity?.let {
                        return@let PermissionsHelper.getAndAskNotificationPermission(
                            it,
                            call.arguments() ?: false
                        )
                    } ?: false
                )
            }

            "getAndAskDndPermission" -> {
                result.success(
                    PermissionsHelper.getAndAskDndPermission(
                        context,
                        call.arguments() ?: false
                    )
                )
            }

            "getAndAskNotificationAccessPermission" -> {
                result.success(
                    PermissionsHelper.getAndAskNotificationAccessPermission(
                        context,
                        call.arguments() ?: false
                    )
                )
            }

            "getAndAskVpnPermission" -> {
                result.success(getAndAskVpnPermission(call.arguments() ?: false))
            }

            // ==============================================================================================================
            // ====================================== UTILS =================================================================
            // ==============================================================================================================

            "disableDeviceAdmin" -> {
                NewActivitiesLaunchHelper.disableDeviceAdmin(context)
                result.success(true)
            }

            "promptForQuickTile" -> {
                NewActivitiesLaunchHelper.promptForQuickFocusTile(context, result)
            }

            "openAppWithPackage" -> {
                NewActivitiesLaunchHelper.openAppWithPackage(
                    context,
                    call.arguments() ?: ""
                )
                result.success(true)
            }

            "openAppWithNotificationThread" -> {
                val notification = Notification.fromJson(call.arguments() ?: "")
                NewActivitiesLaunchHelper.openAppWithNotificationThread(
                    context = context,
                    notification = notification,
                    pendingIntent = notificationServiceConn.service?.getPendingIntentForKey(
                        notification.key
                    ),
                )
                result.success(true)
            }

            "openAppSettingsForPackage" -> {
                NewActivitiesLaunchHelper.openSettingsForPackage(
                    context,
                    call.arguments() ?: ""
                )
                result.success(true)
            }

            "openDeviceDndSettings" -> {
                NewActivitiesLaunchHelper.openDeviceDndSettings(context)
                result.success(true)
            }

            "openAutoStartSettings" -> {
                result.success(NewActivitiesLaunchHelper.openAutoStartSettings(context))
            }

            "restartApp" -> {
                activity?.let {
                    NewActivitiesLaunchHelper.restartMindful(it)
                }
                result.success(true)
            }

            "launchUrl" -> {
                NewActivitiesLaunchHelper.launchUrl(context, call.arguments() ?: "")
                result.success(true)
            }

            "parseHostFromUrl" -> {
                result.success(Utils.parseHostNameFromUrl(call.arguments() ?: "") ?: "")
            }

            else -> result.notImplemented()
        }
    }

    /**
     * Checks if the Create VPN permission is granted and optionally asks for it if not granted.
     *
     * @param askPermissionToo Whether to prompt the user to enable Create VPN permission if not granted.
     * @return True if Create VPN permission is granted, false otherwise.
     */
    private fun getAndAskVpnPermission(askPermissionToo: Boolean): Boolean {
        val intent = VpnService.prepare(context)
        if (askPermissionToo && intent != null) {
            vpnPermLauncher?.launch(intent)
        }
        return intent == null
    }

}