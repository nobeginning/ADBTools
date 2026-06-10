package com.young.sample.adbtools.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * ADBTools 无障碍服务。
 *
 * 通过 ADB 命令启用后，可获取前台应用包名、UI 层级信息等。
 * 启用命令：
 *   settings put secure accessibility_enabled 1
 *   settings put secure enabled_accessibility_services com.young.sample.adbtools/com.young.sample.adbtools.accessibility.ADBToolsAccessibilityService
 */
class ADBToolsAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ADB-A11y"
        @Volatile
        var isRunning = false
            private set
        @Volatile
        var currentPackageName: String? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        Log.i(TAG, "无障碍服务已启动")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (pkg != null && pkg != currentPackageName) {
                currentPackageName = pkg
                Log.d(TAG, "前台应用切换: $pkg")
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "无障碍服务被中断")
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        currentPackageName = null
        Log.i(TAG, "无障碍服务已销毁")
    }
}
