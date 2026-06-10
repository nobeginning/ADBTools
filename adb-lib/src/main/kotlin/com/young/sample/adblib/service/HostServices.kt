package com.young.sample.adblib.service

import com.young.sample.adblib.model.AdbDevice
import com.young.sample.adblib.model.AdbException
import com.young.sample.adblib.model.DeviceState
import com.young.sample.adblib.model.ForwardEntry
import com.young.sample.adblib.transport.TcpTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * ADB Host 级服务。
 *
 * 与 ADB Server（127.0.0.1:5555）直接通信，管理设备列表和端口转发。
 * 注意：每个方法使用独立的 TCP 连接，在方法结束时关闭。
 * host:transport 后无法再发 host 命令，因此每个操作使用短连接。
 */
class HostServices(
    private val host: String = "127.0.0.1",
    private val port: Int = 5555,
    private val connectTimeoutMs: Long = 5000,
    private val readTimeoutMs: Long = 30000
) {

    /**
     * 获取 ADB Server 版本号。
     * host:version 返回 4 字节 hex 字符串（无 OKAY/FAIL 前缀）。
     */
    suspend fun getVersion(): Int = withTransport { stream ->
        writeRequest(stream, "host:version")
        // host:version 特殊：直接返回 4 字节 hex
        val versionHex = readExact(stream.input, 4)
        try {
            versionHex.toString(Charsets.UTF_8).toInt(16)
        } catch (e: NumberFormatException) {
            throw AdbException.ProtocolError("Invalid version response: ${versionHex.toString(Charsets.UTF_8)}")
        }
    }

    /**
     * 列出已连接设备。
     * 格式: "serial\tstate\nserial\tstate\n"
     */
    suspend fun getDevices(): List<AdbDevice> = withTransport { stream ->
        writeRequest(stream, "host:devices")
        readOkay(stream)
        val payload = readPayload(stream)
        parseDeviceList(payload)
    }

    /**
     * 持续追踪设备变化（长连接）。
     * 每次设备状态变化推送新列表，连接保持直到 Flow 结束。
     */
    fun trackDevices(): Flow<List<AdbDevice>> = flow {
        val sock = createSocket()
        try {
            val stream = SocketStream(sock)
            writeRequest(stream, "host:track-devices")
            readOkay(stream)

            while (true) {
                val payload = readPayload(stream)
                if (payload.isEmpty()) break // 连接断开
                emit(parseDeviceList(payload))
            }
        } finally {
            try { sock.close() } catch (_: Exception) {}
        }
    }

    /**
     * 选择目标设备（切换 transport）。
     * 后续操作由 AdbSession 在新连接上完成。
     */
    suspend fun selectDevice(serial: String) = withTransport { stream ->
        writeRequest(stream, "host:transport:$serial")
        readOkay(stream)
    }

    /**
     * 获取设备序列号。
     */
    suspend fun getSerialNo(serial: String): String = withTransport { stream ->
        writeRequest(stream, "host-serial:$serial:get-serialno")
        readOkay(stream)
        String(readPayload(stream), Charsets.UTF_8).trim()
    }

    /**
     * 获取设备状态。
     */
    suspend fun getState(serial: String): String = withTransport { stream ->
        writeRequest(stream, "host-serial:$serial:get-state")
        readOkay(stream)
        String(readPayload(stream), Charsets.UTF_8).trim()
    }

    /**
     * 设置端口转发。
     */
    suspend fun forward(local: String, remote: String) = withTransport { stream ->
        writeRequest(stream, "host:forward:$local;$remote")
        readOkay(stream)
    }

    /**
     * 设置反向端口转发。
     */
    suspend fun reverse(remote: String, local: String) = withTransport { stream ->
        writeRequest(stream, "host:reverse:forward:$remote;$local")
        readOkay(stream)
    }

    /**
     * 列出所有转发。
     */
    suspend fun listForwards(): List<ForwardEntry> = withTransport { stream ->
        writeRequest(stream, "host:list-forward")
        readOkay(stream)
        val payload = readPayload(stream)
        parseForwardList(payload)
    }

    /**
     * 移除所有转发。
     */
    suspend fun killForwardAll() = withTransport { stream ->
        writeRequest(stream, "host:killforward-all")
        readOkay(stream)
    }

    /**
     * 终止 ADB Server。
     */
    suspend fun kill() = withTransport { stream ->
        writeRequest(stream, "host:kill")
        // host:kill 没有响应
    }

    // ---- Internal helpers ----

    private data class SocketStream(val sock: Socket) {
        val input: DataInputStream get() = DataInputStream(sock.getInputStream())
        val output: DataOutputStream get() = DataOutputStream(sock.getOutputStream())
    }

    private suspend fun <T> withTransport(block: suspend (SocketStream) -> T): T =
        withContext(Dispatchers.IO) {
            val sock = createSocket()
            try {
                block(SocketStream(sock))
            } finally {
                try { sock.close() } catch (_: Exception) {}
            }
        }

    private fun createSocket(): Socket {
        val sock = Socket()
        try {
            sock.connect(InetSocketAddress(host, port), connectTimeoutMs.toInt())
            sock.soTimeout = readTimeoutMs.toInt()
            return sock
        } catch (e: Exception) {
            try { sock.close() } catch (_: Exception) {}
            throw AdbException.ConnectionFailed(host, port, e)
        }
    }

    private fun writeRequest(stream: SocketStream, service: String) {
        val hexLen = "%04x".format(service.length)
        val request = (hexLen + service).toByteArray(Charsets.UTF_8)
        stream.output.write(request)
        stream.output.flush()
    }

    private fun readOkay(stream: SocketStream) {
        val response = readExact(stream.input, 4)
        val responseStr = response.toString(Charsets.UTF_8)
        if (responseStr == "OKAY") return
        if (responseStr == "FAIL") {
            val errorMsg = readPayload(stream)
            throw AdbException.ProtocolError("ADB request failed: ${String(errorMsg, Charsets.UTF_8)}")
        }
        throw AdbException.ProtocolError("Expected OKAY/FAIL, got: '$responseStr'")
    }

    private fun readPayload(stream: SocketStream): ByteArray {
        val hexLen = readExact(stream.input, 4)
        val len = try {
            hexLen.toString(Charsets.UTF_8).toInt(16)
        } catch (e: NumberFormatException) {
            throw AdbException.ProtocolError("Invalid payload length: ${hexLen.toString(Charsets.UTF_8)}")
        }
        if (len == 0) return ByteArray(0)
        return readExact(stream.input, len)
    }

    private fun readExact(input: DataInputStream, size: Int): ByteArray {
        val buffer = ByteArray(size)
        input.readFully(buffer)
        return buffer
    }

    private fun parseDeviceList(payload: ByteArray): List<AdbDevice> {
        val text = String(payload, Charsets.UTF_8).trim()
        if (text.isEmpty()) return emptyList()

        return text.lines().map { line ->
            val parts = line.split("\t")
            val serial = parts.getOrNull(0) ?: ""
            val stateStr = parts.getOrNull(1) ?: "unknown"
            AdbDevice(
                serial = serial,
                state = DeviceState.fromString(stateStr)
            )
        }
    }

    private fun parseForwardList(payload: ByteArray): List<ForwardEntry> {
        val text = String(payload, Charsets.UTF_8).trim()
        if (text.isEmpty()) return emptyList()

        return text.lines().map { line ->
            val parts = line.split(" ")
            ForwardEntry(
                serial = parts.getOrNull(0) ?: "",
                local = parts.getOrNull(1) ?: "",
                remote = parts.getOrNull(2) ?: ""
            )
        }
    }
}
