package com.luffy.adbwatchdog

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.util.Log

/**
 * NotificationListenerService: the system rebinds it after a force-stop (unlike
 * accessibility services), which makes it a reliable revival hook on Xiaomi firmware
 * that kills all third-party apps on standby.
 */
class AdbNotifyListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "notification listener connected -> revive watchdog")
        if (WatchdogService.prefs(this).getBoolean(WatchdogService.KEY_ENABLED, false)) {
            WatchdogService.start(this)
            BootJobService.schedule(this)
            if (!AdbWifiController.isEnabled(this)) {
                Log.i(TAG, "adb_enabled was 0 -> ${AdbWifiController.enable(this)}")
            }
        }
    }

    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification?) {
        // Cheap re-check: some firmwares only rebind when a notification arrives.
        if (WatchdogService.prefs(this).getBoolean(WatchdogService.KEY_ENABLED, false) &&
            !AdbWifiController.isEnabled(this)) {
            AdbWifiController.enable(this)
        }
    }

    override fun onNotificationRemoved(sbn: android.service.notification.StatusBarNotification?) {}

    companion object {
        private const val TAG = "AdbWatchdog"
    }
}
