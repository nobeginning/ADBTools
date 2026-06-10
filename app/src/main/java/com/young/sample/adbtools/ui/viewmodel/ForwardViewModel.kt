package com.young.sample.adbtools.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.young.sample.adblib.AdbClient
import com.young.sample.adblib.model.AdbException
import com.young.sample.adblib.model.ForwardEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 端口转发管理 ViewModel。
 */
class ForwardViewModel(
    private val adbClient: AdbClient
) : ViewModel() {

    companion object {
        private const val TAG = "ADB-ForwardVM"
    }

    data class UiState(
        val forwards: List<ForwardEntry> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
        val successMessage: String? = null
    )

    data class ForwardForm(
        val local: String = "",
        val remote: String = ""
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val forwards = adbClient.listForwards()
                _state.update { it.copy(forwards = forwards, isLoading = false) }
            } catch (e: AdbException.ConnectionFailed) {
                _state.update {
                    it.copy(error = "连接失败: ${e.message}", isLoading = false)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        error = "获取转发列表失败: ${e.localizedMessage ?: e.message}",
                        isLoading = false
                    )
                }
            }
        }
    }

    fun addForward(form: ForwardForm) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                adbClient.forward(form.local, form.remote)
                _state.update {
                    it.copy(isLoading = false, successMessage = "转发 ${form.local} → ${form.remote} 已添加")
                }
                refresh()
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "添加转发失败: ${e.localizedMessage ?: e.message}"
                    )
                }
            }
        }
    }

    fun addReverse(form: ForwardForm) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                adbClient.reverse(form.remote, form.local)
                _state.update {
                    it.copy(isLoading = false, successMessage = "反向转发 ${form.remote} → ${form.local} 已添加")
                }
                refresh()
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "添加反向转发失败: ${e.localizedMessage ?: e.message}"
                    )
                }
            }
        }
    }

    fun removeForward(entry: ForwardEntry) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                // 通过 killforward-all + 重新添加其他规则模拟删除单个
                val others = _state.value.forwards.filter { it != entry }
                adbClient.killForwardAll()
                others.forEach { f ->
                    adbClient.forward(f.local, f.remote)
                }
                _state.update {
                    it.copy(isLoading = false, successMessage = "${entry.local} → ${entry.remote} 已删除")
                }
                refresh()
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "删除失败: ${e.localizedMessage ?: e.message}"
                    )
                }
            }
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                adbClient.killForwardAll()
                _state.update {
                    it.copy(
                        forwards = emptyList(),
                        isLoading = false,
                        successMessage = "所有转发规则已清除"
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "清除失败: ${e.localizedMessage ?: e.message}"
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(error = null, successMessage = null) }
    }
}
