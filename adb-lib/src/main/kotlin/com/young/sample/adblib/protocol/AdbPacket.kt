package com.young.sample.adblib.protocol

import com.young.sample.adblib.AdbCommand
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 完整的 ADB 传输层数据包（24 字节 header + 可变长度 payload）。
 *
 * 对应 AOSP 协议栈中的完整消息：header + payload 的组合。
 */
data class AdbPacket(
    val command: Int,
    val arg0: Int = 0,
    val arg1: Int = 0,
    val payload: ByteArray = ByteArray(0)
) {
    val dataLength: Int get() = payload.size

    /** payload 的字符串表示（用于调试和文本类服务） */
    val payloadAsString: String get() = if (payload.isEmpty()) "" else String(payload)

    // ByteArray 在 data class 中的特殊处理
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AdbPacket
        return command == other.command &&
                arg0 == other.arg0 &&
                arg1 == other.arg1 &&
                payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = command
        result = 31 * result + arg0
        result = 31 * result + arg1
        result = 31 * result + payload.contentHashCode()
        return result
    }

    /** 序列化为 byte array：24B header + payload */
    fun toByteArray(): ByteArray {
        val header = AdbMessage.create(command, arg0, arg1, payload.size)
        return if (payload.isEmpty()) {
            header
        } else {
            ByteBuffer.allocate(AdbMessage.SIZE + payload.size).apply {
                put(header)
                put(payload)
            }.array()
        }
    }

    /** A_CNXN 辅助构造 */
    companion object {
        fun connect(version: Int = AdbCommand.VERSION, maxdata: Int = AdbCommand.MAX_PAYLOAD, banner: String): AdbPacket {
            return AdbPacket(
                command = AdbCommand.CNXN,
                arg0 = version,
                arg1 = maxdata,
                payload = banner.toByteArray()
            )
        }

        /** A_CNXN — host 端标准 banner */
        fun hostConnect(): AdbPacket = connect(banner = "host::")

        /** A_AUTH 辅助构造 */
        fun auth(type: Int, data: ByteArray): AdbPacket {
            return AdbPacket(
                command = AdbCommand.AUTH,
                arg0 = type,
                payload = data
            )
        }

        /** A_OPEN 辅助构造 */
        fun open(localId: Int, destination: String): AdbPacket {
            return AdbPacket(
                command = AdbCommand.OPEN,
                arg0 = localId,
                payload = destination.toByteArray()
            )
        }

        /** A_OKAY 辅助构造 */
        fun okay(localId: Int, remoteId: Int): AdbPacket {
            return AdbPacket(command = AdbCommand.OKAY, arg0 = localId, arg1 = remoteId)
        }

        /** A_WRTE 辅助构造 */
        fun write(localId: Int, remoteId: Int, data: ByteArray): AdbPacket {
            return AdbPacket(command = AdbCommand.WRTE, arg0 = localId, arg1 = remoteId, payload = data)
        }

        /** A_CLSE 辅助构造 */
        fun close(localId: Int, remoteId: Int): AdbPacket {
            return AdbPacket(command = AdbCommand.CLSE, arg0 = localId, arg1 = remoteId)
        }
    }
}

/**
 * 从字节数组解析 AdbPacket。
 * 输入必须为完整的 24B header + 可选 payload。
 */
fun ByteArray.toAdbPacket(): AdbPacket {
    require(this.size >= AdbMessage.SIZE) {
        "Invalid packet: expected at least ${AdbMessage.SIZE} bytes, got ${this.size}"
    }

    val parsed = AdbMessage.parse(this)
    val payloadOffset = AdbMessage.SIZE

    val payload = if (parsed.dataLength > 0) {
        require(this.size >= payloadOffset + parsed.dataLength) {
            "Incomplete packet: header claims ${parsed.dataLength} payload bytes, " +
                "but only ${this.size - payloadOffset} available"
        }
        this.copyOfRange(payloadOffset, payloadOffset + parsed.dataLength)
    } else {
        ByteArray(0)
    }

    return AdbPacket(
        command = parsed.command,
        arg0 = parsed.arg0,
        arg1 = parsed.arg1,
        payload = payload
    )
}
