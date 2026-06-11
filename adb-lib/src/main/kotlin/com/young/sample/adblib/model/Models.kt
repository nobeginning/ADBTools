package com.young.sample.adblib.model

/**
 * Shell 命令执行结果
 */
data class ShellResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int
)

/**
 * FrameBuffer 截图结果
 */
data class FramebufferResult(
    val version: Int,
    val bpp: Int,
    val colorSpace: Int,
    val width: Int,
    val height: Int,
    val pixels: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FramebufferResult
        return version == other.version &&
                bpp == other.bpp &&
                colorSpace == other.colorSpace &&
                width == other.width &&
                height == other.height &&
                pixels.contentEquals(other.pixels)
    }

    override fun hashCode(): Int {
        var result = version
        result = 31 * result + bpp
        result = 31 * result + colorSpace
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + pixels.contentHashCode()
        return result
    }
}

/**
 * 文件同步条目
 */
data class SyncEntry(
    val mode: Int,
    val size: Int,
    val mtime: Long,
    val name: String
)

