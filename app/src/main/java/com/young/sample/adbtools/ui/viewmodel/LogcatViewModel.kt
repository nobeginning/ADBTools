package com.young.sample.adbtools.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.young.sample.adblib.model.AdbException
import com.young.sample.adblib.protocol.ShellData
import com.young.sample.adblib.service.ShellService
import com.young.sample.adblib.transport.AdbSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Logcat 浏览器 ViewModel。
 */
class LogcatViewModel(
    private val session: AdbSession
) : ViewModel() {

    private val shellService = ShellService(session)
    private var logcatJob: Job? = null

    data class LogEntry(
        val timestamp: String = "",
        val pid: Int = 0,
        val tid: Int = 0,
        val level: String = "",
        val tag: String = "",
        val message: String = ""
    )

    enum class LogLevel(val label: String, val priority: Int) {
        VERBOSE("V", 0), DEBUG("D", 1), INFO("I", 2), WARN("W", 3), ERROR("E", 4), FATAL("F", 5)
    }

    enum class LogBuffer(val label: String) {
        MAIN("main"), SYSTEM("system"), EVENTS("events"), CRASH("crash")
    }

    data class UiState(
        val entries: List<LogEntry> = emptyList(),
        val filteredEntries: List<LogEntry> = emptyList(),
        val isRunning: Boolean = false,
        val filterText: String = "",
        val minLevel: LogLevel = LogLevel.VERBOSE,
        val buffer: LogBuffer = LogBuffer.MAIN,
        val scrollLocked: Boolean = false,
        val error: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    companion object {
        private const val TAG = "ADB-LogcatVM"
        const val MAX_ENTRIES = 5000

        fun parseLogcatLines(text: String): List<LogEntry> {
            // logcat -v time 格式: MM-DD HH:MM:SS.mmm LEVEL/TAG( PID): MESSAGE
            // 注意: tag 和 (PID) 之间可能有多个空格，如 "D/AAL     ( 1218):"
            val regex = Regex(
                """^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+([VDIWEFS])/(\S+)\s*\(\s*(\d+)\):\s*(.*)$""",
                RegexOption.MULTILINE
            )
            return text.lines().filter { it.isNotBlank() }.mapNotNull { line ->
                val match = regex.find(line.trim())
                if (match != null) {
                    LogEntry(
                        timestamp = match.groupValues[1],
                        pid = match.groupValues[4].toIntOrNull() ?: 0,
                        tid = 0,  // -v time 不含线程 ID
                        level = match.groupValues[2],
                        tag = match.groupValues[3],
                        message = match.groupValues[5]
                    )
                } else null
            }
        }
    }

    fun start() {
        if (_state.value.isRunning) return
        _state.update { it.copy(isRunning = true, error = null) }
        Log.i(TAG, "开始 logcat 监听 (buffer=${_state.value.buffer.label})")
        // 在 Default 线程上运行，避免 logcat 数据流阻塞主线程
        logcatJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val buffer = _state.value.buffer.label
                shellService.execStream("logcat -v time -b $buffer").collect { data ->
                    when (data) {
                        is ShellData.Stdout -> {
                            val text = String(data.data, Charsets.UTF_8)
                            Log.d(TAG, "收到 stdout: ${data.data.size}B, 文本前100字: ${text.take(100)}")
                            val newEntries = parseLogcatLines(text)
                            Log.d(TAG, "解析到 ${newEntries.size} 条日志")
                            if (newEntries.isNotEmpty()) {
                                withContext(Dispatchers.Main) {
                                    addEntries(newEntries)
                                }
                                Log.d(TAG, "已添加 ${newEntries.size} 条, 当前总数=${_state.value.entries.size}")
                            }
                        }
                        is ShellData.Exit -> {
                            Log.d(TAG, "收到 Exit: code=${data.code}")
                            withContext(Dispatchers.Main) {
                                _state.update { it.copy(isRunning = false) }
                            }
                        }
                        else -> {
                            Log.d(TAG, "收到其他类型: ${data::class.simpleName}")
                        }
                    }
                }
            } catch (e: AdbException.StreamClosed) {
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(isRunning = false) }
                }
            } catch (e: Exception) {
                if (isActive) {
                    withContext(Dispatchers.Main) {
                        _state.update {
                            it.copy(isRunning = false, error = "Logcat 错误: ${e.localizedMessage ?: e.message}")
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        logcatJob?.cancel()
        logcatJob = null
        _state.update { it.copy(isRunning = false) }
    }

    fun clear() {
        _state.update { it.copy(entries = emptyList(), filteredEntries = emptyList()) }
    }

    fun setFilter(text: String) {
        _state.update { it.copy(filterText = text) }
        applyFilters()
    }

    fun setMinLevel(level: LogLevel) {
        _state.update { it.copy(minLevel = level) }
        applyFilters()
    }

    fun setBuffer(buffer: LogBuffer) {
        _state.update { it.copy(buffer = buffer) }
        if (_state.value.isRunning) { stop(); start() }
    }

    fun toggleScrollLock() {
        _state.update { it.copy(scrollLocked = !it.scrollLocked) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun exportEntries(): String {
        return _state.value.filteredEntries.joinToString("\n") { entry ->
            "${entry.timestamp} ${entry.pid.toString().padStart(5)}:${entry.tid.toString().padStart(5)} ${entry.level}/${entry.tag}: ${entry.message}"
        }
    }

    private fun addEntries(newEntries: List<LogEntry>) {
        val current = _state.value.entries.toMutableList()
        current.addAll(newEntries)
        if (current.size > MAX_ENTRIES) {
            current.subList(0, current.size - MAX_ENTRIES).clear()
        }
        _state.update { it.copy(entries = current.toList()) }
        applyFilters()
    }

    private fun applyFilters() {
        val s = _state.value
        var result = s.entries
        val minPriority = s.minLevel.priority
        result = result.filter { entry ->
            (LogLevel.entries.find { it.label == entry.level }?.priority ?: 0) >= minPriority
        }
        val query = s.filterText.lowercase().trim()
        if (query.isNotEmpty()) {
            result = result.filter { entry ->
                entry.tag.lowercase().contains(query) || entry.message.lowercase().contains(query)
            }
        }
        Log.d(TAG, "applyFilters: entries=${s.entries.size}, filtered=${result.size}, minLevel=${s.minLevel.label}, filterText='${s.filterText}'")
        _state.update { it.copy(filteredEntries = result) }
    }

    override fun onCleared() {
        super.onCleared()
        logcatJob?.cancel()
    }
}
