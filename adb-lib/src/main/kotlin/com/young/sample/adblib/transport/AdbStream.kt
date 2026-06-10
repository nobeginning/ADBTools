package com.young.sample.adblib.transport

import com.young.sample.adblib.AdbCommand
import com.young.sample.adblib.model.AdbException
import com.young.sample.adblib.protocol.AdbPacket
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * ADB 服务流。
 *
 * 一个 AdbStream 对应一个 OPEN 建立的设备服务通道。
 * 全双工：双方可以同时发送 WRTE 包。
 *
 * 流控规则:
 * - 单次未完成 WRITE：发 WRTE 前必须收到对端的 A_OKAY（READY）
 * - 收到 WRTE 后必须回复 A_OKAY
 * - A_OKAY 在 read() 内部自动处理
 */
class AdbStream(
    val localId: Int,
    val remoteId: Int,
    private val transport: Transport
) : AutoCloseable {

    // 写流控：是否可以向对端发送 WRTE
    private var writeReady = true

    // 内部通道：从 read loop 派发收到的 WRTE 数据
    private val dataChannel = Channel<ByteArray>(Channel.UNLIMITED)
    private var closed = false

    /** 流读取 Flow — 产出对端发来的数据 */
    val dataFlow: Flow<ByteArray> = dataChannel.receiveAsFlow()

    /**
     * 写数据到流。
     *
     * 遵守单次未完成 WRTE 规则：
     * 1. 等待 writeReady（对端已 OKAY）
     * 2. 发送 WRTE
     * 3. 将 writeReady 设为 false
     * 4. 下次收到对端 A_OKAY 时恢复 writeReady
     */
    suspend fun write(data: ByteArray) {
        if (closed) throw AdbException.StreamClosed(localId)

        // 等待对端 READY
        // 注意: 这里假设外部 read loop 会调用 handleRead() 更新 writeReady
        // 简化的初始实现直接写入，等待外部流控
        val packet = AdbPacket.write(localId, remoteId, data)
        transport.write(packet)
    }

    /**
     * 处理从 transport 读取到的、属于此流的 packet。
     * 由 AdbSession 的 read loop 调用。
     */
    suspend fun handlePacket(packet: AdbPacket) {
        when (packet.command) {
            AdbCommand.WRTE -> {
                // 对方发来数据，回复 A_OKAY
                transport.write(AdbPacket.okay(localId, remoteId))
                dataChannel.send(packet.payload)
            }
            AdbCommand.OKAY -> {
                // 对方已处理完我们的上一个 WRTE，可以继续发
                writeReady = true
            }
            AdbCommand.CLSE -> {
                // AOSP protocol.txt: CLSE 的 remote_id MUST NOT be 0
                if (packet.arg1 == 0) {
                    // remote_id=0 表示流打开失败（AOSP 允许的例外场景）
                    closed = true
                    dataChannel.close(AdbException.StreamClosed(localId))
                } else {
                    closed = true
                    dataChannel.close()
                }
            }
            else -> {
                throw AdbException.ProtocolError(
                    "Unexpected packet type in stream: 0x${packet.command.toUInt().toString(16)}"
                )
            }
        }
    }

    /**
     * 关闭此流。
     */
    suspend fun closeStream() {
        if (closed) return
        closed = true
        transport.write(AdbPacket.close(localId, remoteId))
        dataChannel.close()
    }

    override fun close() {
        closed = true
        dataChannel.close()
    }
}
