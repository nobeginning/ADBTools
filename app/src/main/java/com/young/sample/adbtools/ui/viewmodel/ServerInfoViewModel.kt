package com.young.sample.adbtools.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.young.sample.adblib.AdbClient
import com.young.sample.adblib.model.AdbException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ADB Server 信息页 ViewModel。
 */
class ServerInfoViewModel(
    private val adbClient: AdbClient
) : ViewModel() {

    companion object {
        private const val TAG = "ADB-ServerVM"
    }

    data class UiState(
        val serverVersion: String? = null,
        val deviceCount: Int = 0,
        val isLoading: Boolean = false,
        val error: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /**
     * 刷新 Server 信息和设备列表。
     */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            Log.d(TAG, "获取 Server 信息...")
            try {
                val version = adbClient.getServerVersion()
                val devices = adbClient.getDevices()
                Log.i(TAG, "Server 信息: v$version, ${devices.size} 设备")
                _state.update {
                    it.copy(
                        serverVersion = "v$version",
                        deviceCount = devices.size,
                        isLoading = false
                    )
                }
            } catch (e: AdbException.ConnectionFailed) {
                _state.update {
                    it.copy(
                        error = "无法连接到 ADB Server: ${e.message}",
                        isLoading = false
                    )
                }
            } catch (e: AdbException.ProtocolError) {
                _state.update {
                    it.copy(
                        error = "协议错误: ${e.message}",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        error = "获取信息失败: ${e.localizedMessage ?: e.message}",
                        isLoading = false
                    )
                }
            }
        }
    }

    /**
     * 终止 ADB Server（需确认后调用）。
     */
    fun killServer() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                adbClient.killServer()
                _state.update {
                    it.copy(
                        serverVersion = null,
                        deviceCount = 0,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        error = "终止 Server 失败: ${e.localizedMessage ?: e.message}",
                        isLoading = false
                    )
                }
            }
        }
    }
}
