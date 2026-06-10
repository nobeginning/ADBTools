package com.young.sample.adblib.service

import com.young.sample.adblib.model.AdbException
import com.young.sample.adblib.model.FramebufferResult
import com.young.sample.adblib.transport.AdbSession
import com.young.sample.adblib.transport.AdbStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Framebuffer 服务。获取设备屏幕截图。
 *
 * 打开 framebuffer: 服务后，先收到 16 字节 header 描述帧缓冲区属性，
 * 然后每次发送 1 字节请求，服务端返回一帧数据。
 */
class FramebufferService(private val session: AdbSession) {

    /**
     * 截取设备屏幕。
     */
    suspend fun capture(): FramebufferResult = withContext(Dispatchers.IO) {
        val stream = session.openService("framebuffer:")
        try {
            // 读取 16 字节 header
            // 在 ADB wire protocol 中，数据通过 WRTE 包传输
            // 第一个 WRTE 的 payload 包含 header
            // 注意：需要结合 stream 读取实现来适配实际数据传输
            val headerData = ByteArray(16)
            // TODO: 从 stream 读取 headerData

            val buffer = ByteBuffer.wrap(headerData).order(ByteOrder.LITTLE_ENDIAN)
            val depth = buffer.getInt()
            val size = buffer.getInt()
            val width = buffer.getInt()
            val height = buffer.getInt()

            // 请求帧数据
            val requestByte = byteArrayOf(0)
            stream.write(requestByte)

            // 读取像素数据
            val pixelData = ByteArray(size)
            // TODO: 从 stream 读取 pixelData

            FramebufferResult(
                version = 2,
                bpp = depth,
                colorSpace = 0,
                width = width,
                height = height,
                pixels = pixelData
            )
        } finally {
            stream.close()
            session.removeStream(stream.localId)
        }
    }

    /**
     * 截取设备屏幕（通过 shell screencap 命令，更通用）。
     */
    suspend fun captureViaShell(): FramebufferResult? {
        // 备选方案：通过 shell 命令 screencap 获取截图
        // adb exec-out screencap 返回 PNG 数据
        val shellService = ShellService(session)
        val result = shellService.exec("screencap -p")
        // screencap 输出需要通过特殊方式读取（二进制）
        return null
    }
}
