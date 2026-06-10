package com.young.sample.adblib.service

import android.util.Log
import com.young.sample.adblib.model.AdbException
import com.young.sample.adblib.model.SyncEntry
import com.young.sample.adblib.transport.AdbSession
import com.young.sample.adblib.transport.AdbStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path

/**
 * 文件同步服务。在设备上执行文件推送、拉取、列表操作。
 *
 * 基于 ADB Sync 协议：通过 8 字节包 (4B ID + 4B LE length) + payload 通信。
 * 数据块最大 64KB。
 *
 * 注意: Sync 数据包直接以 WRTE payload 传输，不使用 Shell V2 协议。
 */
class SyncService(private val session: AdbSession) {

    companion object {
        private const val TAG = "ADB-Sync"
        private const val ID_LIST = "LIST"
        private const val ID_RECV = "RECV"
        private const val ID_SEND = "SEND"
        private const val ID_STAT = "STAT"
        private const val ID_QUIT = "QUIT"
        private const val ID_DATA = "DATA"
        private const val ID_DENT = "DENT"
        private const val ID_DONE = "DONE"
        private const val ID_OKAY = "OKAY"
        private const val ID_FAIL = "FAIL"
        private const val MAX_CHUNK_SIZE = 64 * 1024 // 64KB
    }

    /**
     * 推送文件到设备。
     */
    suspend fun push(localPath: Path, remotePath: String, mode: Int = 420) { // 0644 octal
        rxCumulative.reset()
        Log.i(TAG, "推送文件: ${localPath.toAbsolutePath()} → $remotePath")
        val stream = session.openService("sync:")
        try {
            val sendPath = "$remotePath,$mode"
            stream.write(buildSyncPacket(ID_SEND, sendPath.toByteArray(Charsets.UTF_8)))

            val fileData = withContext(Dispatchers.IO) {
                java.nio.file.Files.readAllBytes(localPath)
            }
            Log.d(TAG, "文件大小: ${fileData.size} bytes，开始分块传输...")
            var offset = 0
            while (offset < fileData.size) {
                val chunkSize = minOf(MAX_CHUNK_SIZE, fileData.size - offset)
                val chunk = fileData.copyOfRange(offset, offset + chunkSize)
                stream.write(buildSyncPacket(ID_DATA, chunk))
                offset += chunkSize
            }

            val timestamp = System.currentTimeMillis() / 1000
            stream.write(buildSyncPacket(ID_DONE, intToBytes(timestamp.toInt())))

            // OKAY/FAIL 通过 readSingleSyncPacket 读取
            val response = readSingleSyncPacket(stream)
            when (response.first) {
                ID_OKAY -> {
                    Log.i(TAG, "推送完成: $remotePath (${fileData.size} bytes)")
                }
                ID_FAIL -> {
                    val errorMsg = String(response.second, Charsets.UTF_8)
                    Log.e(TAG, "推送失败: $remotePath - $errorMsg")
                    throw AdbException.ServiceError("sync:push", errorMsg)
                }
                else -> {
                    Log.e(TAG, "推送异常响应: ${response.first}")
                    throw AdbException.ProtocolError("Unexpected sync response: ${response.first}")
                }
            }
        } finally {
            stream.write(buildSyncPacket(ID_QUIT, ByteArray(0)))
            stream.close()
            session.removeStream(stream.localId)
        }
    }

