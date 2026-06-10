package com.young.sample.adblib.protocol

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
     * @return 解析出的 ShellData，如果数据不完整返回 null
     */
    fun parsePacket(data: ByteArray): ShellData? {
        if (data.isEmpty()) return null

        val type = data[0]
        val (len, readBytes) = decodeVarint(data, 1)
        if (len < 0 || readBytes < 0) return null

        val payloadStart = 1 + readBytes
        val payloadEnd = payloadStart + len

        // 数据不完整
        if (data.size < payloadEnd) return null

        val payload = data.copyOfRange(payloadStart, payloadEnd)
        return when (type) {
            ID_STDOUT -> ShellData.Stdout(payload)
            ID_STDERR -> ShellData.Stderr(payload)
            ID_EXIT -> ShellData.Exit(decodeExitCode(payload))
            else -> null // 未知类型（包括 stdin 从设备发来）
        }
    }

    /**
     * 解析 Shell V2 包流（一个 WRTE 可能包含多个 Shell V2 包）。
     */
    fun parsePackets(data: ByteArray): List<ShellData> {
        val result = mutableListOf<ShellData>()
        var offset = 0
        while (offset < data.size) {
            val packet = parsePacket(data.copyOfRange(offset, data.size))
                ?: break // 数据不完整，等待下一个 WRTE
            result.add(packet)
            // 计算此包占用的字节数
            val typeLen = 1
            val (protoLen, varintBytes) = decodeVarint(data, offset + 1)
            if (protoLen < 0 || varintBytes < 0) break
            offset += typeLen + varintBytes + protoLen
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
