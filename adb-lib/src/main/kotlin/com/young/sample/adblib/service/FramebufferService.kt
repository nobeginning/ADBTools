package com.young.sample.adblib.service

import android.util.Log
import com.young.sample.adblib.model.AdbException
import com.young.sample.adblib.model.FramebufferResult
import com.young.sample.adblib.transport.AdbSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 截图服务。提供两种方式获取设备屏幕截图：
 *
 * ### 1. screencap（推荐，通过 `exec:` 服务）
 * `exec:screencap -p` 直接返回原始 PNG 数据，无 Shell 协议包装。
 * 兼容所有设备（AOSP / MIUI / 其他厂商），不依赖 framebuffer 协议细节。
 *
 * ### 2. framebuffer（备选）
 * 直接读取 /dev/graphics/fb0。需要设备支持 framebuffer: 服务，
 * 且不同厂商 header 格式可能不同（16B / 56B）。
 */
class FramebufferService(private val session: AdbSession) {

    companion object {
        private const val TAG = "ADB-Framebuffer"
        private const val NEW_HEADER_SIZE = 56
        private const val READ_TIMEOUT_MS = 5000L
    }

    /**
     * 截图（推荐方式）。
     *
     * 使用 `exec:screencap -p` 获取 PNG 格式截图。
     * `exec:` 服务直接返回命令原始输出，不经 Shell V2 协议包装，
     * 因此兼容 MIUI 等非标准 adbd 实现。
     */
    suspend fun captureViaShell(): FramebufferResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "开始截图（exec screencap 方式）...")
        val stream = session.openService("exec:screencap -p")
        try {
            val accumulator = ByteArrayOutputStream()

            // 收集所有 WRTE 数据（PNG 字节流）
            var packetIdx = 1
            while (packetIdx <= 200) {
                val chunk = stream.readChunk(first = packetIdx == 1) ?: break
                accumulator.write(chunk)
                if (packetIdx <= 3) {
                    Log.d(TAG, "WRTE #$packetIdx: ${chunk.size} bytes (累计 ${accumulator.size()})")
                }
                packetIdx++
            }

            val pngData = accumulator.toByteArray()
            Log.i(TAG, "screencap 完成: ${pngData.size} bytes, ${packetIdx - 1} 个包")

            if (pngData.isEmpty()) {
                throw AdbException.ProtocolError("screencap returned empty data")
            }

            // 从 PNG header 解析图像尺寸
            val (width, height) = parsePngDimensions(pngData)

            FramebufferResult(
                version = 2,
                bpp = 32,
                colorSpace = 0,
                width = width,
                height = height,
                pixels = pngData
            )
        } finally {
            stream.close()
            session.removeStream(stream.localId)
        }
    }

    /**
     * 截图（framebuffer 方式，备选）。
     *
     * 直接读取 framebuffer 设备。兼容性取决于设备 adbd 实现：
     * - AOSP: 标准 16B 或 56B header + 原始像素
     * - MIUI: 56B header + 可能压缩的图像数据
     */
    suspend fun capture(): FramebufferResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "开始截图（framebuffer 方式）...")
        val stream = session.openService("framebuffer:")
        try {
            val accumulator = ByteArrayOutputStream()

            // 第一个 WRTE（必定包含 header）
            val first = stream.dataFlow.first()
            accumulator.write(first)
            val totalSize = first.size
            Log.d(TAG, "WRTE #1: ${first.size} bytes")

            // 收集已缓冲的 WRTE 包（MIUI 预推送）
            var packetIdx = 2
            while (packetIdx <= 50) {
                val chunk = stream.readChunk(first = false, shortTimeout = true) ?: break
                accumulator.write(chunk)
                Log.d(TAG, "WRTE #$packetIdx (缓冲): ${chunk.size} bytes (累计 ${accumulator.size()})")
                packetIdx++
            }

            // 发送请求字节（标准 AOSP 需要）
            Log.d(TAG, "发送请求字节...")
            stream.write(byteArrayOf(0))

            // 收集请求后的数据
            while (packetIdx <= 200) {
                val chunk = stream.readChunk(first = false, shortTimeout = false) ?: break
                accumulator.write(chunk)
                Log.d(TAG, "WRTE #$packetIdx (请求后): ${chunk.size} bytes (累计 ${accumulator.size()})")
                packetIdx++
            }

            val rawData = accumulator.toByteArray()
            Log.d(TAG, "全部数据收集完毕: ${rawData.size} bytes, ${packetIdx - 1} 个包")

            val header = parseHeader(rawData)
            val imageData = if (rawData.size > header.headerSize) {
                rawData.copyOfRange(header.headerSize, rawData.size)
            } else {
                Log.w(TAG, "无图像数据: rawData=${rawData.size}, header=${header.headerSize}")
                ByteArray(0)
            }

            val format = detectImageFormat(imageData)
            Log.i(TAG, "framebuffer 截图完成: ${header.width}x${header.height}, " +
                "bpp=${header.bpp}, image=${imageData.size}B, format=$format")

            FramebufferResult(
                version = header.version,
                bpp = header.bpp,
                colorSpace = header.colorSpace,
                width = header.width,
                height = header.height,
                pixels = imageData
            )
        } finally {
            stream.close()
            session.removeStream(stream.localId)
        }
    }

    // ---- 安全读取辅助方法 ----

    /**
     * 从 stream 安全读取下一个 WRTE 数据块。
     *
     * 处理两种流结束情况：
     * - Channel 关闭（adbd 发送 CLSE 正常关闭流）
     * - 超时（无新数据到达）
     *
     * @param first 是否第一个块（第一个块必定存在，不设超时）
     * @param shortTimeout 是否使用短超时（用于缓冲数据收集阶段）
     * @return 数据块，null 表示流结束或超时
     */
    private suspend fun com.young.sample.adblib.transport.AdbStream.readChunk(
        first: Boolean,
        shortTimeout: Boolean = false
    ): ByteArray? {
        return try {
            if (first) {
                dataFlow.first()
            } else {
                val timeout = if (shortTimeout) 300L else READ_TIMEOUT_MS
                withTimeoutOrNull(timeout) { dataFlow.first() }
            }
        } catch (e: NoSuchElementException) {
            // Channel/Flow 已关闭，无更多数据（流正常结束）
            Log.d(TAG, "流已关闭（adbd 发送了 CLSE），数据读取完成")
            null
        }
    }

    // ---- PNG 尺寸解析 ----

    /**
     * 从 PNG 字节流解析图像宽度和高度。
     * PNG IHDR chunk 位于文件头 16 字节偏移处：
     *   [8B signature][4B length][4B "IHDR"][4B width][4B height]...
     */
    private fun parsePngDimensions(data: ByteArray): Pair<Int, Int> {
        // PNG signature: 89 50 4E 47 0D 0A 1A 0A (8 bytes)
        // IHDR length: 00 00 00 0D (4 bytes, always 13)
        // IHDR type: 49 48 44 52 (4 bytes, "IHDR")
        // width: 4 bytes (big-endian)
        // height: 4 bytes (big-endian)
        if (data.size < 24) return Pair(0, 0)
        return try {
            val buf = ByteBuffer.wrap(data, 16, 8).order(ByteOrder.BIG_ENDIAN)
            val width = buf.int
            val height = buf.int
            Pair(width, height)
        } catch (e: Exception) {
            Log.w(TAG, "无法解析 PNG 尺寸: ${e.message}")
            Pair(0, 0)
        }
    }

    // ---- Framebuffer header 解析 ----

    private data class HeaderInfo(
        val version: Int,
        val bpp: Int,
        val colorSpace: Int,
        val width: Int,
        val height: Int,
        val pixelSize: Int,
        val headerSize: Int
    )

    private fun parseHeader(data: ByteArray): HeaderInfo {
        Log.d(TAG, "Header hex (前56B): ${data.take(56).joinToString(" ") { String.format("%02x", it) }}")

        if (data.size < 16) {
            throw AdbException.ProtocolError(
                "Framebuffer header too short: ${data.size} bytes (expected >= 16)"
            )
        }

        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val field1 = buf.getInt(0)
        val field2 = buf.getInt(4)

        return if (field2 in listOf(16, 24, 32, 64)) {
            val version = field1
            val bpp = field2
            val colorSpace = buf.getInt(8)
            val pixelSize = buf.getInt(12)
            val width = buf.getInt(16)
            val height = buf.getInt(20)
            Log.d(TAG, "新版 header (56B): version=$version, bpp=$bpp, " +
                "colorSpace=$colorSpace, size=$pixelSize, ${width}x${height}")
            HeaderInfo(version, bpp, colorSpace, width, height, pixelSize, NEW_HEADER_SIZE)
        } else {
            val depth = field1
            val pixelSize = field2
            val width = buf.getInt(8)
            val height = buf.getInt(12)
            Log.d(TAG, "旧版 header (16B): depth=$depth, size=$pixelSize, ${width}x${height}")
            HeaderInfo(1, depth, 0, width, height, pixelSize, 16)
        }
    }

    private fun detectImageFormat(data: ByteArray): String {
        if (data.size < 4) return "raw(empty)"
        return when {
            data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte() -> "JPEG"
            data[0] == 0x89.toByte() && data[1] == 0x50.toByte() &&
                data[2] == 0x4E.toByte() && data[3] == 0x47.toByte() -> "PNG"
            else -> "raw(${data.size}B)"
        }
    }
}
