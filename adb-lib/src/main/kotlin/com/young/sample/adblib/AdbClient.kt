package com.young.sample.adblib

import com.young.sample.adblib.model.AdbDevice
import com.young.sample.adblib.model.AdbException
import com.young.sample.adblib.model.ForwardEntry
import com.young.sample.adblib.service.HostServices
import com.young.sample.adblib.service.ShellService
import com.young.sample.adblib.service.SyncService
import com.young.sample.adblib.transport.AdbSession
import com.young.sample.adblib.transport.AdbStream
import com.young.sample.adblib.transport.TcpTransport
import kotlinx.coroutines.flow.Flow

/**
 * ADB Client — 公开主入口。
 *
 * 提供设备发现、设备连接、设备操作等功能。
 * 每个设备操作使用独立的 TCP 连接，避免 host:transport 的独占限制。
 *
 * 使用示例:
 * ```kotlin
 * val client = AdbClient()
 * val devices = client.getDevices()
 * if (devices.isNotEmpty()) {
 *     val session = client.connect(devices.first().serial)
 *     val result = session.shell.exec("ls -la")
 *     println(result.stdout)
 *     session.close()
 * }
 * ```
 */
class AdbClient(
    private val config: AdbConfig = AdbConfig.DEFAULT
) : AutoCloseable {

    private val hostServices = HostServices(
        host = config.host,
        port = config.port,
        connectTimeoutMs = config.connectTimeoutMs,
        readTimeoutMs = config.readTimeoutMs
    )

    // ---- Host 级操作 ----

    /**
     * 获取 ADB Server 版本。
     */
    suspend fun getServerVersion(): Int = hostServices.getVersion()

    /**
     * 获取已连接设备列表。
     */
    suspend fun getDevices(): List<AdbDevice> = hostServices.getDevices()

    /**
     * 持续追踪设备变化（长连接 Flow）。
     */
    fun trackDevices(): Flow<List<AdbDevice>> = hostServices.trackDevices()

    /**
     * 连接目标设备。
     *
     * 创建一个到 ADB Server 的新 TCP 连接，执行 host:transport 切换到目标设备，
     * 返回 AdbSession 用于后续操作。
     *
     * @throws AdbException.DeviceNotFound 如果设备未找到
     * @throws AdbException.ConnectionFailed 如果连接失败
     */
    suspend fun connect(serial: String): AdbSession {
        // 先验证设备存在
        val devices = hostServices.getDevices()
        val device = devices.find { it.serial == serial }
            ?: throw AdbException.DeviceNotFound(serial)

        if (device.state != com.young.sample.adblib.model.DeviceState.DEVICE) {
            throw AdbException.DeviceNotFound(
                "Device $serial is in state '${device.state.label}', not 'device'"
            )
        }

        // 创建新连接并切换到目标设备
        val transport = TcpTransport(
            host = config.host,
            port = config.port,
            connectTimeoutMs = config.connectTimeoutMs,
            readTimeoutMs = config.readTimeoutMs
        )
        transport.connect()

        try {
            // 在此连接上发送 host:transport，使 ADB Server 将后续包路由到设备
            transport.writeSmartSocket("host:transport:$serial")
            transport.readSmartSocketOkay()

            // 创建 Session 并启动 read loop
            val session = AdbSession(transport)
            session.startReadLoop()
            return session
        } catch (e: Exception) {
            transport.close()
            throw e
        }
    }

    // ---- 端口转发 ----

    /**
     * 设置端口转发。
     */
    suspend fun forward(local: String, remote: String) =
        hostServices.forward(local, remote)

    /**
     * 设置反向端口转发。
     */
    suspend fun reverse(remote: String, local: String) =
        hostServices.reverse(remote, local)

    /**
     * 列出所有转发。
     */
    suspend fun listForwards(): List<ForwardEntry> =
        hostServices.listForwards()

    /**
     * 移除所有转发。
     */
    suspend fun killForwardAll() =
        hostServices.killForwardAll()

    /**
     * 终止 ADB Server。
     */
    suspend fun killServer() = hostServices.kill()

    /**
     * 为 session 创建 ShellService。
     */
    fun shell(session: AdbSession): ShellService = ShellService(session)

    /**
     * 为 session 创建 SyncService。
     */
    fun sync(session: AdbSession): SyncService = SyncService(session)

    override fun close() {
        // HostServices 无状态，无需清理
    }
}
