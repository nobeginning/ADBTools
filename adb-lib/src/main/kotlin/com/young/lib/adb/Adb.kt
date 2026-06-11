package com.young.lib.adb

import android.util.Log
import com.young.lib.adb.model.AdbException
import com.young.lib.adb.protocol.AdbPacket
import com.young.lib.adb.transport.AdbSession
import com.young.lib.adb.transport.AuthHandler
import com.young.lib.adb.transport.TcpTransport
import java.io.File

/**
 * ADB 库唯一公开入口。
 *
 * 提供直连 adbd 的能力，绕过 ADB Server。
 * 适用于手机 App 连接本机 adbd（127.0.0.1:5555）场景。
 *
 * 使用示例:
 * ```kotlin
 * val session = Adb.connectDirect(
 *     keyStoreDir = File(context.filesDir, "adb_keys")
 * )
 * val result = ShellService(session).exec("ls -la")
 * println(result.stdout)
 * session.close()
 * ```
 */
class Adb private constructor() {

    companion object {
        private const val TAG = "ADB"

        /**
         * 直连 adbd（绕过 ADB Server）。
         *
         * 连接流程: TCP connect → CNXN → AUTH（如需要）→ 返回 AdbSession
         *
         * 适用场景：手机上运行 app，通过 `adb tcpip 5555` 启用 adbd TCP 监听后，
         * 直接连接 127.0.0.1:5555 与手机自身的 adbd 通信。
         *
         * @param host adbd 主机地址（通常 127.0.0.1）
         * @param port adbd TCP 端口（通常 5555）
         * @param keyStoreDir RSA 密钥对存储目录
         * @param connectTimeoutMs TCP 连接超时毫秒
         * @param readTimeoutMs Socket 读取超时毫秒
         * @return 已认证的 AdbSession，可用于 shell/sync/framebuffer 等操作
         * @throws AdbException.ConnectionFailed 如果 TCP 连接失败
         * @throws AdbException.AuthenticationFailed 如果认证失败
         */
        suspend fun connectDirect(
            host: String = "127.0.0.1",
            port: Int = 5555,
            keyStoreDir: File,
            connectTimeoutMs: Long = 5000,
            readTimeoutMs: Long = 30000
        ): AdbSession {
            Log.i(TAG, "直连 adbd: $host:$port  (密钥目录: ${keyStoreDir.absolutePath})")

            val transport = TcpTransport(
                host = host,
                port = port,
                connectTimeoutMs = connectTimeoutMs,
                readTimeoutMs = readTimeoutMs
            )

            Log.d(TAG, "正在建立 TCP 连接...")
            transport.connect()
            Log.i(TAG, "✅ TCP 连接已建立: $host:$port")

            try {
                // 发送 CNXN 包（host 端 banner）
                Log.d(TAG, "发送 CNXN 握手包 (banner=host::, version=0x${AdbCommand.VERSION.toUInt().toString(16)})")
                transport.write(AdbPacket.hostConnect())

                // 认证握手：读取对端响应，走 CNXN 或 AUTH 流程
                Log.d(TAG, "开始认证握手...")
                val authHandler = AuthHandler(keyStoreDir)
                val success = authHandler.authenticate(transport)
                if (!success) {
                    Log.e(TAG, "❌ 认证被拒")
                    transport.close()
                    throw AdbException.AuthenticationFailed("Direct adbd authentication rejected")
                }

                // 创建 Session 并启动 read loop
                Log.d(TAG, "创建 AdbSession 并启动 read loop...")
                val session = AdbSession(transport)
                session.startReadLoop()
                Log.i(TAG, "✅ 直连 adbd 成功，session 已就绪")
                return session
            } catch (e: AdbException) {
                Log.e(TAG, "❌ 连接失败: ${e.message}")
                transport.close()
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "❌ 连接异常: ${e.message}", e)
                transport.close()
                throw AdbException.ConnectionFailed(host, port, e)
            }
        }
    }
}