    /**
     * 从设备拉取文件。
     */
    suspend fun pull(remotePath: String, localPath: Path) {
        rxCumulative.reset()
        Log.i(TAG, "拉取文件: $remotePath → ${localPath.toAbsolutePath()}")
        val stream = session.openService("sync:")
        try {
            stream.write(buildSyncPacket(ID_RECV, remotePath.toByteArray(Charsets.UTF_8)))

            val output = ByteArrayOutputStream()
            var done = false
            while (!done) {
                for ((id, data) in readSyncPackets(stream)) {
                    when (id) {
                        ID_DATA -> output.write(data)
                        ID_DONE -> {
                            Log.i(TAG, "拉取完成: ${output.size()} bytes")
                            done = true
                            break
                        }
                        ID_FAIL -> {
                            val errorMsg = String(data, Charsets.UTF_8)
                            Log.e(TAG, "拉取失败: $remotePath - $errorMsg")
                            throw AdbException.ServiceError("sync:pull", errorMsg)
                        }
                        else -> {
                            Log.e(TAG, "拉取异常响应: $id")
                            throw AdbException.ProtocolError("Unexpected sync response: $id")
                        }
                    }
                }
            }

            withContext(Dispatchers.IO) {
                java.nio.file.Files.write(localPath, output.toByteArray())
            }
        } finally {
            stream.write(buildSyncPacket(ID_QUIT, ByteArray(0)))
            stream.close()
            session.removeStream(stream.localId)
        }
    }

    /**
     * 列出目录内容。
     */
    suspend fun listDir(remotePath: String): List<SyncEntry> {
        rxCumulative.reset()
        Log.i(TAG, "列出目录: $remotePath")
        val stream = session.openService("sync:")
        try {
            stream.write(buildSyncPacket(ID_LIST, remotePath.toByteArray(Charsets.UTF_8)))

            val entries = mutableListOf<SyncEntry>()
            var done = false
            while (!done) {
                for ((id, data) in readSyncPackets(stream)) {
                    when (id) {
                        ID_DENT -> {
                            entries.add(parseDent(data))
                        }
                        ID_DONE -> {
                            Log.i(TAG, "目录列表完成: ${entries.size} 项")
                            done = true
                            break
                        }
                        else -> {
                            Log.e(TAG, "listDir 异常响应: $id")
                            throw AdbException.ProtocolError("Unexpected sync response: $id")
                        }
                    }
                }
            }

            return entries
        } finally {
            stream.write(buildSyncPacket(ID_QUIT, ByteArray(0)))
            stream.close()
            session.removeStream(stream.localId)
        }
    }

    /**
     * 从设备拉取文件内容到内存（适合小文件如 XML、配置文件）。
     *
     * 与 [pull] 不同，此方法不写本地文件，直接返回字节数组。
     */
    suspend fun pullBytes(remotePath: String): ByteArray {
        rxCumulative.reset()
        Log.d(TAG, "拉取文件到内存: $remotePath")
        val stream = session.openService("sync:")
        try {
            stream.write(buildSyncPacket(ID_RECV, remotePath.toByteArray(Charsets.UTF_8)))

            val output = ByteArrayOutputStream()
            var done = false
            while (!done) {
                for ((id, data) in readSyncPackets(stream)) {
                    when (id) {
                        ID_DATA -> output.write(data)
                        ID_DONE -> {
                            Log.d(TAG, "拉取完成: ${output.size()} bytes")
                            done = true
                            break
                        }
                        ID_FAIL -> {
                            val errorMsg = String(data, Charsets.UTF_8)
                            Log.e(TAG, "拉取失败: $remotePath - $errorMsg")
                            throw AdbException.ServiceError("sync:pull", errorMsg)
                        }
                        else -> {
                            Log.e(TAG, "拉取异常响应: $id")
                            throw AdbException.ProtocolError("Unexpected sync response: $id")
                        }
                    }
                }
            }

            return output.toByteArray()
        } finally {
            stream.write(buildSyncPacket(ID_QUIT, ByteArray(0)))
            stream.close()
            session.removeStream(stream.localId)
        }
    }

    /**
     * 获取文件信息。
     */
    suspend fun stat(remotePath: String): SyncEntry {
        rxCumulative.reset()
        val stream = session.openService("sync:")
        try {
            stream.write(buildSyncPacket(ID_STAT, remotePath.toByteArray(Charsets.UTF_8)))

            val (id, data) = readSingleSyncPacket(stream)
            return when (id) {
                ID_STAT -> parseStat(data)
                ID_FAIL -> {
                    val errorMsg = String(data, Charsets.UTF_8)
                    throw AdbException.ServiceError("sync:stat", errorMsg)
                }
                else -> throw AdbException.ProtocolError("Unexpected sync response: $id")
            }
        } finally {
            stream.write(buildSyncPacket(ID_QUIT, ByteArray(0)))
            stream.close()
            session.removeStream(stream.localId)
        }
    }

