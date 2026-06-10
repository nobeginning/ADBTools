package com.young.sample.adblib.service

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
        val stream = session.openService("sync:")
        try {
            val sendPath = "$remotePath,$mode"
            stream.write(buildSyncPacket(ID_SEND, sendPath.toByteArray(Charsets.UTF_8)))

            val fileData = withContext(Dispatchers.IO) {
                java.nio.file.Files.readAllBytes(localPath)
            }
            var offset = 0
            while (offset < fileData.size) {
                val chunkSize = minOf(MAX_CHUNK_SIZE, fileData.size - offset)
                val chunk = fileData.copyOfRange(offset, offset + chunkSize)
                stream.write(buildSyncPacket(ID_DATA, chunk))
                offset += chunkSize
            }

            val timestamp = System.currentTimeMillis() / 1000
            stream.write(buildSyncPacket(ID_DONE, intToBytes(timestamp.toInt())))

            // OKAY/FAIL 通过 readSyncPacket 读取
            val response = readSyncPacket(stream)
            when (response.first) {
                ID_OKAY -> Unit
                ID_FAIL -> {
                    val errorMsg = String(response.second, Charsets.UTF_8)
                    throw AdbException.ServiceError("sync:push", errorMsg)
                }
                else -> throw AdbException.ProtocolError("Unexpected sync response: ${response.first}")
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
        val stream = session.openService("sync:")
        try {
            stream.write(buildSyncPacket(ID_RECV, remotePath.toByteArray(Charsets.UTF_8)))

            val output = ByteArrayOutputStream()
            while (true) {
                val (id, data) = readSyncPacket(stream)
                when (id) {
                    ID_DATA -> output.write(data)
                    ID_DONE -> break
                    ID_FAIL -> {
                        val errorMsg = String(data, Charsets.UTF_8)
                        throw AdbException.ServiceError("sync:pull", errorMsg)
                    }
                    else -> throw AdbException.ProtocolError("Unexpected sync response: $id")
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
        val stream = session.openService("sync:")
        try {
            stream.write(buildSyncPacket(ID_LIST, remotePath.toByteArray(Charsets.UTF_8)))

            val entries = mutableListOf<SyncEntry>()
            while (true) {
                val (id, data) = readSyncPacket(stream)
                when (id) {
                    ID_DENT -> {
                        entries.add(parseDent(data))
                    }
                    ID_DONE -> break
                    else -> throw AdbException.ProtocolError("Unexpected sync response: $id")
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
     * 获取文件信息。
     */
    suspend fun stat(remotePath: String): SyncEntry {
        val stream = session.openService("sync:")
        try {
            stream.write(buildSyncPacket(ID_STAT, remotePath.toByteArray(Charsets.UTF_8)))

            val (id, data) = readSyncPacket(stream)
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
     * 从 stream 读取 sync packet。
     *
     * sync 协议中，每个 packet 是 8 字节 header + payload。
     * 在 ADB wire protocol 中，sync 的 data 通过 WRTE 包的 payload 传输。
     * 每个 WRTE payload 就是完整的 sync packet。
     */
    private suspend fun readSyncPacket(stream: AdbStream): Pair<String, ByteArray> {
        val payload = stream.dataFlow.first()
        if (payload.size < 8) throw AdbException.ProtocolError("Sync packet too short: ${payload.size} bytes")
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val idInt = buffer.getInt()
        val length = buffer.getInt()
        val id = String(byteArrayOf(
            (idInt and 0xFF).toByte(),
            ((idInt shr 8) and 0xFF).toByte(),
            ((idInt shr 16) and 0xFF).toByte(),
            ((idInt shr 24) and 0xFF).toByte()
        ), Charsets.US_ASCII)
        val data = ByteArray(length)
        if (length > 0) {
            buffer.get(data)
        }
        return Pair(id, data)
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
