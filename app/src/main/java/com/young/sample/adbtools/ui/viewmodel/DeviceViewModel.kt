package com.young.sample.adbtools.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.young.sample.adblib.AdbClient
import com.young.sample.adblib.model.AdbDevice
import com.young.sample.adblib.model.AdbException
import com.young.sample.adblib.transport.AdbSession
import com.young.sample.adblib.service.ShellService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 设备列表页 ViewModel。
 */
class DeviceViewModel : ViewModel() {

    private val adbClient = AdbClient()

    private val _devices = MutableStateFlow<List<AdbDevice>>(emptyList())
    val devices: StateFlow<List<AdbDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _serverVersion = MutableStateFlow<String?>(null)
    val serverVersion: StateFlow<String?> = _serverVersion.asStateFlow()

    /** 当前已连接的 session 映射：serial -> AdbSession */
    private val sessions = mutableMapOf<String, AdbSession>()

    private val _connectedSerials = MutableStateFlow<Set<String>>(emptySet())
    val connectedSerials: StateFlow<Set<String>> = _connectedSerials.asStateFlow()

    init {
        // 启动时自动扫描
        refreshDevices()
    }

    /**
     * 刷新设备列表。
     */
    fun refreshDevices() {
        viewModelScope.launch {
            _isScanning.value = true
            _error.value = null
            try {
                _devices.value = adbClient.getDevices()
                _serverVersion.value = "v${adbClient.getServerVersion()}"
            } catch (e: AdbException.ConnectionFailed) {
                _error.value = "无法连接到 ADB Server (${e.message})"
                _devices.value = emptyList()
                _serverVersion.value = null
            } catch (e: Exception) {
                _error.value = "扫描失败: ${e.localizedMessage ?: e.message}"
            } finally {
                _isScanning.value = false
            }
        }
    }

    /**
     * 获取 ADB Server 版本。
     */
    fun fetchServerVersion() {
        viewModelScope.launch {
            try {
                _serverVersion.value = "v${adbClient.getServerVersion()}"
            } catch (_: Exception) {
                _serverVersion.value = null
            }
        }
    }

    /**
     * 连接到指定设备。
     */
    fun connectToDevice(serial: String) {
        viewModelScope.launch {
            _error.value = null
            try {
                val session = adbClient.connect(serial)
                sessions[serial] = session
                _connectedSerials.value = _connectedSerials.value + serial
            } catch (e: Exception) {
                _error.value = "连接 $serial 失败: ${e.localizedMessage ?: e.message}"
            }
        }
    }

    /**
     * 断开指定设备。
     */
    fun disconnect(serial: String) {
        sessions[serial]?.close()
        sessions.remove(serial)
        _connectedSerials.value = _connectedSerials.value - serial
    }

    /**
     * 获取设备的 ShellService。
     */
    fun getShellService(serial: String): ShellService? {
        val session = sessions[serial] ?: return null
        return adbClient.shell(session)
    }

    /**
     * 获取 AdbSession（供 Shell 页面使用）。
     */
    fun getSession(serial: String): AdbSession? = sessions[serial]

    /**
     * 检查是否已连接到某设备。
     */
    fun isConnected(serial: String): Boolean = serial in _connectedSerials.value

    override fun onCleared() {
        super.onCleared()
        sessions.values.forEach { it.close() }
        sessions.clear()
        adbClient.close()
    }
}
