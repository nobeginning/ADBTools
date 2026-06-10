package com.young.sample.adblib

/**
 * ADB 协议命令常量
 *
 * 每个常量为 4 个 ASCII 字符打包为 little-endian Int32
 */
object AdbCommand {
    /** A_SYNC — 内部桥接，不通过线路发送 */
    const val SYNC = 0x434e5953

    /** A_CNXN — 连接请求/版本协商 */
    const val CNXN = 0x4e584e43

    /** A_AUTH — 认证握手 */
    const val AUTH = 0x48545541

    /** A_OPEN — 打开流到服务 */
    const val OPEN = 0x4e45504f

    /** A_OKAY — 流就绪，允许写入 */
    const val OKAY = 0x59414b4f

    /** A_CLSE — 关闭流 */
    const val CLSE = 0x45534c43

    /** A_WRTE — 写数据 */
    const val WRTE = 0x45545257

    /** 认证类型 — TOKEN */
    const val AUTH_TOKEN = 1

    /** 认证类型 — SIGNATURE */
    const val AUTH_SIGNATURE = 2

    /** 认证类型 — RSAPUBLICKEY */
    const val AUTH_RSA_PUBLIC = 3

    /** 当前协议版本 */
    const val VERSION = 0x01000001

    /** 最大 payload 大小 (1MB) */
    const val MAX_PAYLOAD = 1024 * 1024
}
