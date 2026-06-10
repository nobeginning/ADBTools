package com.young.sample.adblib

/**
 * ADB Library 连接配置
 */
data class AdbConfig(
    val host: String = "127.0.0.1",
    val port: Int = 5037,
    val connectTimeoutMs: Long = 5000,
    val readTimeoutMs: Long = 30000,
    val maxRetries: Int = 5
) {
    companion object {
        val DEFAULT = AdbConfig()
    }
}
