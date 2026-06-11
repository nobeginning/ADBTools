package com.young.sample.adbtools.ui.viewmodel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.young.lib.adb.model.AdbException
import com.young.lib.adb.model.FramebufferResult
import com.young.lib.adb.service.FramebufferService
import com.young.lib.adb.transport.AdbSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

/**
 * 截图 ViewModel。
 *
 * 截图策略：
 * 1. 优先使用 exec:screencap -p（返回 PNG，兼容所有设备）
 * 2. 备选 framebuffer: 服务（需要设备支持）
 */
class ScreenshotViewModel(
    private val session: AdbSession
) : ViewModel() {

    private val framebufferService = FramebufferService(session)
    private var autoRefreshJob: Job? = null

    data class UiState(
        val bitmap: Bitmap? = null,
        val width: Int = 0,
        val height: Int = 0,
        val isCapturing: Boolean = false,
        val error: String? = null,
        val autoRefreshIntervalMs: Long = 0L
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun capture() {
        viewModelScope.launch {
            _state.update { it.copy(isCapturing = true, error = null) }
            Log.d(TAG, "开始截图...")
            try {
                // 优先使用 screencap（兼容性最好，返回 PNG）
                captureViaScreencap()
            } catch (e: AdbException.ServiceError) {
                Log.w(TAG, "exec:screencap 失败，尝试 framebuffer: ${e.message}")
                // screencap 不支持，尝试 framebuffer 备选方案
                try {
                    captureViaFramebuffer()
                } catch (e2: Exception) {
                    _state.update {
                        it.copy(
                            error = "截图失败: ${e2.localizedMessage ?: e2.message}",
                            isCapturing = false
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "screencap 失败，尝试 framebuffer: ${e.message}")
                try {
                    captureViaFramebuffer()
                } catch (e2: Exception) {
                    _state.update {
                        it.copy(
                            error = "截图失败: ${e2.localizedMessage ?: e2.message}",
                            isCapturing = false
                        )
                    }
                }
            }
        }
    }

    /**
     * 通过 exec:screencap -p 获取 PNG 截图。
     * 这是推荐方式，兼容所有设备。
     */
    private suspend fun captureViaScreencap() {
        val result = framebufferService.captureViaShell()
        Log.i(TAG, "screencap 成功: ${result.width}x${result.height}, ${result.pixels.size} bytes")

        // result.pixels 是 PNG 字节数据，用 BitmapFactory 解码
        val bitmap = BitmapFactory.decodeByteArray(result.pixels, 0, result.pixels.size)
        if (bitmap != null) {
            _state.update {
                it.copy(
                    bitmap = bitmap,
                    width = bitmap.width,
                    height = bitmap.height,
                    isCapturing = false
                )
            }
        } else {
            _state.update {
                it.copy(error = "无法解码 PNG 截图数据", isCapturing = false)
            }
        }
    }

    /**
     * 通过 framebuffer: 服务获取截图。
     * 备选方案，兼容性取决于设备 adbd 实现。
     */
    private suspend fun captureViaFramebuffer() {
        val result = framebufferService.capture()
        Log.i(TAG, "framebuffer 成功: ${result.width}x${result.height}, ${result.pixels.size} bytes")

        // 尝试多种方式解码
        var bitmap: Bitmap? = null

        // 方式1: 如果是 PNG/JPEG，用 BitmapFactory 解码
        if (result.pixels.size >= 4) {
            bitmap = BitmapFactory.decodeByteArray(result.pixels, 0, result.pixels.size)
        }

        // 方式2: 如果 decodeByteArray 失败，尝试 raw 像素转换
        if (bitmap == null && result.width > 0 && result.height > 0) {
            bitmap = framebufferToBitmap(result)
        }

        if (bitmap != null) {
            _state.update {
                it.copy(
                    bitmap = bitmap,
                    width = bitmap.width,
                    height = bitmap.height,
                    isCapturing = false
                )
            }
        } else {
            _state.update {
                it.copy(error = "无法解码截图数据 (${result.pixels.size} bytes)", isCapturing = false)
            }
        }
    }

    fun startAutoRefresh(intervalMs: Long) {
        if (intervalMs <= 0) return
        stopAutoRefresh()
        _state.update { it.copy(autoRefreshIntervalMs = intervalMs) }
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                capture()
                delay(intervalMs)
            }
        }
    }

    fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
        _state.update { it.copy(autoRefreshIntervalMs = 0L) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        autoRefreshJob?.cancel()
    }

    companion object {
        private const val TAG = "ADB-ScreenshotVM"

        /**
         * 将 FramebufferResult 的 raw 像素数据转换为 Android Bitmap。
         * 假设像素格式为 RGBA 或 RGBX (每像素 4 字节)。
         */
        fun framebufferToBitmap(result: FramebufferResult): Bitmap? {
            return try {
                val bitmap = Bitmap.createBitmap(result.width, result.height, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(result.pixels))
                bitmap
            } catch (e: Exception) {
                null
            }
        }
    }
}
