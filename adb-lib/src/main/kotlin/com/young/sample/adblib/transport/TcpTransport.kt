package com.young.sample.adblib.transport

import com.young.sample.adblib.AdbCommand
import com.young.sample.adblib.model.AdbException
import com.young.sample.adblib.protocol.AdbMessage
import com.young.sample.adblib.protocol.AdbPacket
import com.young.sample.adblib.protocol.toAdbPacket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * TCP 传输层实现。
 *
 * 提供协程友好的 ADB 协议 read/write，基于 java.net.Socket。
 *
 * @param host 目标主机地址
 * @param port 目标端口
 * @param connectTimeoutMs TCP 连接超时
 * @param readTimeoutMs socket read 超时
 */
class TcpTransport(
    private val host: String,
    private val port: Int,
    private val connectTimeoutMs: Long = 5000,
    private val readTimeoutMs: Long = 30000
) : Transport {

    private var socket: Socket? = null
    private var inputStream: DataInputStream? = null
    private var outputStream: DataOutputStream? = null
    private var connected = false

    override val isConnected: Boolean get() = connected

    /** 协商后的最大 payload 大小。初始为本地最大值，收到对端 CNXN 后取 min */
    override var maxPayload: Int = AdbCommand.MAX_PAYLOAD
        private set

    /**
     * 根据对端 CNXN 响应协商 maxdata。
     * AOSP protocol.txt: 双方取 min(my_maxdata, peer_maxdata) 作为会话上限。
     */
    override fun negotiateMaxPayload(peerMaxdata: Int) {
        maxPayload = minOf(AdbCommand.MAX_PAYLOAD, peerMaxdata)
    }

    /**
     * 建立 TCP 连接到 ADB Server。
     * 连接建立后，Socket 的超时、缓冲区等参数也会被设置。
     */
    suspend fun connect() {
        withContext(Dispatchers.IO) {
            try {
                val sock = Socket()
                sock.connect(InetSocketAddress(host, port), connectTimeoutMs.toInt())
                sock.soTimeout = readTimeoutMs.toInt()
                sock.tcpNoDelay = true

                socket = sock
                inputStream = DataInputStream(sock.getInputStream())
                outputStream = DataOutputStream(sock.getOutputStream())
                connected = true
            } catch (e: Exception) {
                close()
                throw AdbException.ConnectionFailed(host, port, e)
            }
        }
    }

    /**
     * 读取一个完整 packet。
     *
     * 分两步读取:
     * 1. 先读 24 字节 header
     * 2. 从 header 提取 data_length，再读 payload
     */
    override suspend fun read(): AdbPacket = withContext(Dispatchers.IO) {
        val stream = inputStream
            ?: throw AdbException.ProtocolError("Not connected")

        try {
            // 步骤 1: 读取 24 字节 header
            val header = ByteArray(AdbMessage.SIZE)
            stream.readFully(header)

            // 步骤 2: 校验 magic
            val parsed = try {
                AdbMessage.parse(header)
            } catch (e: IllegalArgumentException) {
                throw AdbException.ProtocolError("Header magic check failed: ${e.message}")
            }

            // A_SYNC 是内部命令，不应在线路上出现，静默丢弃并重读
            if (parsed.isSync) {
                return@withContext read()
            }

            // 步骤 3: 读取 payload
            val payload = if (parsed.dataLength > 0) {
                val data = ByteArray(parsed.dataLength)
                stream.readFully(data)
                // CRC32 校验（仅非零时校验）
                if (!AdbMessage.verifyCrc32(data, parsed.dataCrc32)) {
                    throw AdbException.ProtocolError("CRC32 mismatch")
                }
                data
            } else {
                ByteArray(0)
            }

            AdbPacket(
                command = parsed.command,
                arg0 = parsed.arg0,
                arg1 = parsed.arg1,
                payload = payload
            )
        } catch (e: SocketTimeoutException) {
            throw AdbException.Timeout("Read timed out after ${readTimeoutMs}ms")
        } catch (e: java.io.EOFException) {
            connected = false
            throw AdbException.ProtocolError("Connection closed by peer")
        }
    }

    /**
     * 写入一个完整 packet。
     *
     * 写操作由 Mutex 保护，防止多个协程同时写入导致数据交错。
     */
    override suspend fun write(packet: AdbPacket) = withContext(Dispatchers.IO) {
        val stream = outputStream
            ?: throw AdbException.ProtocolError("Not connected")

        try {
            val data = packet.toByteArray()
            stream.write(data)
            stream.flush()
        } catch (e: Exception) {
            connected = false
            throw AdbException.ProtocolError("Write failed: ${e.message}")
        }
    }

    override fun close() {
        try {
            inputStream?.close()
        } catch (_: Exception) {
        }
        try {
            outputStream?.close()
        } catch (_: Exception) {
        }
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        inputStream = null
        outputStream = null
        socket = null
        connected = false
    }
}
