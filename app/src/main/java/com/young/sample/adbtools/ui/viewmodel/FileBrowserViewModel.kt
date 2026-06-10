package com.young.sample.adbtools.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.young.sample.adblib.model.AdbException
import com.young.sample.adblib.model.SyncEntry
import com.young.sample.adblib.service.SyncService
import com.young.sample.adblib.transport.AdbSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.file.Path

/**
 * 文件浏览器 ViewModel。
 */
class FileBrowserViewModel(
    private val session: AdbSession
) : ViewModel() {

    companion object {
        private const val TAG = "ADB-FileVM"
    }

    private val syncService = SyncService(session)

    data class UiState(
        val currentPath: String = "/",
        val entries: List<SyncEntry> = emptyList(),
        val pathHistory: List<String> = listOf("/"),
        val isLoading: Boolean = false,
        val error: String? = null,
        val selectedEntry: SyncEntry? = null,
        val transferMessage: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        loadDirectory("/")
    }

    fun loadDirectory(path: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            Log.d(TAG, "加载目录: $path")
            try {
                val entries = syncService.listDir(path)
                Log.i(TAG, "目录加载成功: $path (${entries.size} 项)")
                val sorted = entries.sortedWith(
                    compareByDescending<SyncEntry> { it.isDirectory() }
                        .thenBy { it.name.lowercase() }
                )
                _state.update {
                    it.copy(
                        currentPath = path,
                        entries = sorted,
                        isLoading = false
                    )
                }
            } catch (e: AdbException.ServiceError) {
                _state.update {
                    it.copy(error = "服务错误: ${e.message}", isLoading = false)
                }
            } catch (e: AdbException.StreamClosed) {
                _state.update {
                    it.copy(error = "连接已断开", isLoading = false)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        error = "读取目录失败: ${e.localizedMessage ?: e.message}",
                        isLoading = false
                    )
                }
            }
        }
    }

    fun navigateToDir(dirName: String) {
        val path = _state.value.currentPath
        val newPath = if (path.endsWith("/")) "$path$dirName" else "$path/$dirName"
        val history = _state.value.pathHistory.toMutableList()
        history.add(newPath)
        _state.update { it.copy(pathHistory = history) }
        loadDirectory(newPath)
    }

    fun navigateUp() {
        val history = _state.value.pathHistory.toMutableList()
        if (history.size > 1) {
            history.removeAt(history.lastIndex)
            _state.update { it.copy(pathHistory = history) }
            loadDirectory(history.last())
        }
    }

    fun selectEntry(entry: SyncEntry?) {
        _state.update { it.copy(selectedEntry = entry) }
    }

    fun pullFile(remotePath: String, localPath: Path) {
        viewModelScope.launch {
            _state.update { it.copy(transferMessage = "正在拉取...", error = null) }
            try {
                syncService.pull(remotePath, localPath)
                _state.update { it.copy(transferMessage = "拉取完成") }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        transferMessage = null,
                        error = "拉取失败: ${e.localizedMessage ?: e.message}"
                    )
                }
            }
        }
    }

    fun pushFile(localPath: Path, remotePath: String) {
        viewModelScope.launch {
            _state.update { it.copy(transferMessage = "正在推送...", error = null) }
            try {
                syncService.push(localPath, remotePath)
                _state.update { it.copy(transferMessage = "推送完成") }
                // 刷新当前目录
                loadDirectory(_state.value.currentPath)
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        transferMessage = null,
                        error = "推送失败: ${e.localizedMessage ?: e.message}"
                    )
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}

/** 类型别名：SyncEntry 是否为目录 */
private fun SyncEntry.isDirectory(): Boolean = (mode shr 14) and 1 == 1
