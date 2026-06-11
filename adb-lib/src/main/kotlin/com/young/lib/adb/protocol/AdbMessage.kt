package com.young.lib.adb.protocol

import com.young.lib.adb.AdbCommand
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * ADB 24 字节消息头的封装。
 * 来源: AOSP protocol.txt — struct message
 *
 * 偏移  0        4        8        12       16       20
 *     ┌────────┬────────┬────────┬────────┬────────┬────────┐
 *     │command │  arg0  │  arg1  │length  │crc32   │ magic  │
 *     │ 4B LE  │ 4B LE  │ 4B LE  │ 4B LE  │ 4B LE  │ 4B LE  │
 *     └────────┴────────┴────────┴────────┴────────┴────────┘
 *                                     magic = command ^ 0xFFFFFFFF
 */
object AdbMessage {
    const val SIZE = 24

    /**
     * 构造 24 字节消息头。
     * 自 version 0x01000001 起，data_crc32 可设为 0。
     */
    fun create(
        command: Int,
        arg0: Int = 0,
        arg1: Int = 0,
        dataLength: Int = 0,
        dataCrc32: Int = 0
    ): ByteArray {
        val magic = command xor 0xffffffff.toInt()
        val buffer = ByteBuffer.allocate(SIZE).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            putInt(command)
            putInt(arg0)
            putInt(arg1)
            putInt(dataLength)
            putInt(dataCrc32)
            putInt(magic)
        }
        return buffer.array()
    }

    /**
     * 解析 24 字节为 ParsedMessage。
     * @throws IllegalArgumentException 如果数据长度 < 24 或 magic 校验失败
     */
    fun parse(bytes: ByteArray): ParsedMessage {
        require(bytes.size >= SIZE) { "Message header must be at least $SIZE bytes" }

        val buffer = ByteBuffer.wrap(bytes).apply {
            order(ByteOrder.LITTLE_ENDIAN)
        }

        val command = buffer.getInt()
        val arg0 = buffer.getInt()
        val arg1 = buffer.getInt()
        val dataLength = buffer.getInt()
        val dataCrc32 = buffer.getInt()
        val magic = buffer.getInt()

        // magic 校验
        val expectedMagic = command xor 0xffffffff.toInt()
        if (magic != expectedMagic) {
            throw IllegalArgumentException(
                "Magic mismatch: expected 0x${expectedMagic.toUInt().toString(16)}, " +
                    "got 0x${magic.toUInt().toString(16)}"
            )
        }

        return ParsedMessage(command, arg0, arg1, dataLength, dataCrc32, magic)
    }

    /**
     * 从 header bytes 中提取 data_length（不完整解析整个 header）。
     * 用于 read() 中先读 header、再读 payload 的两步读取模式。
     */
    fun extractDataLength(bytes: ByteArray): Int {
        return ByteBuffer.wrap(bytes, 12, 4).order(ByteOrder.LITTLE_ENDIAN).getInt()
    }

    /**
     * 验证 payload 的 CRC32（仅当 non-zero 时校验）。
     * 现代 ADB (version >= 0x01000001) 不强制检验 CRC32。
     */
    fun verifyCrc32(payload: ByteArray, expectedCrc32: Int): Boolean {
        if (expectedCrc32 == 0) return true
        val actual = crc32(payload)
        return actual == expectedCrc32
    }

    private fun crc32(data: ByteArray): Int {
        var crc = 0xffffffffL
        for (b in data) {
            crc = crc xor (b.toLong() and 0xff)
            repeat(8) {
                crc = if ((crc and 1L) != 0L) {
                    (crc shr 1) xor 0xedb88320L
                } else {
                    crc shr 1
                }
            }
        }
        return ((crc xor 0xffffffffL).toInt())
    }
}

/**
 * 解析后的消息头数据结构
 */
data class ParsedMessage(
    val command: Int,
    val arg0: Int,
    val arg1: Int,
    val dataLength: Int,
    val dataCrc32: Int,
    val magic: Int
) {
    /** 是否为 A_CNXN 消息 */
    val isConnect: Boolean get() = command == AdbCommand.CNXN

    /** 是否为 A_AUTH 消息 */
    val isAuth: Boolean get() = command == AdbCommand.AUTH

    /** 是否为 A_OPEN 消息 */
    val isOpen: Boolean get() = command == AdbCommand.OPEN

    /** 是否为 A_OKAY 消息 */
    val isOkay: Boolean get() = command == AdbCommand.OKAY

    /** 是否为 A_CLSE 消息 */
    val isClose: Boolean get() = command == AdbCommand.CLSE

    /** 是否为 A_WRTE 消息 */
    val isWrite: Boolean get() = command == AdbCommand.WRTE

    /** 是否为 A_SYNC 消息（内部命令，不应在线路上出现） */
    val isSync: Boolean get() = command == AdbCommand.SYNC
}
