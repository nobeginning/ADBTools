package com.young.lib.adb.service

import com.young.lib.adb.model.AdbException
import com.young.lib.adb.model.ShellResult
import com.young.lib.adb.protocol.ShellData
import com.young.lib.adb.protocol.ShellProtocol
import com.young.lib.adb.transport.AdbSession
import com.young.lib.adb.transport.AdbStream
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * Shell 服务。在设备上执行 shell 命令。
 *
 * 支持 Shell V2 协议（如果设备支持），自动处理 stdout/stderr/exit code 多路复用。
 * 后备使用 Shell V1（所有输出合并到 stdout）。
 */
class ShellService(private val session: AdbSession) {

    companion object {
        private const val TAG = "ADB-Shell"
    }

    /**
     * 执行单条命令，等待返回完整结果。
     * 自动尝试 Shell V2，在设备支持时获得 stderr 分离和 exit code。
     */
    suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "执行命令: $command")
        try {
            Log.d(TAG, "尝试 Shell V2 协议...")
            execV2(command).also {
                Log.d(TAG, "Shell V2 完成: exit=${it.exitCode}, stdout=${it.stdout.length}B, stderr=${it.stderr.length}B")
            }
        } catch (e: AdbException.ServiceError) {
            Log.w(TAG, "Shell V2 不支持，回退到 V1: ${e.message}")
            execV1(command).also {
                Log.d(TAG, "Shell V1 完成: stdout=${it.stdout.length}B")
            }
        }
    }

    /**
     * 执行命令，流式返回输出。
     */
    fun execStream(command: String): Flow<ShellData> = flow {
        Log.d(TAG, "流式执行: $command")
        val stream = try {
            session.openService("shell,raw:$command")
        } catch (e: AdbException.ServiceError) {
            Log.d(TAG, "raw 模式失败，尝试普通模式...")
            session.openService("shell:${command.replace(" ", "%s")}")
        }

        try {
            var v2Parsed = false
            stream.dataFlow.collect { data ->
                Log.d(TAG, "收到数据块: ${data.size}B, 前32字节: ${data.take(32).joinToString(" ") { String.format("%02x", it) }}")
                val packets = ShellProtocol.parsePackets(data)
                if (packets.isNotEmpty()) {
                    Log.d(TAG, "  Shell V2 解析出 ${packets.size} 个包: ${packets.map { it::class.simpleName }}")
                    v2Parsed = true
                    for (packet in packets) {
                        emit(packet)
                        if (packet is ShellData.Exit) break
                    }
                } else if (!v2Parsed) {
                    // MIUI adbd 不支持 Shell V2，原始数据当作 stdout
                    val text = String(data, Charsets.UTF_8)
                    Log.d(TAG, "  Shell V2 解析为空，当作 V1 stdout: ${text.take(100)}")
                    emit(ShellData.Stdout(data))
                }
            }
        } finally {
            stream.close()
            session.removeStream(stream.localId)
        }
    }

    /**
     * 打开交互式 Shell。
     */
    suspend fun interactive(): InteractiveShell {
        val stream = session.openService("shell:")
        return InteractiveShell(stream)
    }

    // ---- Internal ----

    private suspend fun execV2(command: String): ShellResult {
        val stream = session.openService("shell,v2,raw:$command")
        val stdout = StringBuilder()
        var exitCode = -1
        var v2Parsed = false

        try {
            stream.dataFlow.collect { data ->
                Log.d(TAG, "收到 shell 数据: ${data.size}B, 前16字节 hex: ${data.take(16).joinToString(" ") { String.format("%02x", it) }}")
                val packets = ShellProtocol.parsePackets(data)
                if (packets.isNotEmpty()) {
                    Log.d(TAG, "  Shell V2 解析到 ${packets.size} 个包: ${packets.map { it::class.simpleName }}")
                    v2Parsed = true
                    for (packet in packets) {
                        when (packet) {
                            is ShellData.Stdout -> {
                                Log.d(TAG, "    stdout: ${packet.data.size}B")
                                stdout.append(packet.text)
                            }
                            is ShellData.Stderr -> {
                                Log.d(TAG, "    stderr: ${packet.data.size}B")
                            }
                            is ShellData.Exit -> {
                                Log.d(TAG, "    exit: ${packet.code}")
                                exitCode = packet.code
                                return@collect
                            }
                        }
                    }
                } else if (!v2Parsed) {
                    // Shell V2 解析失败 → 设备不支持 Shell V2 格式（如 MIUI），
                    // 将原始数据当作 V1 stdout 处理
                    Log.d(TAG, "  Shell V2 解析为空，fallback V1 原始数据")
                    stdout.append(String(data, Charsets.UTF_8))
                }
            }
        } finally {
            stream.close()
            session.removeStream(stream.localId)
        }

        return ShellResult(stdout.toString(), "", exitCode)
    }

    private suspend fun execV1(command: String): ShellResult {
        val stream = session.openService("shell:${command.replace(" ", "%s")}")
        val stdout = StringBuilder()

        try {
            stream.dataFlow.collect { data ->
                stdout.append(String(data, Charsets.UTF_8))
            }
        } finally {
            stream.close()
            session.removeStream(stream.localId)
        }

        return ShellResult(stdout.toString(), "", -1)
    }
}

/**
 * 交互式 Shell 会话。
 */
class InteractiveShell(private val stream: AdbStream) : AutoCloseable {

    val output: Flow<ByteArray> = stream.dataFlow

    suspend fun writeStdin(input: String) {
        val stdinData = ShellProtocol.buildStdin(input.toByteArray(Charsets.UTF_8))
        stream.write(stdinData)
    }

    suspend fun writeStdin(data: ByteArray) {
        stream.write(data)
    }

    suspend fun resize(rows: Int, cols: Int) {
        // PTY resize 转义序列
        val escape = "[8;${rows};${cols}t".toByteArray()
        stream.write(escape)
    }

    override fun close() {
        stream.close()
    }
}
