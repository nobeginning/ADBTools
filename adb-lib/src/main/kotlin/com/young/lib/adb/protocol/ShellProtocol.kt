package com.young.lib.adb.protocol

import java.io.ByteArrayOutputStream

/**
 * Shell V2 协议 — 基于单字节类型 ID 的多路复用分包。
 *
 * 当设备支持 `kFeatureShell2` 时使用此协议。
 * 包格式: [1B: id] [varint: len] [payload]
 *
 * ID 类型:
 *   0 = stdin  (App -> Device)
 *   1 = stdout (Device -> App)
 *   2 = stderr (Device -> App)
 *   3 = exit   (Device -> App)
 */
sealed class ShellData {
    data class Stdout(val data: ByteArray) : ShellData() {
        val text: String get() = String(data)
    }

    data class Stderr(val data: ByteArray) : ShellData() {
        val text: String get() = String(data)
    }

    data class Exit(val code: Int) : ShellData()
}

/**
 * Shell V2 协议编解码器。
 */
object ShellProtocol {
    private const val ID_STDIN: Byte = 0
    private const val ID_STDOUT: Byte = 1
    private const val ID_STDERR: Byte = 2
    private const val ID_EXIT: Byte = 3

    /**
     * 从 WRTE payload 中解析 Shell V2 子包。
     *
     * ⚠ 支持两种长度编码格式：
     *   - 标准 AOSP: [id:1B][length:varint][payload]
     *   - MIUI adbd: [id:1B][length:4B LE uint32][payload]
     *   自动检测：当 varint 解码为 0 且第二字节为 0x00 时，尝试 4B LE 格式。
     *
     * @return Pair(ShellData, consumedBytes) 或 null（数据不完整/未知类型）
     */
    private fun tryParsePacket(data: ByteArray): Pair<ShellData, Int>? {
        if (data.size < 2) return null

        val type = data[0]
        if (type != ID_STDOUT && type != ID_STDERR && type != ID_EXIT) return null

        // 同时尝试标准 varint 和 MIUI 4B LE 两种长度编码
        val (varintLen, varintBytes) = decodeVarint(data, 1)

        val leLen = if (data.size >= 5) {
            ((data[4].toInt() and 0xff) shl 24) or
            ((data[3].toInt() and 0xff) shl 16) or
            ((data[2].toInt() and 0xff) shl 8) or
            (data[1].toInt() and 0xff)
        } else -1

        // 判断哪种格式更合理
        val varintValid = varintLen >= 0 && varintBytes > 0 &&
            data.size >= 1 + varintBytes + varintLen
        val leValid = leLen in 1..(256 * 1024) && data.size >= 5 + leLen

        val (payloadLen, headerSize) = when {
            // 两者都有效：选 payload 不以 0x00 开头的（MIUI 的 4B LE 长度后紧跟文本）
            varintValid && leValid -> {
                val varintStartsNull = varintLen > 0 &&
                    data[1 + varintBytes] == 0.toByte()
                val leStartsNull = leLen > 0 && data[5] == 0.toByte()
                if (varintStartsNull && !leStartsNull) {
                    Pair(leLen, 4)
                } else if (varintLen == 0 && data[1] == 0.toByte() && leLen > 0) {
                    // varint 解出 0 + data[1]=0 → 典型 MIUI 大 payload
                    Pair(leLen, 4)
                } else {
                    Pair(varintLen, varintBytes)
                }
            }
            varintValid -> Pair(varintLen, varintBytes)
            leValid -> {
                if (leLen > 0 && data.size >= 5 && data[5] != 0.toByte()) {
                    Pair(leLen, 4)
                } else {
                    return null
                }
            }
            else -> return null
        }

        val payloadStart = 1 + headerSize
        val payloadEnd = payloadStart + payloadLen
        if (data.size < payloadEnd) return null  // 不完整

        val payload = data.copyOfRange(payloadStart, payloadEnd)
        val packet = when (type) {
            ID_STDOUT -> ShellData.Stdout(payload)
            ID_STDERR -> ShellData.Stderr(payload)
            ID_EXIT -> ShellData.Exit(decodeExitCode(payload))
            else -> return null
        }
        return Pair(packet, payloadEnd)
    }

    /**
     * 从 WRTE payload 中解析 Shell V2 子包（公开 API，保持向后兼容）。
     */
    fun parsePacket(data: ByteArray): ShellData? {
        return tryParsePacket(data)?.first
    }

    /**
     * 解析 Shell V2 包流（一个 WRTE 可能包含多个 Shell V2 包）。
     */
    fun parsePackets(data: ByteArray): List<ShellData> {
        val result = mutableListOf<ShellData>()
        var offset = 0
        while (offset < data.size) {
            val (packet, consumed) = tryParsePacket(data.copyOfRange(offset, data.size))
                ?: break // 数据不完整或未知类型
            result.add(packet)
            offset += consumed
        }
        return result
    }

    /**
     * 构造 stdin 子包。
     */
    fun buildStdin(data: ByteArray): ByteArray {
        val lenBuffer = encodeVarint(data.size)
        return ByteArrayOutputStream(data.size + 1 + lenBuffer.size).apply {
            write(ID_STDIN.toInt())
            write(lenBuffer)
            write(data)
        }.toByteArray()
    }

    // ---- Private helpers ----

    /** Varint 解码，返回 (value, bytesRead) */
    private fun decodeVarint(data: ByteArray, offset: Int): Pair<Int, Int> {
        if (offset >= data.size) return Pair(-1, -1)

        var result = 0
        var shift = 0
        var pos = offset

        while (pos < data.size) {
            val b = data[pos].toInt() and 0xff
            result = result or ((b and 0x7f) shl shift)
            shift += 7
            pos++
            if (b and 0x80 == 0) {
                return Pair(result, pos - offset)
            }
        }
        return Pair(-1, -1) // 不完整
    }

    /** Varint 编码 */
    private fun encodeVarint(value: Int): ByteArray {
        var v = value
        val result = ByteArrayOutputStream()
        while (v >= 0x80) {
            result.write((v and 0x7f) or 0x80)
            v = v shr 7
        }
        result.write(v and 0x7f)
        return result.toByteArray()
    }

    /** 从 exit payload 解码退出码 */
    private fun decodeExitCode(payload: ByteArray): Int {
        if (payload.isEmpty()) return 0
        // exit 包 payload 是 varint 编码的退出码
        val (code, _) = decodeVarint(payload, 0)
        return if (code < 0) 0 else code
    }
}
