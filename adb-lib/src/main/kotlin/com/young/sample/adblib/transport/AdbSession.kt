package com.young.sample.adblib.transport

import com.young.sample.adblib.AdbCommand
import com.young.sample.adblib.model.AdbException
import com.young.sample.adblib.protocol.AdbPacket
import com.young.sample.adblib.protocol.toAdbPacket
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * ADB 设备会话。
 *
 * 一个 AdbSession 对应一条经过 host:transport 选择了目标设备的连接。
 * 在 Smart Socket 协议中，host:transport 后的所有包都会直接路由到 adbd。
 */
class AdbSession(
    private val transport: Transport
) : AutoCloseable {

    private val localIdCounter = AtomicInteger(1)
    private val streams = ConcurrentHashMap<Int, AdbStream>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 底层 read loop 错误通知
    private val _errorFlow = MutableSharedFlow<Throwable>(extraBufferCapacity = 1)
    val errorFlow: SharedFlow<Throwable> = _errorFlow

    private var readLoopJob: Job? = null
    private var closed = false

    /**
     * 启动底层 read loop。
     * 持续从 transport 读取包，按 remote_id 分发到对应的 AdbStream。
     */
    fun startReadLoop() {
        if (readLoopJob != null) return
        readLoopJob = scope.launch {
            try {
                while (isActive && !closed) {
                    val packet = transport.read()
                    handlePacket(packet)
                }
            } catch (e: CancellationException) {
                // 正常取消
            } catch (e: Exception) {
                if (!closed) {
                    _errorFlow.tryEmit(e)
                }
            }
        }
    }

    private suspend fun handlePacket(packet: AdbPacket) {
        // 根据 remote_id（实际上是 arg1 中的 remote_id）分发给对应 stream
        // OPEN 和 OPEN 的 OKAY 响应由 openService 处理
        when (packet.command) {
            AdbCommand.CNXN -> {
                // adbd 的连接通知，忽略
            }
            AdbCommand.WRTE, AdbCommand.OKAY, AdbCommand.CLSE -> {
                // 这些包携带 remote_id 在 arg1 中
                val streamRemoteId = packet.arg1
                val stream = streams[streamRemoteId]
                if (stream != null) {
                    stream.handlePacket(packet)
                } else {
                    // 未知 stream ID，忽略
                }
            }
            else -> {
                // 忽略其他包
            }
        }
    }

    /**
     * 打开一个服务流。
     *
     * @param service 服务名，如 "shell:v2,raw:ls", "sync:", "framebuffer:"
     * @return AdbStream 实例
     */
    suspend fun openService(service: String): AdbStream {
        val localId = localIdCounter.getAndIncrement()
        val packet = AdbPacket.open(localId, service)
        transport.write(packet)

        // 读取响应
        val response = transport.read()
        return when (response.command) {
            AdbCommand.OKAY -> {
                val remoteId = response.arg1
                val stream = AdbStream(localId, remoteId, transport)
                streams[localId] = stream
                stream
            }
            AdbCommand.CLSE -> {
                throw AdbException.ServiceError(service, "Service rejected by device")
            }
            else -> {
                throw AdbException.ProtocolError(
                    "Expected OKAY or CLSE for OPEN, got 0x${response.command.toUInt().toString(16)}"
                )
            }
        }
    }

    /**
     * 移除已关闭的 stream。
     */
    fun removeStream(localId: Int) {
        streams.remove(localId)
    }

    override fun close() {
        if (closed) return
        closed = true
        readLoopJob?.cancel()
        scope.cancel()
        streams.values.forEach { it.close() }
        streams.clear()
        transport.close()
    }
}
