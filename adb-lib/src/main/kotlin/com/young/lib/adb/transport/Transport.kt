package com.young.lib.adb.transport

import com.young.lib.adb.protocol.AdbPacket
import java.io.Closeable

/**
 * 传输层接口 — 负责 TCP socket 管理和数据包的可靠收发。
 *
 * 设计原则:
 * - 最小接口：仅暴露 read/write + 连接状态
 * - 协程友好：所有方法都是 suspend 函数
 * - 与协议层解耦：不关心 packet 内容，只负责传输
 */
interface Transport : Closeable {
    /** 读取一个完整 packet (header + payload) */
    suspend fun read(): AdbPacket

    /** 写入一个完整 packet */
    suspend fun write(packet: AdbPacket)

    /** 连接是否已建立 */
    val isConnected: Boolean

    /** 协商后的最大 payload 大小（默认 1MB，与对端 CNXN 协商后取 min） */
    val maxPayload: Int get() = 1024 * 1024

    /** 根据对端 CNXN 的 maxdata 协商最大 payload */
    fun negotiateMaxPayload(peerMaxdata: Int) {
        // 默认空实现，子类可覆盖
    }
}
