package com.young.sample.adbtools.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.young.lib.adb.model.AdbException
import com.young.lib.adb.service.InputService
import com.young.lib.adb.transport.AdbSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 输入模拟 ViewModel。支持点击、滑动、按键、文本输入。
 */
class InputViewModel(
    private val session: AdbSession
) : ViewModel() {

    companion object {
        private const val TAG = "ADB-InputVM"
    }

    private val inputService = InputService(session)

    data class TapForm(
        val x: String = "",
        val y: String = ""
    )

    data class SwipeForm(
        val x1: String = "",
        val y1: String = "",
        val x2: String = "",
        val y2: String = "",
        val durationMs: String = "300"
    )

    data class KeyForm(
        val keyCode: String = "",
        val keyName: String = ""
    )

    data class TextForm(
        val text: String = ""
    )

    data class UiState(
        val tapForm: TapForm = TapForm(),
        val swipeForm: SwipeForm = SwipeForm(),
        val keyForm: KeyForm = KeyForm(),
        val textForm: TextForm = TextForm(),
        val isLoading: Boolean = false,
        val error: String? = null,
        val successMessage: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    // ---- 表单更新 ----

    fun updateTapForm(form: TapForm) {
        _state.update { it.copy(tapForm = form) }
    }

    fun updateSwipeForm(form: SwipeForm) {
        _state.update { it.copy(swipeForm = form) }
    }

    fun updateKeyForm(form: KeyForm) {
        _state.update { it.copy(keyForm = form) }
    }

    fun updateTextForm(form: TextForm) {
        _state.update { it.copy(textForm = form) }
    }

    // ---- 操作 ----

    fun performTap() {
        val form = _state.value.tapForm
        val x = form.x.toIntOrNull() ?: return
        val y = form.y.toIntOrNull() ?: return
        Log.i(TAG, "执行点击: ($x, $y)")
        execute { inputService.tap(x, y) }
    }

    fun performSwipe() {
        val form = _state.value.swipeForm
        val x1 = form.x1.toIntOrNull() ?: return
        val y1 = form.y1.toIntOrNull() ?: return
        val x2 = form.x2.toIntOrNull() ?: return
        val y2 = form.y2.toIntOrNull() ?: return
        val duration = form.durationMs.toIntOrNull() ?: 300
        Log.i(TAG, "执行滑动: ($x1,$y1) → ($x2,$y2), ${duration}ms")
        execute { inputService.swipe(x1, y1, x2, y2, duration) }
    }

    fun performKey(keyCode: Int) {
        Log.i(TAG, "执行按键: keyCode=$keyCode")
        execute { inputService.keyEvent(keyCode) }
    }

    fun performCustomKey() {
        val form = _state.value.keyForm
        val code = form.keyCode.toIntOrNull() ?: return
        Log.i(TAG, "执行自定义按键: $code")
        execute { inputService.keyEvent(code) }
    }

    fun performText() {
        val form = _state.value.textForm
        val text = form.text
        if (text.isBlank()) return
        Log.i(TAG, "执行文本输入: \"$text\"")
        execute { inputService.text(text) }
    }

    fun performLongPress() {
        val form = _state.value.tapForm
        val x = form.x.toIntOrNull() ?: return
        val y = form.y.toIntOrNull() ?: return
        Log.i(TAG, "执行长按: ($x, $y)")
        execute { inputService.longPress(x, y) }
    }

    fun clearMessages() {
        _state.update { it.copy(error = null, successMessage = null) }
    }

    private fun execute(action: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, successMessage = null) }
            try {
                action()
                _state.update { it.copy(isLoading = false, successMessage = "操作完成") }
            } catch (e: AdbException.ServiceError) {
                Log.e(TAG, "输入服务错误: ${e.message}")
                _state.update { it.copy(isLoading = false, error = "服务错误: ${e.message}") }
            } catch (e: Exception) {
                Log.e(TAG, "输入失败: ${e.message}", e)
                _state.update { it.copy(isLoading = false, error = "输入失败: ${e.localizedMessage ?: e.message}") }
            }
        }
    }
}
