package com.young.sample.adbtools.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.young.sample.adblib.service.ShellService
import com.young.sample.adblib.transport.AdbSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Shell 终端页 ViewModel。
 *
 * @param serial 目标设备序列号
 * @param session 已连接的 AdbSession
 */
class ShellViewModel(
    private val serial: String,
    private val session: AdbSession
) : ViewModel() {

    private val shellService = ShellService(session)

    private val _outputLines = MutableStateFlow<List<ShellLine>>(emptyList())
    val outputLines: StateFlow<List<ShellLine>> = _outputLines.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _inputHistory = MutableStateFlow<List<String>>(emptyList())
    val inputHistory: StateFlow<List<String>> = _inputHistory.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var currentJob: Job? = null
    private var historyIndex = -1

    /**
     * 执行一条 shell 命令。
     */
    fun executeCommand(command: String) {
        if (command.isBlank() || _isRunning.value) return

        // 添加到输入历史
        val newHistory = _inputHistory.value.toMutableList().apply {
            add(command)
        }
        _inputHistory.value = newHistory
        historyIndex = newHistory.size

        // 显示命令
        addLine(ShellLine(command = true, text = "$ $command"))

        currentJob = viewModelScope.launch {
            _isRunning.value = true
            _error.value = null
            try {
                val result = shellService.exec(command)
                if (result.stdout.isNotBlank()) {
                    addLine(ShellLine(text = result.stdout.trimEnd()))
                }
                if (result.stderr.isNotBlank()) {
                    addLine(ShellLine(text = result.stderr.trimEnd(), isError = true))
                }
                addLine(
                    ShellLine(
                        text = "exit code: ${result.exitCode}",
                        isSystem = true
                    )
                )
            } catch (e: Exception) {
                addLine(
                    ShellLine(
                        text = "错误: ${e.localizedMessage ?: e.message}",
                        isError = true
                    )
                )
            } finally {
                _isRunning.value = false
            }
        }
    }

    /**
     * 清除输出。
     */
    fun clearOutput() {
        _outputLines.value = emptyList()
    }

    /**
     * 获取上一条历史命令。
     */
    fun previousHistory(): String? {
        val history = _inputHistory.value
        if (history.isEmpty()) return null
        historyIndex = maxOf(0, historyIndex - 1)
        return history.getOrNull(historyIndex)
    }

    /**
     * 获取下一条历史命令。
     */
    fun nextHistory(): String? {
        val history = _inputHistory.value
        if (history.isEmpty()) return null
        historyIndex = minOf(history.size, historyIndex + 1)
        return history.getOrNull(historyIndex)
    }

    private fun addLine(line: ShellLine) {
        _outputLines.value = _outputLines.value + line
    }

    override fun onCleared() {
        super.onCleared()
        currentJob?.cancel()
    }
}

/**
 * Shell 输出行。
 */
data class ShellLine(
    val command: Boolean = false,
    val isError: Boolean = false,
    val isSystem: Boolean = false,
    val text: String
)