    // ---- Internal helpers ----

    /** 累积缓冲区：跨 WRTE 拼接所有 sync 响应数据 */
    private val rxCumulative = java.io.ByteArrayOutputStream()

    private fun buildSyncPacket(id: String, payload: ByteArray): ByteArray {
        val idBytes = id.toByteArray(Charsets.US_ASCII)
        val idInt = ByteBuffer.wrap(idBytes).order(ByteOrder.LITTLE_ENDIAN).getInt()
        val header = ByteBuffer.allocate(8).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            putInt(idInt)
            putInt(payload.size)
        }
        return ByteBuffer.allocate(8 + payload.size).apply {
            put(header.array())
            put(payload)
        }.array()
    }

    /**
     * 从累积缓冲区解析所有**完整**的 sync 包。
     *
     * ⚠ MIUI adbd 的 sync 协议与标准 AOSP 不同：sync 包没有独立的 payload length 字段。
     * 各类包的长度通过类型特定的字段决定：
     *   - DENT: 4B id + 16B header + namelen 字节 name → 由 namelen 决定总长
     *   - DONE: 4B id，无 payload
     *   - DATA: 4B id + 4B LE length + length 字节数据（DATA 有 length 字段）
     *   - OKAY/FAIL: 4B id + NUL 结尾字符串
     *
     * 缓冲区末尾不完整的包会被保留，等待下次追加更多数据后再解析。
     * 返回值中 data 是各类型包的 payload（不含 id）。
     */
    private fun parseSyncPacketsFromBuffer(): List<Pair<String, ByteArray>> {
        val allData = rxCumulative.toByteArray()
        if (allData.size < 4) return emptyList()

        val result = mutableListOf<Pair<String, ByteArray>>()
        var pos = 0

        while (pos + 4 <= allData.size) {
            val id = String(allData.copyOfRange(pos, pos + 4), Charsets.US_ASCII)

            when (id) {
                ID_DENT -> {
                    // DENT: [id:4B][mode:4B LE][size:4B LE][mtime:4B LE][namelen:4B LE][name: namelen B]
                    if (pos + 4 + 16 > allData.size) break
                    val nameLen = ByteBuffer.wrap(allData, pos + 4 + 12, 4)
                        .order(ByteOrder.LITTLE_ENDIAN).getInt()
                    val totalLen = 4 + 16 + nameLen
                    if (pos + totalLen > allData.size) break  // name 不完整
                    val payload = allData.copyOfRange(pos + 4, pos + totalLen)
                    result.add(Pair(ID_DENT, payload))
                    pos += totalLen
                }
                ID_DONE -> {
                    // DONE: [id:4B]，无 payload
                    // MIUI adbd 可能在 DONE 后面填 0，对齐到某边界
                    result.add(Pair(ID_DONE, ByteArray(0)))
                    pos += 4
                    // 跳过尾部填充的 0
                    while (pos + 4 <= allData.size) {
                        val peek = allData.copyOfRange(pos, minOf(pos + 4, allData.size))
                        if (peek.all { it == 0.toByte() }) pos++ else break
                    }
                }
                ID_DATA -> {
                    // DATA: [id:4B][length:4B LE][data: length B]
                    // DATA 包保留 length 字段以保证与标准协议兼容
                    if (pos + 8 > allData.size) break
                    val length = ByteBuffer.wrap(allData, pos + 4, 4)
                        .order(ByteOrder.LITTLE_ENDIAN).getInt()
                    if (pos + 8 + length > allData.size) break
                    val payload = allData.copyOfRange(pos + 8, pos + 8 + length)
                    result.add(Pair(ID_DATA, payload))
                    pos += 8 + length
                }
                ID_OKAY, ID_FAIL -> {
                    // OKAY/FAIL: [id:4B][message: NUL-terminated string]
                    val tail = allData.copyOfRange(pos + 4, allData.size)
                    val nullIdx = tail.indexOf(0.toByte())
                    if (nullIdx < 0) break  // 未找到 NUL
                    val payload = tail.copyOfRange(0, nullIdx)
                    result.add(Pair(id, payload))
                    pos = pos + 4 + nullIdx + 1
                }
                ID_STAT -> {
                    // STAT: [id:4B][mode:4B LE][size:4B LE][mtime:4B LE] — 12 字节
                    if (pos + 16 > allData.size) break
                    val payload = allData.copyOfRange(pos + 4, pos + 16)
                    result.add(Pair(ID_STAT, payload))
                    pos += 16
                }
                else -> {
                    Log.w(TAG, "未知 sync ID '$id' at offset $pos，中止解析（可能是 padding）")
                    break
                }
            }
        }

        // 裁剪已消费的数据
        if (pos > 0) {
            rxCumulative.reset()
            if (pos < allData.size) {
                rxCumulative.write(allData, pos, allData.size - pos)
            }
        }

        return result
    }

    /**
     * 从 stream 读取 sync 包，追加到累积缓冲区后解析。
     *
     * 将所有 WRTE payload 追加到累积缓冲区 [rxCumulative]，
     * 然后调用 [parseSyncPacketsFromBuffer] 解析所有完整包。
     *
     * ⚠ MIUI adbd 的 sync 包无统一 length 字段，各类包格式由类型决定。
     */
    private suspend fun readSyncPackets(stream: AdbStream): List<Pair<String, ByteArray>> {
        val raw = stream.dataFlow.first()
        rxCumulative.write(raw)
        val cumSize = rxCumulative.size()
        Log.d(TAG, "收到 WRTE payload: ${raw.size} bytes, 累积缓冲区: $cumSize bytes")
        // 打印新到数据的头部 32 字节，用于诊断
        Log.d(TAG, "  新数据头部 hex: ${raw.take(32).joinToString(" ") { String.format("%02x", it) }}")
        if (raw.size > 100) {
            Log.d(TAG, "  新数据尾部 hex: ${raw.takeLast(32).joinToString(" ") { String.format("%02x", it) }}")
        }

        val packets = parseSyncPacketsFromBuffer()
        if (packets.isNotEmpty()) {
            Log.d(TAG, "  解析出 ${packets.size} 个包: ${packets.map { it.first }.joinToString(", ")}")
            packets.take(3).forEach { (id, data) ->
                Log.d(TAG, "    [$id] len=${data.size}")
            }
            if (packets.size > 3) Log.d(TAG, "    ... +${packets.size - 3} 个包")
        } else {
            Log.w(TAG, "  ⚠ 未能解析任何完整包，累积缓冲区前32字节: ${rxCumulative.toByteArray().take(32).joinToString(" ") { String.format("%02x", it) }}")
        }
        return packets
    }

    /**
     * 读取单个 sync 包（用于只需要一个响应的场景，如 push、stat）。
     */
    private suspend fun readSingleSyncPacket(stream: AdbStream): Pair<String, ByteArray> {
        while (true) {
            val packets = readSyncPackets(stream)
            if (packets.isNotEmpty()) return packets.first()
        }
    }

    private fun parseDent(data: ByteArray): SyncEntry {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val mode = buffer.getInt()
        val size = buffer.getInt()
        val mtime = buffer.getInt().toLong() and 0xffffffffL
        val nameLen = buffer.getInt()
        val nameBytes = ByteArray(nameLen)
        buffer.get(nameBytes)
        val name = String(nameBytes, Charsets.UTF_8)
        return SyncEntry(mode = mode, size = size, mtime = mtime, name = name)
    }

    private fun parseStat(data: ByteArray): SyncEntry {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val mode = buffer.getInt()
        val size = buffer.getInt()
        val mtime = buffer.getInt().toLong() and 0xffffffffL
        return SyncEntry(mode = mode, size = size, mtime = mtime, name = "")
    }

    private fun intToBytes(value: Int): ByteArray {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    }
}
