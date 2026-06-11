package com.young.lib.adb.transport

import android.util.Log
import com.young.lib.adb.AdbCommand
import com.young.lib.adb.model.AdbException
import com.young.lib.adb.protocol.AdbPacket
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * ADB 设备会话。
 *
 * 一个 AdbSession 对应一条经过认证的 adbd 连接。
 * 读循环持续从 transport 读取包，按需分发给各 AdbStream。
 *
 * 并发安全: openService() 不直接调用 transport.read()，
 * 而是注册 CompletableDeferred，由读循环统一派发 OPEN 响应。
 */
class AdbSession(
    private val transport: Transport
) : AutoCloseable {

    companion object {
        private const val TAG = "ADB-Session"
    }

    private val localIdCounter = AtomicInteger(1)
    private val streams = ConcurrentHashMap<Int, AdbStream>()

    /** 等待 OPEN 响应的 deferred，key 为发起 OPEN 时的 local_id */
    private val pendingOpens = ConcurrentHashMap<Int, CompletableDeferred<AdbPacket>>()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 底层 read loop 错误通知
    private val _errorFlow = MutableSharedFlow<Throwable>(extraBufferCapacity = 1)
    val errorFlow: SharedFlow<Throwable> = _errorFlow

    private var readLoopJob: Job? = null
    private var closed = false

    /**
     * 启动底层 read loop。
     * 持续从 transport 读取包，按 remote_id 分发到对应的 AdbStream。
     * 同时检查 pendingOpens，将 OPEN 响应交给等待的 openService() 协程。
     */
    fun startReadLoop() {
        if (readLoopJob != null) return
        readLoopJob = scope.launch {
            Log.d(TAG, "读循环已启动")
            try {
                while (isActive && !closed) {
                    try {
                        val packet = transport.read()
                        Log.d(TAG, "读循环收到: cmd=0x${packet.command.toUInt().toString(16)}, arg0=${packet.arg0}, arg1=${packet.arg1}")
                        handlePacket(packet)
                    } catch (e: AdbException.Timeout) {
                        // 空闲超时是正常的（用户没有操作），继续等待下一个包
                        if (closed) break
                        Log.d(TAG, "读循环空闲超时（${e.message}），继续等待...")
                        continue
                    }
                }
            } catch (e: CancellationException) {
                // 正常取消
            } catch (e: Exception) {
                if (!closed) {
                    Log.e(TAG, "读循环异常: ${e.message}", e)
                    _errorFlow.tryEmit(e)
                }
            }
            Log.d(TAG, "读循环已退出")
        }
    }

    private suspend fun handlePacket(packet: AdbPacket) {
        when (packet.command) {
            AdbCommand.CNXN -> {
                // adbd 的连接通知，忽略
                Log.d(TAG, "收到 CNXN（忽略）")
            }
            AdbCommand.OKAY, AdbCommand.CLSE -> {
                // 检查是否是等待中的 OPEN 响应
                // OPEN 响应中 arg1 = 客户端发起 OPEN 时的 local_id
                val pendingKey = packet.arg1
                val deferred = pendingOpens.remove(pendingKey)
                if (deferred != null) {
                    // 对于 OKAY 响应，立即创建 stream 并注册到 streams map
                    // 防止在 openService() 协程恢复前，WRTE 数据到达时找不到 stream
                    if (packet.command == AdbCommand.OKAY) {
                        val stream = AdbStream(pendingKey, packet.arg0, transport)
                        streams[pendingKey] = stream
                    }
                    Log.d(TAG, "派发 OPEN 响应: cmd=${packet.command}, localId=$pendingKey")
                    deferred.complete(packet)
                    return
                }
                // 否则分发给对应 stream
                dispatchToStream(packet)
            }
            AdbCommand.WRTE -> {
                dispatchToStream(packet)
            }
            else -> {
                Log.w(TAG, "未知包类型: 0x${packet.command.toUInt().toString(16)}")
            }
        }
    }

    private suspend fun dispatchToStream(packet: AdbPacket) {
        val streamLocalId = packet.arg1  // 对端发来的包，arg1 = 我们本地的 stream id
        val stream = streams[streamLocalId]
        if (stream != null) {
            stream.handlePacket(packet)
        } else {
            // 通常是因为 stream 已被关闭（如收到 CLSE 后移除），重复的 CLSE/WRTE 可安全忽略
            Log.d(TAG, "包已忽略 (localId=$streamLocalId 已移除): cmd=0x${packet.command.toUInt().toString(16)}")
        }
    }

    /**
     * 打开一个服务流。
     *
     * 并发安全: 不直接调用 transport.read()，而是通过 pendingOpens
     * 委托读循环派发 OPEN 响应，避免竞态条件。
     *
     * @param service 服务名，如 "shell:v2,raw:ls", "sync:", "framebuffer:"
     * @return AdbStream 实例
     */
    suspend fun openService(service: String): AdbStream {
        val localId = localIdCounter.getAndIncrement()
        Log.d(TAG, "打开服务: $service (localId=$localId)")

        // 注册等待器
        val deferred = CompletableDeferred<AdbPacket>()
        pendingOpens[localId] = deferred

        try {
            // 发送 OPEN
            val packet = AdbPacket.open(localId, service)
            transport.write(packet)
            Log.d(TAG, "OPEN 已发送: $service")

            // 等待读循环派发响应
            val response = deferred.await()
            Log.d(TAG, "收到 OPEN 响应: cmd=0x${response.command.toUInt().toString(16)}, arg0=${response.arg0}, arg1=${response.arg1}")

            return when (response.command) {
                AdbCommand.OKAY -> {
                    // 优先从 streams 获取已被 read loop 预创建的 stream（防 WRTE 竞态）
                    val stream = streams[localId]
                    if (stream != null) {
                        Log.i(TAG, "服务已打开: $service → localId=$localId, remoteId=${stream.remoteId}")
                        stream
                    } else {
                        // 兜底：stream 未被预创建（理论上不会发生）
                        val remoteId = response.arg0
                        Log.i(TAG, "服务已打开（兜底）: $service → localId=$localId, remoteId=$remoteId")
                        val newStream = AdbStream(localId, remoteId, transport)
                        streams[localId] = newStream
                        newStream
                    }
                }
                AdbCommand.CLSE -> {
                    Log.e(TAG, "服务被拒绝: $service (arg0=${response.arg0})")
                    throw AdbException.ServiceError(service, "Service rejected by device")
                }
                else -> {
                    Log.e(TAG, "OPEN 响应异常: 0x${response.command.toUInt().toString(16)}")
                    throw AdbException.ProtocolError(
                        "Expected OKAY or CLSE for OPEN, got 0x${response.command.toUInt().toString(16)}"
                    )
                }
            }
        } catch (e: Exception) {
            pendingOpens.remove(localId)
            // 清理可能被 read loop 预创建的 stream
            streams.remove(localId)?.close()
            throw e
        }
    }

    /**
     * 移除已关闭的 stream。
     */
    fun removeStream(localId: Int) {
        streams.remove(localId)
        Log.d(TAG, "Stream 已移除: localId=$localId")
    }

    override fun close() {
        if (closed) return
        closed = true
        Log.d(TAG, "关闭 Session")
        readLoopJob?.cancel()
        scope.cancel()
        streams.values.forEach { it.close() }
        streams.clear()
        // 取消所有等待中的 OPEN
        pendingOpens.values.forEach { it.cancel() }
        pendingOpens.clear()
        transport.close()
    }
}
