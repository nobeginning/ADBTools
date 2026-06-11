package com.young.lib.adb.service

import android.util.Log
import com.young.lib.adb.model.AdbException
import com.young.lib.adb.transport.AdbSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * UI Automator 服务 — dump 设备屏幕上的 UI 树。
 *
 * 通过 shell uiautomator dump 命令抓取当前屏幕的 UI 层级结构（XML 格式），
 * 可用于 UI 自动化测试、元素定位等场景。
 *
 * 流程:
 * 1. uiautomator dump 生成 XML 到 /sdcard/window_dump.xml
 * 2. 通过 SyncService 拉取文件内容（避免 Shell V2 对大输出的截断问题）
 * 3. 删除临时文件
 */
class UiAutomatorService(private val session: AdbSession) {

    companion object {
        private const val TAG = "ADB-UiAutomator"
        private const val DUMP_PATH = "/sdcard/window_dump.xml"
    }

    private val shellService = ShellService(session)
    private val syncService = SyncService(session)

    /**
     * Dump 当前屏幕的 UI 树，返回 XML 字符串。
     *
     * @return UI 树 XML 字符串
     * @throws AdbException.ServiceError 如果 dump 失败（如设备不支持 uiautomator）
     */
    suspend fun dumpUiTree(): String {
        Log.i(TAG, "开始 dump UI 树...")

        return withContext(Dispatchers.IO) {
            try {
                // 1. 执行 dump 命令
                Log.d(TAG, "执行 uiautomator dump $DUMP_PATH")
                val dumpResult = shellService.exec("uiautomator dump $DUMP_PATH")
                Log.d(TAG, "dump 输出: ${dumpResult.stdout.trim()}")

                if (dumpResult.stderr.contains("Error") || dumpResult.stderr.contains("ERROR")) {
                    Log.e(TAG, "dump 失败: ${dumpResult.stderr}")
                    throw AdbException.ServiceError("uiautomator dump", dumpResult.stderr)
                }

                // 2. 通过 sync 协议拉取 XML 文件（避免 shell cat 被 Shell V2 截断）
                Log.d(TAG, "通过 sync 协议拉取 dump 文件: $DUMP_PATH")
                val fileBytes = syncService.pullBytes(DUMP_PATH)
                val xml = String(fileBytes, Charsets.UTF_8)

                if (xml.isBlank()) {
                    Log.e(TAG, "dump 文件为空")
                    throw AdbException.ServiceError("uiautomator", "dump 文件为空，设备可能不支持 uiautomator")
                }

                Log.i(TAG, "UI 树 dump 成功，XML 大小: ${xml.length} 字符, ${fileBytes.size} bytes")

                // 3. 删除临时文件
                Log.d(TAG, "删除临时文件")
                shellService.exec("rm -f $DUMP_PATH")

                xml
            } catch (e: AdbException) {
                Log.e(TAG, "UI 树 dump 失败: ${e.message}")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "UI 树 dump 异常: ${e.message}", e)
                throw AdbException.ServiceError("uiautomator", e.message ?: "unknown")
            }
        }
    }

    /**
     * 获取屏幕分辨率（用于辅助坐标计算）。
     *
     * @return Pair(width, height) 屏幕宽度和高度（像素）
     */
    suspend fun getScreenSize(): Pair<Int, Int> {
        Log.d(TAG, "获取屏幕尺寸...")
        return withContext(Dispatchers.IO) {
            try {
                val result = shellService.exec("wm size")
                val text = result.stdout.trim()
                // 格式: "Physical size: 1080x2400" 或 "Override size: 1080x2340"
                val regex = Regex("""(\d+)x(\d+)""")
                val match = regex.find(text)
                if (match != null) {
                    val w = match.groupValues[1].toInt()
                    val h = match.groupValues[2].toInt()
                    Log.i(TAG, "屏幕尺寸: ${w}x${h}")
                    Pair(w, h)
                } else {
                    Log.w(TAG, "无法解析屏幕尺寸: $text")
                    Pair(1080, 1920) // fallback
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取屏幕尺寸失败: ${e.message}")
                Pair(1080, 1920) // fallback
            }
        }
    }
}
