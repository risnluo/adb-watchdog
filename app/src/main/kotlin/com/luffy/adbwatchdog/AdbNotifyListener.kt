package com.luffy.adbwatchdog

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * 系统在强杀后会重新绑定通知监听服务（无障碍不会），据此复活进程。
 * 复活时机和固件写 adb_enabled=0 的先后顺序不固定，所以连接后连查 90 秒。
 */
class AdbNotifyListener : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private var remaining = 0

    private val retry = object : Runnable {
        override fun run() {
            if (!WatchdogService.prefs(this@AdbNotifyListener)
                    .getBoolean(WatchdogService.KEY_ENABLED, false)) return
            if (!AdbWifiController.isEnabled(this@AdbNotifyListener)) {
                Log.i(TAG, "adb_enabled=0 -> ${AdbWifiController.enable(this@AdbNotifyListener)}")
            }
            restoreAccessibility()
            if (remaining-- > 0) handler.postDelayed(this, 3000)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "notification listener connected -> revive")
        if (!WatchdogService.prefs(this).getBoolean(WatchdogService.KEY_ENABLED, false)) return
        runCatching { WatchdogService.start(this) }
        BootJobService.schedule(this)
        remaining = 30
        handler.removeCallbacks(retry)
        handler.post(retry)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (WatchdogService.prefs(this).getBoolean(WatchdogService.KEY_ENABLED, false) &&
            !AdbWifiController.isEnabled(this)) {
            AdbWifiController.enable(this)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    override fun onDestroy() {
        handler.removeCallbacks(retry)
        super.onDestroy()
    }

    /** 强杀会把自己的无障碍条目从列表里删掉，这里补回去（需要 WRITE_SECURE_SETTINGS）。 */
    private fun restoreAccessibility() {
        val me = "$packageName/${WatchdogAccessibilityService::class.java.name}"
        val cur = Settings.Secure.getString(contentResolver, "enabled_accessibility_services") ?: ""
        if (cur.contains(me)) return
        val next = if (cur.isBlank()) me else "$cur:$me"
        runCatching {
            Settings.Secure.putString(contentResolver, "enabled_accessibility_services", next)
            Settings.Secure.putInt(contentResolver, "accessibility_enabled", 1)
        }
    }

    companion object {
        private const val TAG = "AdbWatchdog"
    }
}
