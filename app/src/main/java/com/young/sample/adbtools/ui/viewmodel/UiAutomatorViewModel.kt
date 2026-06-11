package com.young.sample.adbtools.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.young.lib.adb.model.AdbException
import com.young.lib.adb.service.UiAutomatorService
import com.young.lib.adb.transport.AdbSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI Automator ViewModel — dump UI 树。
 */
class UiAutomatorViewModel(
    private val session: AdbSession
) : ViewModel() {

    companion object {
        private const val TAG = "ADB-UiAutomatorVM"

        /**
         * 将 uiautomator dump 输出的单行 XML 格式化为可读的多行缩进格式。
         *
         * 处理逻辑：
         * 1. 在 >< 之间插入换行，将连续的标签拆成独立行
         * 2. 根据开始/结束标签维护缩进层级
         * 3. 自闭合标签不改变缩进
         */
        fun formatXml(xml: String): String {
            if (xml.isBlank()) return xml

            val sb = StringBuilder()
            var indent = 0
            val indentUnit = "  "

            // 第一步：将 >< 替换为 >\n<，拆分粘连的标签
            val withBreaks = xml.replace("><", ">\n<")
            val lines = withBreaks.split("\n")

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue

                when {
                    // XML 声明 <?xml ...?>
                    trimmed.startsWith("<?") -> {
                        sb.appendLine(trimmed)
                    }
                    // 注释 <!-- ... -->
                    trimmed.startsWith("<!--") -> {
                        sb.appendLine(indentUnit.repeat(indent) + trimmed)
                    }
                    // 结束标签 </tag> — 先减少缩进再输出
                    trimmed.startsWith("</") -> {
                        indent = maxOf(0, indent - 1)
                        sb.appendLine(indentUnit.repeat(indent) + trimmed)
                    }
                    // 自闭合标签 <tag .../>
                    trimmed.endsWith("/>") -> {
                        sb.appendLine(indentUnit.repeat(indent) + trimmed)
                    }
                    // 开始标签 <tag ...>
                    trimmed.startsWith("<") -> {
                        sb.appendLine(indentUnit.repeat(indent) + trimmed)
                        indent++
                    }
                    // 文本内容
                    else -> {
                        sb.appendLine(indentUnit.repeat(indent) + trimmed)
                    }
                }
            }

            return sb.toString()
        }
    }

    private val uiAutomatorService = UiAutomatorService(session)

    data class UiState(
        val uiTreeXml: String? = null,
        val screenWidth: Int = 0,
        val screenHeight: Int = 0,
        val isLoading: Boolean = false,
        val error: String? = null,
        val successMessage: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        loadScreenSize()
    }

    private fun loadScreenSize() {
        viewModelScope.launch {
            try {
                val (w, h) = uiAutomatorService.getScreenSize()
                _state.update { it.copy(screenWidth = w, screenHeight = h) }
                Log.i(TAG, "屏幕尺寸: ${w}x${h}")
            } catch (e: Exception) {
                Log.e(TAG, "获取屏幕尺寸失败: ${e.message}")
            }
        }
    }

    fun dumpUiTree() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, successMessage = null) }
            Log.i(TAG, "开始 dump UI 树...")
            try {
                val rawXml = uiAutomatorService.dumpUiTree()
                val formatted = formatXml(rawXml)
                _state.update {
                    it.copy(
                        uiTreeXml = formatted,
                        isLoading = false,
                        successMessage = "UI 树获取成功 (${rawXml.length} 字符)"
                    )
                }
                Log.i(TAG, "UI 树 dump 成功: ${rawXml.length} 字符 → 格式化后 ${formatted.length} 字符")
            } catch (e: AdbException.ServiceError) {
                Log.e(TAG, "UI 树 dump 失败: ${e.message}")
                _state.update {
                    it.copy(isLoading = false, error = "服务错误: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "UI 树 dump 异常: ${e.message}", e)
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "dump 失败: ${e.localizedMessage ?: e.message}"
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(error = null, successMessage = null) }
    }
}
