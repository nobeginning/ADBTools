package com.young.sample.adbtools.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.young.sample.adblib.Adb
import com.young.sample.adblib.model.AdbException
import com.young.sample.adblib.transport.AdbSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * 连接状态。
 */
sealed class ConnectionState {
    /** 未连接 */
    data object Disconnected : ConnectionState()
    /** 连接中 */
    data object Connecting : ConnectionState()
    /** 已连接 */
    data class Connected(val session: AdbSession) : ConnectionState()
    /** 连接失败 */
    data class Error(val message: String) : ConnectionState()
}

/**
 * 设备连接 ViewModel。
 *
 * 负责管理到本机 adbd 的直连生命周期。
 */
class DeviceViewModel(application: Application) : AndroidViewModel(application) {

    /** 当前连接状态 */
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    /** 目标主机地址 */
    private val _host = MutableStateFlow("127.0.0.1")
    val host: StateFlow<String> = _host.asStateFlow()

    /** 目标端口 */
    private val _port = MutableStateFlow("5555")
    val port: StateFlow<String> = _port.asStateFlow()

    /** 是否正在连接中 */
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    /** 上一次错误描述 */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** RSA 密钥对存储目录 */
    private val keyStoreDir: File
        get() = File(getApplication<Application>().filesDir, "adb_keys")

    /** 已连接的 serial 集合（供 TestHubScreen 下拉选择器使用） */
    private val _connectedSerials = MutableStateFlow<Set<String>>(emptySet())
    val connectedSerials: StateFlow<Set<String>> = _connectedSerials.asStateFlow()

    init {
        connect()
    }

    /**
     * 发起 adbd 直连。
     * 如果已有连接，会先断开再重连。
     */
    fun connect() {
        // 断开已有连接
        val current = _connectionState.value
        if (current is ConnectionState.Connected) {
            current.session.close()
        }

        viewModelScope.launch {
            _isScanning.value = true
            _error.value = null
            _connectionState.value = ConnectionState.Connecting

            try {
                val h = _host.value
                val p = _port.value.toIntOrNull() ?: 5555
                Log.i(TAG, "开始直连 adbd: $h:$p, 密钥目录: ${keyStoreDir.absolutePath}")

                val session = Adb.connectDirect(
                    host = h,
                    port = p,
                    keyStoreDir = keyStoreDir
                )

                val serial = "direct"
                _connectedSerials.value = setOf(serial)
                _connectionState.value = ConnectionState.Connected(session)
                Log.i(TAG, "✅ 直连 adbd 成功")
            } catch (e: AdbException.AuthenticationFailed) {
                Log.e(TAG, "❌ 认证失败: ${e.message}")
                val msg = "adbd 认证失败: ${e.message}\n\n" +
                    "操作步骤:\n" +
                    "1. USB 连接手机到 PC，执行 adb tcpip 5555\n" +
                    "2. 回到本 app 点击「重新连接」\n" +
                    "3. 观察手机屏幕是否弹出「允许 USB 调试」对话框\n" +
                    "4. 点击「允许」并勾选「一律允许」"
                _error.value = msg
                _connectionState.value = ConnectionState.Error(msg)
                _connectedSerials.value = emptySet()
            } catch (e: AdbException.ConnectionFailed) {
                Log.e(TAG, "❌ 无法连接 adbd: ${e.message}")
                val msg = "无法连接到 adbd (${_host.value}:${_port.value}): ${e.message}"
                _error.value = msg
                _connectionState.value = ConnectionState.Error(msg)
                _connectedSerials.value = emptySet()
            } catch (e: Exception) {
                Log.e(TAG, "❌ 直连异常: ${e.message}", e)
                val msg = "直连失败: ${e.localizedMessage ?: e.message}"
                _error.value = msg
                _connectionState.value = ConnectionState.Error(msg)
                _connectedSerials.value = emptySet()
            } finally {
                _isScanning.value = false
            }
        }
    }

    /**
     * 更新连接目标并重新连接。
     */
    fun updateTarget(host: String, port: Int) {
        _host.value = host
        _port.value = port.toString()
        connect()
    }

    /**
     * 获取 AdbSession（供导航路由和功能页面使用）。
     */
    fun getSession(serial: String): AdbSession? {
        val state = _connectionState.value
        return if (state is ConnectionState.Connected) state.session else null
    }

    /**
     * 检查是否已连接。
     */
    fun isConnected(serial: String): Boolean =
        _connectionState.value is ConnectionState.Connected

    override fun onCleared() {
        super.onCleared()
        val state = _connectionState.value
        if (state is ConnectionState.Connected) {
            state.session.close()
        }
    }

    companion object {
        private const val TAG = "ADB-VM"
    }
}
