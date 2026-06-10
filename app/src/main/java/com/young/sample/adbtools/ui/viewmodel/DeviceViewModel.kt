package com.young.sample.adbtools.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.young.sample.adblib.AdbClient
import com.young.sample.adblib.AdbConfig
import com.young.sample.adblib.model.AdbDevice
import com.young.sample.adblib.model.AdbException
import com.young.sample.adblib.model.DeviceState
import com.young.sample.adblib.transport.AdbSession
import com.young.sample.adblib.service.ShellService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * 设备列表页 ViewModel。
 */
class DeviceViewModel(application: Application) : AndroidViewModel(application) {

    /** 当前 ADB Server 主机地址 */
    private val _host = MutableStateFlow("127.0.0.1")
    val host: StateFlow<String> = _host.asStateFlow()

    /** 当前 ADB Server 端口 */
    private val _port = MutableStateFlow("5555")
    val port: StateFlow<String> = _port.asStateFlow()

    /** 直连 adbd 模式（绕过 ADB Server），默认开启 */
    private val _directMode = MutableStateFlow(true)
    val directMode: StateFlow<Boolean> = _directMode.asStateFlow()

    /** ADB Client 实例（config 更新时重建） */
    var adbClient: AdbClient = AdbConfig(host = "127.0.0.1", port = 5555).let { AdbClient(it) }
        private set

    /** RSA 密钥对存储目录（直连 adbd 认证用） */
    private val keyStoreDir: File
        get() = File(getApplication<Application>().filesDir, "adb_keys")

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
        refreshDevices()
    }

    /**
     * 更新 ADB Server 连接配置并重新连接。
     *
     * @param directMode true = 直连 adbd 模式（绕过 ADB Server），false = 通过 ADB Server 连接
     */
    fun updateConfig(host: String, port: Int, directMode: Boolean = false) {
        _host.value = host
        _port.value = port.toString()
        _directMode.value = directMode
        // 断开所有现有连接
        sessions.values.forEach { it.close() }
        sessions.clear()
        _connectedSerials.value = emptySet()
        // 重建 AdbClient（Server 模式下需要；直连模式下也用于 shell/sync 工厂方法）
        adbClient.close()
        adbClient = AdbClient(AdbConfig(host = host, port = port))
        // 重新扫描 / 直连
        if (directMode) {
            connectDirect()
        } else {
            refreshDevices()
        }
    }

    /**
     * 刷新设备列表。
     * 直连模式下重新发起 adbd 直连；Server 模式下扫描 ADB Server 设备列表。
     */
    fun refreshDevices() {
        if (_directMode.value) {
            connectDirect()
            return
        }
        viewModelScope.launch {
            _isScanning.value = true
            _error.value = null
            try {
                _devices.value = adbClient.getDevices()
                _serverVersion.value = "v${adbClient.getServerVersion()}"
            } catch (e: AdbException.ConnectionFailed) {
                _error.value = "无法连接到 ADB Server (${host.value}:${port.value}) — ${e.message}"
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
     * 直连 adbd（绕过 ADB Server）。
     * 连接成功后创建虚拟设备条目，使用 "direct" 作为 serial。
     */
    fun connectDirect() {
        viewModelScope.launch {
            _isScanning.value = true
            _error.value = null
            try {
                val host = _host.value
                val port = _port.value.toIntOrNull() ?: 5555
                Log.i("ADB-ViewModel", "开始直连 adbd: $host:$port, 密钥目录: ${keyStoreDir.absolutePath}")
                val session = adbClient.connectDirect(host, port, keyStoreDir)
                val serial = "direct"
                sessions[serial] = session
                _connectedSerials.value = _connectedSerials.value + serial
                _devices.value = listOf(
                    AdbDevice(serial = "$host:$port (直连)", state = DeviceState.DEVICE)
                )
                _serverVersion.value = "adbd 直连"
                Log.i("ADB-ViewModel", "✅ 直连 adbd 成功")
            } catch (e: AdbException.AuthenticationFailed) {
                Log.e("ADB-ViewModel", "❌ 认证失败: ${e.message}")
                _error.value = "adbd 认证失败: ${e.message}\n\n" +
                    "操作步骤:\n" +
                    "1. USB 连接手机到 PC，执行 adb tcpip 5555\n" +
                    "2. 回到本 app 点击「重新连接」\n" +
                    "3. 观察手机屏幕是否弹出「允许 USB 调试」对话框\n" +
                    "4. 点击「允许」并勾选「一律允许」"
                _devices.value = emptyList()
                _serverVersion.value = null
            } catch (e: AdbException.ConnectionFailed) {
                Log.e("ADB-ViewModel", "❌ 无法连接 adbd: ${e.message}")
                _error.value = "无法连接到 adbd (${_host.value}:${_port.value}): ${e.message}"
                _devices.value = emptyList()
                _serverVersion.value = null
            } catch (e: Exception) {
                Log.e("ADB-ViewModel", "❌ 直连异常: ${e.message}", e)
                _error.value = "直连失败: ${e.localizedMessage ?: e.message}"
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
