package com.young.sample.adbtools.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.young.sample.adblib.model.AdbException
import com.young.sample.adblib.service.ShellService
import com.young.sample.adblib.transport.AdbSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 无障碍权限 ViewModel。
 *
 * 通过 ADB shell 命令启用/禁用本应用的无障碍服务。
 * 服务全限定名: com.young.sample.adbtools/.accessibility.ADBToolsAccessibilityService
 */
class AccessibilityViewModel(
    private val session: AdbSession
) : ViewModel() {

    companion object {
        private const val TAG = "ADB-A11yVM"
        private const val SERVICE_FQN =
            "com.young.sample.adbtools/com.young.sample.adbtools.accessibility.ADBToolsAccessibilityService"
    }

    private val shellService = ShellService(session)

    data class UiState(
        val isEnabled: Boolean = false,
        val isLoading: Boolean = false,
        val statusText: String = "点击「检查状态」查看无障碍服务是否已开启",
        val error: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /**
     * 检查当前无障碍服务状态。
     */
    fun checkStatus() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                // 查询 enabled_accessibility_services 是否包含我们的服务
                val result = shellService.exec("settings get secure enabled_accessibility_services")
                val services = result.stdout.trim()
                Log.d(TAG, "enabled_accessibility_services = '$services'")
                val enabled = services.contains(SERVICE_FQN)
                _state.update {
                    it.copy(
                        isEnabled = enabled,
                        isLoading = false,
                        statusText = if (enabled) "✓ 无障碍服务已开启" else "✗ 无障碍服务未开启"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "检查状态失败: ${e.message}", e)
                _state.update {
                    it.copy(isLoading = false, error = "检查失败: ${e.localizedMessage ?: e.message}")
                }
            }
        }
    }

    /**
     * 通过 ADB shell 启用无障碍服务。
     */
    fun enable() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                // 1. 设置无障碍服务列表
                shellService.exec("settings put secure enabled_accessibility_services $SERVICE_FQN")
                // 2. 开启无障碍总开关
                shellService.exec("settings put secure accessibility_enabled 1")
                Log.i(TAG, "无障碍服务已启用")
                _state.update {
                    it.copy(
                        isEnabled = true,
                        isLoading = false,
                        statusText = "✓ 无障碍服务已开启"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "启用失败: ${e.message}", e)
                _state.update {
                    it.copy(isLoading = false, error = "启用失败: ${e.localizedMessage ?: e.message}")
                }
            }
        }
    }

    /**
     * 通过 ADB shell 关闭无障碍服务。
     */
    fun disable() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                shellService.exec("settings put secure accessibility_enabled 0")
                shellService.exec("settings put secure enabled_accessibility_services \"\"")
                Log.i(TAG, "无障碍服务已关闭")
                _state.update {
                    it.copy(
                        isEnabled = false,
                        isLoading = false,
                        statusText = "✗ 无障碍服务已关闭"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "关闭失败: ${e.message}", e)
                _state.update {
                    it.copy(isLoading = false, error = "关闭失败: ${e.localizedMessage ?: e.message}")
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
