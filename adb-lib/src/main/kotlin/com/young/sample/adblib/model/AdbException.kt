package com.young.sample.adblib.model

/**
 * ADB 协议相关异常
 */
sealed class AdbException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    /** TCP 连接失败 */
    class ConnectionFailed(host: String, port: Int, cause: Throwable) :
        AdbException("Cannot connect to ADB server at $host:$port", cause)

    /** 认证失败 */
    class AuthenticationFailed(reason: String) :
        AdbException("ADB authentication failed: $reason")

    /** 未找到目标设备 */
    class DeviceNotFound(serial: String) :
        AdbException("Device not found: $serial")

    /** 服务打开失败 */
    class ServiceError(service: String, reason: String) :
        AdbException("Service '$service' error: $reason")

    /** 协议错误 */
    class ProtocolError(message: String) :
        AdbException("Protocol error: $message")

    /** 流已关闭 */
    class StreamClosed(localId: Int) :
        AdbException("Stream $localId closed")

    /** 命令执行失败 */
    class CommandFailed(command: String, exitCode: Int, stderr: String) :
        AdbException("Command '$command' failed ($exitCode): $stderr")

    /** 超时 */
    class Timeout(message: String) :
        AdbException(message)
}
