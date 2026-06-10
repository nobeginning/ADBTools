package com.young.sample.adblib.service

import com.young.sample.adblib.model.AdbException
import com.young.sample.adblib.model.ShellResult
import com.young.sample.adblib.protocol.ShellData
import com.young.sample.adblib.protocol.ShellProtocol
import com.young.sample.adblib.transport.AdbSession
import com.young.sample.adblib.transport.AdbStream
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

    /**
     * 执行单条命令，等待返回完整结果。
     * 自动尝试 Shell V2，在设备支持时获得 stderr 分离和 exit code。
     */
    suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        try {
            // 尝试 Shell V2
            execV2(command)
        } catch (e: AdbException.ServiceError) {
            // 设备不支持 V2，回退到 V1
            execV1(command)
        }
    }

    /**
     * 执行命令，流式返回输出。
     */
    fun execStream(command: String): Flow<ShellData> = flow {
        val stream = try {
            session.openService("shell,raw:$command")
        } catch (e: AdbException.ServiceError) {
            // 尝试不带 raw 的方式
            session.openService("shell:${command.replace(" ", "%s")}")
        }

        try {
            // 读取输出
            stream.dataFlow.collect { data ->
                val packets = ShellProtocol.parsePackets(data)
                for (packet in packets) {
                    emit(packet)
                    if (packet is ShellData.Exit) break
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
        val stderr = StringBuilder()
        var exitCode = -1

        try {
            stream.dataFlow.collect { data ->
                val packets = ShellProtocol.parsePackets(data)
                for (packet in packets) {
                    when (packet) {
                        is ShellData.Stdout -> stdout.append(packet.text)
                        is ShellData.Stderr -> stderr.append(packet.text)
                        is ShellData.Exit -> {
                            exitCode = packet.code
                            return@collect
                        }
                    }
                }
            }
        } finally {
            stream.close()
            session.removeStream(stream.localId)
        }

        return ShellResult(stdout.toString(), stderr.toString(), exitCode)
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
