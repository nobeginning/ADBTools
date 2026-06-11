package com.young.lib.adb.service

import android.util.Log
import com.young.lib.adb.model.AdbException
import com.young.lib.adb.transport.AdbSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 输入服务 — 在设备上模拟点击、滑动、按键、文本输入。
 *
 * 底层通过 ADB shell input 命令实现:
 *   input tap <x> <y>
 *   input swipe <x1> <y1> <x2> <y2> [duration]
 *   input keyevent <keycode>
 *   input text <text>
 */
class InputService(private val session: AdbSession) {

    companion object {
        private const val TAG = "ADB-Input"

        // 常用按键码
        const val KEYCODE_HOME = 3
        const val KEYCODE_BACK = 4
        const val KEYCODE_APP_SWITCH = 187
        const val KEYCODE_ENTER = 66
        const val KEYCODE_DEL = 67
        const val KEYCODE_TAB = 61
        const val KEYCODE_DPAD_UP = 19
        const val KEYCODE_DPAD_DOWN = 20
        const val KEYCODE_DPAD_LEFT = 21
        const val KEYCODE_DPAD_RIGHT = 22
        const val KEYCODE_VOLUME_UP = 24
        const val KEYCODE_VOLUME_DOWN = 25
        const val KEYCODE_POWER = 26
        const val KEYCODE_CAMERA = 27
        const val KEYCODE_MENU = 82
        const val KEYCODE_SEARCH = 84
    }

    private val shellService = ShellService(session)

    /**
     * 模拟点击。
     *
     * @param x 目标 X 坐标（相对于屏幕左上角）
     * @param y 目标 Y 坐标（相对于屏幕左上角）
     */
    suspend fun tap(x: Int, y: Int) {
        Log.i(TAG, "模拟点击: ($x, $y)")
        exec("input tap $x $y")
        Log.d(TAG, "点击完成")
    }

    /**
     * 模拟滑动。
     *
     * @param x1 起始 X 坐标
     * @param y1 起始 Y 坐标
     * @param x2 结束 X 坐标
     * @param y2 结束 Y 坐标
     * @param durationMs 滑动持续时间（毫秒），默认 300ms
     */
    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int = 300) {
        Log.i(TAG, "模拟滑动: ($x1, $y1) → ($x2, $y2), 持续 ${durationMs}ms")
        exec("input swipe $x1 $y1 $x2 $y2 $durationMs")
        Log.d(TAG, "滑动完成")
    }

    /**
     * 模拟按键事件。
     *
     * @param keyCode Android KeyEvent 按键码
     */
    suspend fun keyEvent(keyCode: Int) {
        Log.i(TAG, "模拟按键: keyCode=$keyCode")
        exec("input keyevent $keyCode")
        Log.d(TAG, "按键完成")
    }

    /**
     * 输入文本（仅支持 ASCII/数字/标点，不支持中文）。
     * 如需输入中文，使用 ADBKeyboard 或通过剪贴板注入。
     *
     * @param text 要输入的文本
     */
    suspend fun text(text: String) {
        Log.i(TAG, "模拟文本输入: \"$text\" (${text.length} 字符)")
        // 转义特殊字符：空格需要特殊处理
        val escaped = text.replace(" ", "%s")
        exec("input text $escaped")
        Log.d(TAG, "文本输入完成")
    }

    /**
     * 长按。
     *
     * @param x X 坐标
     * @param y Y 坐标
     * @param durationMs 长按持续时间（毫秒），默认 1000ms
     */
    suspend fun longPress(x: Int, y: Int, durationMs: Int = 1000) {
        Log.i(TAG, "模拟长按: ($x, $y), 持续 ${durationMs}ms")
        swipe(x, y, x, y, durationMs)
        Log.d(TAG, "长按完成")
    }

    private suspend fun exec(command: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val result = shellService.exec(command)
                Log.d(TAG, "命令执行: $command → exit=${result.exitCode}")
                if (result.stderr.isNotBlank()) {
                    Log.w(TAG, "命令 stderr: ${result.stderr.trim()}")
                }
                result.stdout
            } catch (e: AdbException.ServiceError) {
                Log.e(TAG, "输入命令执行失败: ${e.message}")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "输入异常: ${e.message}", e)
                throw AdbException.ServiceError("input", e.message ?: "unknown")
            }
        }
    }
}
